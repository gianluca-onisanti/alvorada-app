package dev.gianluca.alvoradaapp.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.data.AlarmEntity
import dev.gianluca.alvoradaapp.data.MissionEntity
import dev.gianluca.alvoradaapp.data.MissionRun
import dev.gianluca.alvoradaapp.data.OccurrenceEntity
import dev.gianluca.alvoradaapp.data.OccurrenceStatus
import dev.gianluca.alvoradaapp.data.isOneShot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** O que está tocando agora. A `AlarmActivity` desenha a partir disto. */
data class RingingState(
    val mode: AlarmMode,
    val alarmId: Long,
    val occurrenceId: Long,
    val label: String,
    val startedAt: Long,
    val snoozeMinutes: Int,
    val snoozesLeft: Int,
    /** Despertador de uma vez só: some assim que este toque terminar. */
    val oneShot: Boolean = false,
    /** Despertar normal: as missões de hoje deste despertador. */
    val missions: List<MissionEntity> = emptyList(),
    /** Cobrança: só as missões cuja janela de evidência estourou. */
    val chaseRuns: List<MissionRun> = emptyList(),
) {
    val canSnooze: Boolean get() = snoozesLeft > 0
}

/**
 * Publicado no momento em que o alarme é dispensado, e deliberadamente **não** limpo
 * junto com o `RingingState`: é o que permite a tela do alarme seguir para a fase de
 * evidência em vez de simplesmente sumir quando o som para.
 *
 * Guarda só as chaves, não a lista de missões: a tela observa o banco por conta
 * própria, e assim uma foto enviada some da lista na hora, em vez de continuar
 * cobrando algo que já foi feito.
 */
data class PendingEvidence(
    val occurrenceId: Long,
    val alarmId: Long,
)

