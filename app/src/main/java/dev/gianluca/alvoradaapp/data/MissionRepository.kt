package dev.gianluca.alvoradaapp.data

import android.util.Log
import dev.gianluca.alvoradaapp.alarm.AlarmScheduler
import dev.gianluca.alvoradaapp.core.DayMask
import dev.gianluca.alvoradaapp.evidence.EvidenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Uma missão junto do seu estado num dia concreto. */
data class MissionRun(
    val mission: MissionEntity,
    val instance: MissionInstanceEntity,
    val photoCount: Int = 0,
) {
    val requiresPhoto: Boolean get() = mission.requiresEvidence
    val isOverdue: Boolean
        get() = instance.evidenceDeadline?.let { it < System.currentTimeMillis() } == true
}

/**
 * Domínio das missões: o que fazer, se já foi feito e o que acontece quando não foi.
 *
 * A regra central desta fase mora aqui — a **janela de evidência**. Ao dispensar o
 * despertador, cada missão que exige foto ganha um prazo. Se a foto não chegar, o
 * app volta a despertar, e repete até um teto por missão. Isso transforma "desliguei
 * o alarme" em algo bem diferente de "cumpri o que ia fazer".
 */
class MissionRepository(
    private val db: AlvoradaDatabase,
    private val scheduler: AlarmScheduler,
    private val evidenceStore: EvidenceStore,
    private val points: PointsRepository,
) {

    // ------------------------------------------------------------------ cadastro

    fun observeMissions(alarmId: Long): Flow<List<MissionEntity>> =
        db.missionDao().observeByAlarm(alarmId)

    /**
     * A invariante do schema é aplicada na gravação, não só na UI: uma missão nunca
     * pode existir num dia em que o despertador dela não toca, ou ela ficaria
     * eternamente pendente sem nada capaz de cumpri-la.
     */
    suspend fun saveMission(mission: MissionEntity): Long {
        val alarm = db.alarmDao().getById(mission.alarmId)
            ?: error("Missão órfã: alarme ${mission.alarmId} não existe")
        if (alarm.isOneShot) {
            error("Despertador de uma vez só não sustenta missões: ${alarm.id}")
        }
        val allowed = alarm.missionDayMask()
        val safe = mission.copy(
            // Seguir o dono é uma cópia, não uma interseção: a missão também ganha os
            // dias que o despertador passou a ter.
            daysMask = if (mission.followsAlarmDays) allowed
            else DayMask.intersect(mission.daysMask, allowed),
        )
        return db.missionDao().upsert(safe).let { if (safe.id == 0L) it else safe.id }
    }

    suspend fun deleteMission(mission: MissionEntity) = db.missionDao().delete(mission)

    // ------------------------------------------------------------------ o dia

    /**
     * Cria as instâncias do dia no momento em que o despertador é dispensado.
     *
     * Materializar aqui — e não à meia-noite — significa que uma missão editada de
     * manhã já vale para o disparo daquele dia, e que dias em que o alarme não tocou
     * não geram pendência nenhuma.
     *
     * [completedNow] são as missões que já foram marcadas na própria tela do alarme.
     */
    suspend fun materializeOnDismiss(
        occurrenceId: Long,
        alarmId: Long,
        completedNow: Set<Long>,
        now: Long = System.currentTimeMillis(),
    ): List<MissionInstanceEntity> {
        if (db.missionInstanceDao().countForOccurrence(occurrenceId) > 0) {
            Log.i(TAG, "Disparo $occurrenceId já materializado — ignorando")
            return db.missionInstanceDao().getByOccurrence(occurrenceId)
        }

        val today = LocalDate.now()
        val dayBit = DayMask.bitOf(today.dayOfWeek)
        val missions = db.missionDao().getByAlarmForDayBit(alarmId, dayBit)
        if (missions.isEmpty()) return emptyList()

        val instances = missions.map { mission ->
            val done = mission.id in completedNow
            MissionInstanceEntity(
                missionId = mission.id,
                occurrenceId = occurrenceId,
                date = today.toString(),
                status = when {
                    done -> MissionStatus.COMPLETED
                    mission.requiresEvidence -> MissionStatus.AWAITING_EVIDENCE
                    // Sem foto exigida não há o que cobrar: fica em aberto para ser
                    // marcada depois, sem despertar ninguém.
                    else -> MissionStatus.PENDING
                },
                evidenceDeadline = if (!done && mission.requiresEvidence) {
                    now + mission.evidenceWindowMinutes * 60_000L
                } else null,
                completedAt = if (done) now else null,
                xpAwarded = if (done) mission.xpValue else 0,
                coinAwarded = if (done) mission.coinValue else 0,
            )
        }

        val ids = db.missionInstanceDao().insertAll(instances)
        val saved = instances.zip(ids) { instance, id -> instance.copy(id = id) }

        // As marcadas na própria tela do alarme já valem ponto cheio — foram feitas
        // no horário, que é o comportamento que o app quer reforçar.
        saved.filter { it.status == MissionStatus.COMPLETED }.forEach { instance ->
            points.awardMission(
                instanceId = instance.id,
                xp = instance.xpAwarded,
                coins = instance.coinAwarded,
                late = false,
            )
        }

        refreshChase(occurrenceId, alarmId)
        return saved
    }

    // ------------------------------------------------------------------ cobrança

    /**
     * Reagenda (ou cancela) a cobrança de um disparo com base no que ainda falta.
     *
     * Chamado depois de materializar e depois de cada evidência enviada: assim que a
     * última foto chega, o alarme de cobrança some — nada pior do que ser cobrado por
     * algo que já foi feito.
     */
    suspend fun refreshChase(occurrenceId: Long, alarmId: Long) {
        val pending = db.missionInstanceDao().getAwaitingEvidence(occurrenceId)
        val next = pending.mapNotNull { it.evidenceDeadline }.minOrNull()
        if (next == null) {
            scheduler.cancelChase(alarmId, occurrenceId)
            Log.i(TAG, "Sem pendências no disparo $occurrenceId — cobrança cancelada")
        } else {
            scheduler.scheduleChase(alarmId, occurrenceId, next)
        }
    }

    /**
     * Decide o que a cobrança deve fazer ao disparar.
     *
     * Missões cujo teto de cobranças estourou viram FAILED e param de incomodar —
     * insistir para sempre transformaria o app numa fonte de ansiedade, que é
     * exatamente o oposto do objetivo.
     */
    suspend fun onChaseFired(
        occurrenceId: Long,
        alarmId: Long,
        now: Long = System.currentTimeMillis(),
    ): List<MissionRun> {
        val instances = db.missionInstanceDao().getAwaitingEvidence(occurrenceId)
        val missions = db.missionDao().getByIds(instances.map { it.missionId }).associateBy { it.id }

        val stillDue = mutableListOf<MissionRun>()
        var nextChaseAt: Long? = null

        for (instance in instances) {
            val mission = missions[instance.missionId] ?: continue
            val deadline = instance.evidenceDeadline ?: continue

            if (deadline > now) {
                // Ainda dentro da janela: não é a vez desta missão.
                nextChaseAt = minOfNullable(nextChaseAt, deadline)
                continue
            }

            if (instance.chaseCount >= mission.maxChases) {
                db.missionInstanceDao().update(instance.copy(status = MissionStatus.FAILED))
                Log.i(TAG, "Missão ${mission.title} falhou após ${instance.chaseCount} cobranças")
                continue
            }

            val bumped = instance.copy(chaseCount = instance.chaseCount + 1)
            db.missionInstanceDao().update(bumped)
            stillDue += MissionRun(mission, bumped)
            nextChaseAt = minOfNullable(nextChaseAt, now + mission.chaseIntervalMinutes * 60_000L)
        }

        if (nextChaseAt != null) scheduler.scheduleChase(alarmId, occurrenceId, nextChaseAt)
        else scheduler.cancelChase(alarmId, occurrenceId)

        return stillDue
    }

    // ------------------------------------------------------------------ evidência

    suspend fun addPhoto(instanceId: Long, absolutePath: String) {
        val order = db.evidencePhotoDao().countForInstance(instanceId)
        db.evidencePhotoDao().insert(
            EvidencePhotoEntity(
                missionInstanceId = instanceId,
                filePath = absolutePath,
                takenAt = System.currentTimeMillis(),
                sortOrder = order,
            )
        )
    }

    fun observePhotos(instanceId: Long): Flow<List<EvidencePhotoEntity>> =
        db.evidencePhotoDao().observeByInstance(instanceId)

    suspend fun removePhoto(photo: EvidencePhotoEntity) {
        db.evidencePhotoDao().delete(photo)
        evidenceStore.delete(photo.filePath)
    }

    /**
     * Fecha a missão. Recusa se ela exige foto e não há nenhuma — a evidência é o
     * ponto inteiro da mecânica.
     */
    suspend fun complete(instanceId: Long, now: Long = System.currentTimeMillis()): Boolean {
        val instance = db.missionInstanceDao().getById(instanceId) ?: return false
        // Já fechada: um toque duplo ou uma corrida entre a tela do alarme e o painel
        // não pode reescrever o horário nem pontuar de novo.
        if (instance.status == MissionStatus.COMPLETED) return true

        val mission = db.missionDao().getById(instance.missionId) ?: return false

        if (mission.requiresEvidence && db.evidencePhotoDao().countForInstance(instanceId) == 0) {
            return false
        }

        val late = instance.evidenceDeadline?.let { now > it } == true
        // Atraso custa moedas, nunca XP: o progresso de longo prazo não regride por
        // um dia ruim, e cumprir tarde continua valendo mais do que não cumprir.
        val xp = mission.xpValue
        val coins = if (late) {
            (mission.coinValue * PointsRepository.LATE_COIN_RATIO).toInt()
        } else {
            mission.coinValue
        }

        db.missionInstanceDao().update(
            instance.copy(
                status = MissionStatus.COMPLETED,
                completedAt = now,
                wasLate = late,
                xpAwarded = xp,
                coinAwarded = coins,
            )
        )
        points.awardMission(instanceId = instanceId, xp = xp, coins = coins, late = late)

        val occurrence = db.occurrenceDao().getById(instance.occurrenceId)
        if (occurrence != null) refreshChase(occurrence.id, occurrence.alarmId)
        return true
    }

    // ------------------------------------------------------------------ pendências

    /** Tudo em aberto, com a missão e a contagem de fotos já resolvidas. */
    fun observeOpenRuns(): Flow<List<MissionRun>> =
        combine(
            db.missionInstanceDao().observeOpen(),
            db.missionDao().observeAll(),
        ) { instances, missions ->
            val byId = missions.associateBy { it.id }
            instances.mapNotNull { instance ->
                byId[instance.missionId]?.let { MissionRun(it, instance) }
            }
        }

    /** Pendências de evidência de um disparo, ao vivo. */
    fun observeAwaitingRuns(occurrenceId: Long): Flow<List<MissionRun>> =
        combine(
            db.missionInstanceDao().observeAwaitingEvidence(occurrenceId),
            db.missionDao().observeAll(),
        ) { instances, missions ->
            val byId = missions.associateBy { it.id }
            instances.mapNotNull { instance ->
                byId[instance.missionId]?.let { MissionRun(it, instance) }
            }
        }

    /**
     * Fecha o que ficou aberto em dias já encerrados.
     *
     * Sem isto a tela de Pendências viraria um cemitério: missões de semanas atrás
     * acumulando sem nada capaz de resolvê-las. Um dia que passou, passou.
     */
    suspend fun sweepStale(today: String = LocalDate.now().toString()): Int {
        val stale = db.missionInstanceDao().getStaleBefore(today)
        stale.forEach {
            db.missionInstanceDao().update(it.copy(status = MissionStatus.FAILED))
        }
        if (stale.isNotEmpty()) Log.i(TAG, "Varredura fechou ${stale.size} pendência(s) vencida(s)")
        return stale.size
    }

    fun observeRunsOf(date: String): Flow<List<MissionRun>> =
        combine(
            db.missionInstanceDao().observeByDate(date),
            db.missionDao().observeAll(),
        ) { instances, missions ->
            val byId = missions.associateBy { it.id }
            instances.mapNotNull { instance ->
                byId[instance.missionId]?.let { MissionRun(it, instance) }
            }
        }

    suspend fun getRun(instanceId: Long): MissionRun? {
        val instance = db.missionInstanceDao().getById(instanceId) ?: return null
        val mission = db.missionDao().getById(instance.missionId) ?: return null
        return MissionRun(mission, instance, db.evidencePhotoDao().countForInstance(instanceId))
    }

    /** Missões que este despertador traria hoje — para a tela do alarme. */
    suspend fun missionsForToday(alarmId: Long): List<MissionEntity> =
        db.missionDao().getByAlarmForDayBit(alarmId, DayMask.bitOf(LocalDate.now().dayOfWeek))

    fun newPhotoFile(instanceId: Long, date: String) =
        evidenceStore.newPhotoFile(date, instanceId)

    private fun minOfNullable(a: Long?, b: Long): Long = if (a == null) b else minOf(a, b)

    private companion object {
        const val TAG = "MissionRepository"
    }
}