/**
 * Dono do alarme enquanto ele toca: segura o WakeLock, o áudio e a notificação de
 * full-screen intent. Existe como serviço em foreground porque é a única forma de o
 * sistema garantir que o processo não morre no meio do toque.
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: AlarmSoundPlayer
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenLock: PowerManager.WakeLock? = null
    private var autoSilenceJob: Job? = null

    private val container by lazy { (application as AlvoradaApp).container }

    override fun onCreate() {
        super.onCreate()
        player = AlarmSoundPlayer(this)
        Notifications.ensureChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AlarmContract.ACTION_SNOOZE -> {
                handleSnooze()
                return START_NOT_STICKY
            }

            AlarmContract.ACTION_DISMISS -> {
                val completed = intent.getLongArrayExtra(AlarmContract.EXTRA_COMPLETED_MISSIONS)
                handleDismiss(completed?.toSet().orEmpty())
                return START_NOT_STICKY
            }

            AlarmContract.ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
        }

        val mode = runCatching {
            AlarmMode.valueOf(intent?.getStringExtra(AlarmContract.EXTRA_MODE) ?: AlarmMode.WAKE.name)
        }.getOrDefault(AlarmMode.WAKE)
        val alarmId = intent?.getLongExtra(AlarmContract.EXTRA_ALARM_ID, AlarmContract.NO_ID)
            ?: AlarmContract.NO_ID
        val occurrenceId = intent?.getLongExtra(AlarmContract.EXTRA_OCCURRENCE_ID, AlarmContract.NO_ID)
            ?: AlarmContract.NO_ID

        // Precisa acontecer nos primeiros segundos, antes de qualquer I/O:
        // startForegroundService sem startForeground rápido derruba o processo.
        goForeground(
            title = if (mode == AlarmMode.TEST) "Alarme de teste" else "Despertador",
            body = "Tocando…",
            mode = mode,
            alarmId = alarmId,
            occurrenceId = occurrenceId,
        )
        acquireWakeLock()

        scope.launch { fire(mode, alarmId, occurrenceId) }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ disparo

    private suspend fun fire(mode: AlarmMode, alarmId: Long, occurrenceId: Long) {
        if (mode == AlarmMode.TEST) {
            fireTest()
            return
        }

        val alarm = withContext(Dispatchers.IO) { container.db.alarmDao().getById(alarmId) }
        if (alarm == null) {
            Log.w(TAG, "Alarme $alarmId não existe mais — encerrando")
            shutdown()
            return
        }

        if (mode == AlarmMode.CHASE) {
            fireChase(alarm, occurrenceId)
            return
        }

        val occurrence = withContext(Dispatchers.IO) { resolveOccurrence(alarm, occurrenceId, mode) }

        // O próximo disparo recorrente é agendado agora, não no momento de dispensar:
        // se o alarme tocar sozinho até o fim, o de amanhã já está de pé.
        if (mode == AlarmMode.WAKE) {
            withContext(Dispatchers.IO) { container.alarmScheduler.scheduleNextWake(alarm) }
        }

        val missions = withContext(Dispatchers.IO) {
            container.missionRepository.missionsForToday(alarm.id)
        }

        _state.value = RingingState(
            mode = mode,
            alarmId = alarm.id,
            occurrenceId = occurrence.id,
            label = alarm.label.ifBlank { "Despertador" },
            startedAt = System.currentTimeMillis(),
            snoozeMinutes = alarm.snoozeMinutes,
            snoozesLeft = (alarm.maxSnoozes - occurrence.snoozeCount).coerceAtLeast(0),
            oneShot = alarm.isOneShot,
            missions = missions,
        )

        val snoozesLeft = (alarm.maxSnoozes - occurrence.snoozeCount).coerceAtLeast(0)
        updateNotification(
            title = alarm.label.ifBlank { "Despertador" },
            body = "Tocando…",
            mode = mode,
            alarmId = alarm.id,
            occurrenceId = occurrence.id,
            canSnooze = snoozesLeft > 0,
            snoozeMinutes = alarm.snoozeMinutes,
        )
        startSound(alarm)
        startAutoSilence()
        launchAlarmScreen(mode, alarm.id, occurrence.id)
    }

    private fun fireTest() {
        _state.value = RingingState(
            mode = AlarmMode.TEST,
            alarmId = AlarmContract.NO_ID,
            occurrenceId = AlarmContract.NO_ID,
            label = "Alarme de teste",
            startedAt = System.currentTimeMillis(),
            snoozeMinutes = 0,
            snoozesLeft = 0,
        )
        player.start(soundUri = null, volumePercent = 100, escalate = false, vibrate = true)
        startAutoSilence()
        launchAlarmScreen(AlarmMode.TEST, AlarmContract.NO_ID, AlarmContract.NO_ID)
    }

    /**
     * Cobrança. Se nada estiver vencido — porque as fotos chegaram enquanto o alarme
     * vinha a caminho — encerra em silêncio. Ser cobrado por algo já feito é o tipo de
     * atrito que faz alguém desinstalar o app.
     */
    private suspend fun fireChase(alarm: AlarmEntity, occurrenceId: Long) {
        val due = withContext(Dispatchers.IO) {
            container.missionRepository.onChaseFired(occurrenceId, alarm.id)
        }

        if (due.isEmpty()) {
            Log.i(TAG, "Cobrança do disparo $occurrenceId sem pendências — encerrando")
            shutdown()
            return
        }

        _state.value = RingingState(
            mode = AlarmMode.CHASE,
            alarmId = alarm.id,
            occurrenceId = occurrenceId,
            label = alarm.label.ifBlank { "Despertador" },
            startedAt = System.currentTimeMillis(),
            snoozeMinutes = alarm.snoozeMinutes,
            snoozesLeft = 0, // Cobrança não tem soneca: adiar é justamente o problema.
            chaseRuns = due,
        )

        updateNotification(
            title = "Faltou a evidência",
            body = due.joinToString(", ") { it.mission.title },
            mode = AlarmMode.CHASE,
            alarmId = alarm.id,
            occurrenceId = occurrenceId,
        )
        startSound(alarm)
        startAutoSilence()
        launchAlarmScreen(AlarmMode.CHASE, alarm.id, occurrenceId)
    }

    private fun startSound(alarm: AlarmEntity) {
        player.start(
            soundUri = alarm.soundUri?.let(Uri::parse),
            volumePercent = alarm.volumePercent,
            escalate = alarm.escalateVolume,
            vibrate = alarm.vibrate,
        )
    }

    /** Reusa o disparo em curso (soneca) ou cria um novo (despertar normal). */
    private suspend fun resolveOccurrence(
        alarm: AlarmEntity,
        occurrenceId: Long,
        mode: AlarmMode,
    ): OccurrenceEntity {
        val dao = container.db.occurrenceDao()
        if (occurrenceId != AlarmContract.NO_ID) {
            dao.getById(occurrenceId)?.let { existing ->
                val updated = existing.copy(
                    status = OccurrenceStatus.FIRED,
                    firedAt = System.currentTimeMillis(),
                )
                dao.update(updated)
                return updated
            }
        }
        val now = System.currentTimeMillis()
        val fresh = OccurrenceEntity(
            alarmId = alarm.id,
            scheduledFor = now,
            firedAt = now,
            status = OccurrenceStatus.FIRED,
        )
        val id = dao.insert(fresh)
        Log.i(TAG, "Disparo $id criado para o alarme ${alarm.id} (modo $mode)")
        return fresh.copy(id = id)
    }

    // ------------------------------------------------------------------ ações

    private fun handleSnooze() {
        val current = _state.value ?: run { shutdown(); return }
        if (!current.canSnooze) {
            Log.i(TAG, "Soneca negada: teto atingido")
            return
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                val dao = container.db.occurrenceDao()
                dao.getById(current.occurrenceId)?.let {
                    dao.update(it.copy(status = OccurrenceStatus.SNOOZED, snoozeCount = it.snoozeCount + 1))
                }
                container.alarmScheduler.scheduleSnooze(
                    alarmId = current.alarmId,
                    occurrenceId = current.occurrenceId,
                    minutes = current.snoozeMinutes,
                )
                // Soneca custa moedas; o XP fica intacto.
                container.pointsRepository.penalizeSnooze()
            }
            shutdown()
        }
    }

    private fun handleDismiss(completedMissions: Set<Long>) {
        val current = _state.value ?: run { shutdown(); return }
        if (current.mode == AlarmMode.TEST) {
            shutdown()
            return
        }

        scope.launch {
            withContext(Dispatchers.IO) {
                if (current.mode == AlarmMode.CHASE) {
                    // A próxima cobrança já foi agendada no disparo; aqui só silencia.
                    publishPendingEvidence(current.occurrenceId, current.alarmId)
                    return@withContext
                }

                val dao = container.db.occurrenceDao()
                dao.getById(current.occurrenceId)?.let {
                    dao.update(
                        it.copy(
                            status = OccurrenceStatus.DISMISSED,
                            dismissedAt = System.currentTimeMillis(),
                        )
                    )
                }
                container.alarmScheduler.cancelSnooze(current.alarmId, current.occurrenceId)

                container.missionRepository.materializeOnDismiss(
                    occurrenceId = current.occurrenceId,
                    alarmId = current.alarmId,
                    completedNow = completedMissions,
                )
                publishPendingEvidence(current.occurrenceId, current.alarmId)
                consumeIfOneShot(current)
            }
            shutdown()
        }
    }

    /**
     * A autodestruição em si: o despertador de uma vez só termina aqui.
     *
     * Acontece ao dispensar e ao silenciar sozinho, que são os dois jeitos de um toque
     * acabar sem deixar outro marcado. Adiar não conta — a soneca tem um disparo a
     * caminho, e apagar o despertador no meio dela seria apagar esse disparo junto.
     */
    private suspend fun consumeIfOneShot(current: RingingState) {
        if (!current.oneShot) return
        container.alarmRepository.consumeOneShot(current.alarmId, current.occurrenceId)
        Log.i(TAG, "Despertador único ${current.alarmId} cumpriu seu papel e se apagou")
    }

    private suspend fun publishPendingEvidence(occurrenceId: Long, alarmId: Long) {
        val pending = container.db.missionInstanceDao().getAwaitingEvidence(occurrenceId)
        _pendingEvidence.value =
            if (pending.isEmpty()) null else PendingEvidence(occurrenceId, alarmId)
    }

    /**
     * Ninguém interagiu. Encerra em vez de tocar para sempre — bateria e vizinhos.
     * Num despertar normal o disparo fica marcado como MISSED, que é o que o painel
     * usa para diferenciar "dispensei" de "dormi por cima". Numa cobrança não há o que
     * marcar: a próxima já está agendada.
     */
    private fun startAutoSilence() {
        autoSilenceJob?.cancel()
        autoSilenceJob = scope.launch {
            delay(AUTO_SILENCE_MS)
            val current = _state.value
            if (current != null && current.mode != AlarmMode.TEST && current.mode != AlarmMode.CHASE) {
                withContext(Dispatchers.IO) {
                    val dao = container.db.occurrenceDao()
                    dao.getById(current.occurrenceId)?.let {
                        dao.update(it.copy(status = OccurrenceStatus.MISSED))
                    }
                    // Ninguém atendeu, e um despertador único não tem próximo toque
                    // para agendar: mantê-lo vivo deixaria na lista um alarme ligado
                    // que nunca mais vai tocar.
                    consumeIfOneShot(current)
                }
            }
            Log.i(TAG, "Silenciado automaticamente após ${AUTO_SILENCE_MS / 60_000} min")
            shutdown()
        }
    }

    // ------------------------------------------------------------------ infra

    private fun goForeground(
        title: String,
        body: String,
        mode: AlarmMode,
        alarmId: Long,
        occurrenceId: Long,
    ) {
        startForeground(
            AlarmContract.FOREGROUND_NOTIFICATION_ID,
            Notifications.buildAlarmNotification(this, title, body, mode, alarmId, occurrenceId),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun updateNotification(
        title: String,
        body: String,
        mode: AlarmMode,
        alarmId: Long,
        occurrenceId: Long,
        canSnooze: Boolean = false,
        snoozeMinutes: Int = 0,
    ) {
        getSystemService(NotificationManager::class.java).notify(
            AlarmContract.FOREGROUND_NOTIFICATION_ID,
            Notifications.buildAlarmNotification(
                context = this,
                title = title,
                body = body,
                mode = mode,
                alarmId = alarmId,
                occurrenceId = occurrenceId,
                canSnooze = canSnooze,
                snoozeMinutes = snoozeMinutes,
            ),
        )
    }

    /**
     * O full-screen intent da notificação já abre a tela com o aparelho bloqueado.
     * Com o aparelho desbloqueado e em uso ele vira só um heads-up, então tentamos
     * também o start direto — a allowlist temporária concedida ao receber um alarme
     * exato permite iniciar Activity a partir do background nessa janela.
     */
    private fun launchAlarmScreen(mode: AlarmMode, alarmId: Long, occurrenceId: Long) {
        runCatching {
            startActivity(
                Intent(this, AlarmActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(AlarmContract.EXTRA_MODE, mode.name)
                    putExtra(AlarmContract.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmContract.EXTRA_OCCURRENCE_ID, occurrenceId)
                }
            )
        }.onFailure { Log.w(TAG, "Start direto da tela bloqueado; seguindo pelo full-screen intent", it) }
    }

    /**
     * Dois locks, com papéis distintos.
     *
     * O parcial garante que a CPU não volte a dormir no meio do toque. Ele **não**
     * acende a tela — e é por isso que `setTurnScreenOn` sozinho falhava: com o
     * aparelho em Doze profundo, quem liga o display fisicamente é um lock de tela
     * com `ACQUIRE_CAUSES_WAKEUP`. Sem ele, o alarme tocava com a tela apagada e não
     * havia onde tocar para desligar ou adiar.
     *
     * `SCREEN_BRIGHT_WAKE_LOCK` está depreciado desde a API 17 em favor de
     * `FLAG_KEEP_SCREEN_ON`, mas a substituta só mantém acesa uma tela que já está
     * acesa — não serve para acordar o aparelho. Este é o caminho que ainda funciona.
     */
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(AUTO_SILENCE_MS + 60_000L)
        }

        @Suppress("DEPRECATION")
        screenLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            SCREEN_LOCK_TAG,
        ).apply {
            setReferenceCounted(false)
            // Solto junto com o alarme: o `ON_AFTER_RELEASE` deixa a tela acesa pelo
            // tempo normal do sistema depois disso, em vez de apagar na cara de quem
            // acabou de acordar.
            acquire(AUTO_SILENCE_MS + 60_000L)
        }
    }

    private fun releaseWakeLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        screenLock?.let { if (it.isHeld) it.release() }
        screenLock = null
    }

    private fun shutdown() {
        autoSilenceJob?.cancel()
        player.stop()
        _state.value = null
        releaseWakeLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        autoSilenceJob?.cancel()
        player.stop()
        releaseWakeLocks()
        _state.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val WAKE_LOCK_TAG = "alvoradaapp:alarm"
        private const val SCREEN_LOCK_TAG = "alvoradaapp:alarm-screen"
        private const val AUTO_SILENCE_MS = 5 * 60_000L

        private val _state = MutableStateFlow<RingingState?>(null)
        private val _pendingEvidence = MutableStateFlow<PendingEvidence?>(null)

        /** Fonte de verdade da tela do alarme. `null` = nada tocando. */
        val state: StateFlow<RingingState?> = _state.asStateFlow()

        /** Sobrevive ao fim do som: é a ponte para a fase de evidência. */
        val pendingEvidence: StateFlow<PendingEvidence?> = _pendingEvidence.asStateFlow()

        fun clearPendingEvidence() {
            _pendingEvidence.value = null
        }

        /** `true` quando há som tocando agora — a tela do app usa isto para se impor. */
        fun isRinging(): Boolean = _state.value != null

        /**
         * Cala o alarme deste despertador, se for ele que está tocando.
         *
         * Existe porque desativar ou excluir um despertador enquanto ele toca não
         * fazia nada: o serviço já tinha carregado o alarme e o som na memória e não
         * volta ao banco. Do lado de fora isso parecia um app que se recusa a obedecer.
         */
        fun stopIfRinging(context: Context, alarmId: Long) {
            if (_state.value?.alarmId != alarmId) return
            send(context, AlarmContract.ACTION_STOP) {}
        }

        fun snooze(context: Context) = send(context, AlarmContract.ACTION_SNOOZE) {}

        fun dismiss(context: Context, completedMissions: Set<Long> = emptySet()) =
            send(context, AlarmContract.ACTION_DISMISS) {
                putExtra(AlarmContract.EXTRA_COMPLETED_MISSIONS, completedMissions.toLongArray())
            }

        private fun send(context: Context, action: String, extras: Intent.() -> Unit) {
            context.startService(
                Intent(context, AlarmService::class.java).apply {
                    this.action = action
                    extras()
                }
            )
        }
    }
}
