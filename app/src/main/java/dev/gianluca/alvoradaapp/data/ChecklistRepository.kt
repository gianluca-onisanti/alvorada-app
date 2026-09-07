package dev.gianluca.alvoradaapp.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.ZonedDateTime

/** Um item da lista junto do seu estado na rodada corrente. */
data class ChecklistItemRun(
    val item: ChecklistItemEntity,
    val state: ChecklistItemStateEntity,
) {
    val done: Boolean get() = state.done

    /** Tem despertador próprio? É o que decide o ícone de sino na linha. */
    val hasAlarm: Boolean get() = item.alarmId != null
}

/** Um checklist na sua rodada corrente, com tudo que a tela precisa mostrar. */
data class ChecklistRun(
    val checklist: ChecklistEntity,
    val folder: FolderEntity?,
    val cycle: ChecklistCycleEntity,
    val items: List<ChecklistItemRun>,
) {
    val total: Int get() = items.size
    val done: Int get() = items.count { it.done }
    val isComplete: Boolean get() = total > 0 && done == total
    fun isOverdue(now: Long = System.currentTimeMillis()): Boolean =
        !isComplete && now > cycle.dueAt
}

/**
 * Listas recorrentes com vencimento, e o despertador que elas calam.
 *
 * A estrutura espelha `MissionRepository` de propósito — cadastro, a rodada,
 * conclusão, varredura — porque o problema é o mesmo com outro relógio: materializar
 * o estado de um período, deixar marcar, creditar uma vez só, e fechar o que venceu.
 *
 * Depende de [AlarmRepository] e não do DAO de alarmes: calar um despertador é uma
 * escrita em `alarms`, e a V1 estabeleceu que toda escrita ali passa pelo repositório
 * para que banco e `AlarmManager` nunca discordem.
 */
class ChecklistRepository(
    private val db: AlvoradaDatabase,
    private val alarms: AlarmRepository,
    private val points: PointsRepository,
) {

    // ---- cadastro

    fun observeChecklists(): Flow<List<ChecklistEntity>> = db.checklistDao().observeAll()

    fun observeItems(checklistId: Long): Flow<List<ChecklistItemEntity>> =
        db.checklistDao().observeItems(checklistId)

    suspend fun getChecklist(id: Long): ChecklistEntity? = db.checklistDao().getById(id)

    suspend fun saveChecklist(checklist: ChecklistEntity): Long {
        val id = db.checklistDao().upsert(checklist)
            .let { if (checklist.id == 0L) it else checklist.id }
        // Editar a frequência muda onde as rodadas caem, e a rodada aberta pode ter
        // deixado de existir. Reabrir agora evita uma tela mostrando um vencimento
        // que a configuração nova não produz mais.
        ensureCycle(id)
        return id
    }

    suspend fun deleteChecklist(checklist: ChecklistEntity) {
        // Os ids são colhidos **antes** do CASCADE, porque depois dele os itens não
        // existem mais para dizer que despertadores tocavam por eles. O recálculo vem
        // **depois**, porque só aí a resposta é a certa — e sem ele um despertador
        // calado por um checklist apagado nunca mais voltaria a tocar. O Room apaga
        // as linhas, mas o `AlarmManager` não sabe disso: mesma lição de
        // `deleteFolder`.
        val affected = db.checklistDao().getItems(checklist.id).mapNotNull { it.alarmId }.distinct()
        db.checklistDao().delete(checklist)
        affected.forEach { refreshAlarmSuppression(it) }
    }

    suspend fun saveItem(item: ChecklistItemEntity): Long {
        val previous = if (item.id == 0L) null else db.checklistDao().getItem(item.id)
        val id = db.checklistDao().upsertItem(item).let { if (item.id == 0L) it else item.id }

        // Trocar ou remover o despertador de um item precisa destravar o antigo —
        // senão ele fica calado para sempre por um vínculo que não existe mais.
        val oldAlarm = previous?.alarmId
        if (oldAlarm != null && oldAlarm != item.alarmId) refreshAlarmSuppression(oldAlarm)

        ensureCycle(item.checklistId)
        return id
    }

    suspend fun deleteItem(item: ChecklistItemEntity) {
        db.checklistDao().deleteItem(item)
        item.alarmId?.let { refreshAlarmSuppression(it) }
        // Apagar o último item pendente pode ter fechado a rodada.
        db.checklistDao().getCycle(item.checklistId, currentPeriodStart(item.checklistId))
            ?.let { refreshCompletion(it.id) }
    }

    // ---- a rodada

    /**
     * Abre a rodada vigente deste checklist, se ela ainda não existe, e materializa o
     * estado de cada item nela. Devolve a rodada, ou `null` se o checklist está
     * desligado ou configurado de um jeito que nunca acontece.
     *
     * Idempotente pelo índice único em (checklistId, periodStart): abrir a tela junto
     * com a varredura das 03h é uma corrida esperada, e as duas chamadas convergem
     * para a mesma rodada em vez de criarem duas.
     */
    suspend fun ensureCycle(
        checklistId: Long,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): ChecklistCycleEntity? {
        val checklist = db.checklistDao().getById(checklistId) ?: return null
        val cycle = checklist.currentCycle(now) ?: return null
        val dao = db.checklistDao()

        dao.insertCycle(
            ChecklistCycleEntity(
                checklistId = checklistId,
                periodStart = cycle.periodStart.toString(),
                dueAt = cycle.dueAt,
                endsAt = cycle.endsAt,
            )
        )
        val saved = dao.getCycle(checklistId, cycle.periodStart.toString()) ?: return null

        // Itens criados depois da rodada ter aberto também precisam de estado nela.
        // `IGNORE` no insert deixa os que já existem intocados.
        val items = dao.getItems(checklistId)
        val existing = dao.getStates(saved.id).map { it.itemId }.toSet()
        val missing = items.filter { it.id !in existing }
        if (missing.isNotEmpty()) {
            dao.insertStates(missing.map { ChecklistItemStateEntity(cycleId = saved.id, itemId = it.id) })
        }
        return saved
    }

    /** Abre a rodada de todos os checklists ligados. Usado pela tela e pela varredura. */
    suspend fun ensureAllCycles(now: ZonedDateTime = ZonedDateTime.now()) {
        db.checklistDao().getEnabled().forEach { ensureCycle(it.id, now) }
    }

    /**
     * Tudo que a tela de Checklists mostra, já com pasta, rodada e estado de cada item.
     *
     * Combina os quatro fluxos em vez de resolver num JOIN só porque a rodada corrente
     * depende do relógio, e não de uma coluna: qual rodada está viva muda sozinho à
     * meia-noite, sem nenhuma escrita no banco para o SQL observar.
     */
    fun observeRuns(): Flow<List<ChecklistRun>> = combine(
        db.checklistDao().observeAll(),
        db.checklistDao().observeAllItems(),
        db.checklistDao().observeLiveCycles(),
        db.checklistDao().observeLiveStates(),
        db.folderDao().observeAll(),
    ) { checklists, items, cycles, states, folders ->
        val foldersById = folders.associateBy { it.id }
        val itemsByChecklist = items.groupBy { it.checklistId }
        val statesByCycle = states.groupBy { it.cycleId }
        val now = ZonedDateTime.now()

        checklists.filter { it.enabled }.mapNotNull { checklist ->
            val expected = checklist.currentCycle(now) ?: return@mapNotNull null
            val cycle = cycles.firstOrNull {
                it.checklistId == checklist.id && it.periodStart == expected.periodStart.toString()
            } ?: return@mapNotNull null

            val stateByItem = statesByCycle[cycle.id].orEmpty().associateBy { it.itemId }
            val runs = itemsByChecklist[checklist.id].orEmpty()
                .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                .mapNotNull { item ->
                    val state = stateByItem[item.id] ?: return@mapNotNull null
                    ChecklistItemRun(item = item, state = state)
                }

            ChecklistRun(
                checklist = checklist,
                folder = foldersById[checklist.folderId],
                cycle = cycle,
                items = runs,
            )
        }
    }

    // ---- conclusão

    /**
     * Marca ou desmarca um item, credita os pontos e cala (ou destrava) o despertador
     * dele até o fim da rodada.
     *
     * Desmarcar destrava o despertador, mas **não estorna pontos**. XP nunca decresce
     * neste app, e estornar moedas abriria a porta para saldo negativo, que não
     * existe aqui. A guarda de idempotência do extrato cuida do resto: marcar de novo
     * não credita duas vezes.
     */
    suspend fun toggleItem(
        cycleId: Long,
        itemId: Long,
        done: Boolean,
        now: Long = System.currentTimeMillis(),
    ) {
        val dao = db.checklistDao()
        val state = dao.getState(cycleId, itemId) ?: return
        if (state.done == done) return

        val item = dao.getItem(itemId) ?: return
        val cycle = dao.getCycleById(cycleId) ?: return

        dao.updateState(
            state.copy(
                done = done,
                doneAt = if (done) now else null,
                xpAwarded = if (done) item.xpValue else 0,
                coinAwarded = if (done) item.coinValue else 0,
            )
        )

        if (done) points.awardChecklistItem(state.id, item.xpValue, item.coinValue)
        item.alarmId?.let { refreshAlarmSuppression(it) }

        refreshCompletion(cycleId, now)
    }

    /**
     * Reconcilia o estado da rodada depois de qualquer mudança. Ponto único, no papel
     * que `refreshChase` cumpre para missões.
     *
     * Fechar a rodada cala **todos** os despertadores vinculados, e não só o do item
     * que a fechou: com o checklist inteiro preenchido, nenhum deles tem mais o que
     * cobrar até a próxima rodada.
     */
    suspend fun refreshCompletion(cycleId: Long, now: Long = System.currentTimeMillis()) {
        val dao = db.checklistDao()
        val cycle = dao.getCycleById(cycleId) ?: return
        if (cycle.status == ChecklistCycleStatus.EXPIRED) return

        val states = dao.getStates(cycleId)
        val complete = states.isNotEmpty() && states.all { it.done }

        when {
            complete && cycle.status != ChecklistCycleStatus.COMPLETED -> {
                dao.updateCycle(
                    cycle.copy(status = ChecklistCycleStatus.COMPLETED, completedAt = now)
                )
                points.awardChecklistComplete(cycleId)
                dao.getItems(cycle.checklistId).mapNotNull { it.alarmId }.distinct()
                    .forEach { refreshAlarmSuppression(it) }
                Log.i(TAG, "Rodada $cycleId fechada com ${states.size} item(ns)")
            }
            // Desmarcar um item reabre a rodada. O bônus já creditado fica: ele
            // registra que a lista esteve completa, e o extrato é append-only.
            !complete && cycle.status == ChecklistCycleStatus.COMPLETED -> {
                dao.updateCycle(cycle.copy(status = ChecklistCycleStatus.OPEN, completedAt = null))
            }
        }
    }

    // ---- varredura

    /**
     * Fecha as rodadas que venceram e abre as seguintes.
     *
     * Roda na varredura das 03h, antes do reagendamento em massa, para que a supressão
     * já esteja destravada quando os alarmes forem reafirmados. Devolve quantas
     * rodadas foram fechadas.
     */
    suspend fun sweepCycles(now: ZonedDateTime = ZonedDateTime.now()): Int {
        val dao = db.checklistDao()
        val millis = now.toInstant().toEpochMilli()
        var closed = 0

        dao.getOpenCycles().filter { millis >= it.endsAt }.forEach { cycle ->
            dao.updateCycle(cycle.copy(status = ChecklistCycleStatus.EXPIRED))
            closed++
        }

        // Rodadas fechadas cujo prazo de silêncio acabou: os despertadores voltam.
        // `suppressedUntil` já teria caducado sozinho no cálculo do próximo disparo,
        // mas limpar a coluna mantém a lista honesta sobre o que está calado.
        db.alarmDao().getEnabled()
            .filter { it.suppressedUntil != null && millis >= it.suppressedUntil!! }
            .forEach { alarms.clearSuppression(it.id) }

        ensureAllCycles(now)
        if (closed > 0) Log.i(TAG, "Varredura fechou $closed rodada(s) vencida(s)")
        return closed
    }

    /**
     * Os itens de checklist que este despertador traz agora, para a tela do alarme.
     *
     * Só os ainda não marcados: ser cobrado por algo já feito é o atrito que faria
     * qualquer um desinstalar o app.
     */
    suspend fun pendingItemsForAlarm(alarmId: Long): List<ChecklistItemRun> {
        val dao = db.checklistDao()
        return dao.getItemsForAlarm(alarmId).mapNotNull { item ->
            val cycle = ensureCycle(item.checklistId) ?: return@mapNotNull null
            val state = dao.getState(cycle.id, item.id) ?: return@mapNotNull null
            if (state.done) null else ChecklistItemRun(item, state)
        }
    }

    // ---- interno

    /**
     * Recalcula, do zero, se este despertador deve estar calado.
     *
     * Ponto único de decisão, e a regra é: **todos** os itens que apontam para ele
     * precisam estar marcados. Suprimir ao marcar o primeiro estaria errado — se o
     * despertador serve a dois itens e só um foi feito, ele ainda tem o que cobrar.
     *
     * Um despertador pode receber itens de checklists diferentes, com rodadas de
     * tamanhos diferentes. O silêncio vale até a **primeira** delas virar: a partir
     * daí o item daquela rodada volta a estar em aberto.
     */
    private suspend fun refreshAlarmSuppression(alarmId: Long) {
        val dao = db.checklistDao()
        val items = dao.getItemsForAlarm(alarmId)
        if (items.isEmpty()) {
            alarms.clearSuppression(alarmId)
            return
        }

        var earliestEnd = Long.MAX_VALUE
        for (item in items) {
            val cycle = ensureCycle(item.checklistId)
            val state = cycle?.let { dao.getState(it.id, item.id) }
            if (cycle == null || state == null || !state.done) {
                alarms.clearSuppression(alarmId)
                return
            }
            earliestEnd = minOf(earliestEnd, cycle.endsAt)
        }
        alarms.suppressUntil(alarmId, earliestEnd)
    }

    private suspend fun currentPeriodStart(checklistId: Long): String {
        val checklist = db.checklistDao().getById(checklistId) ?: return ""
        return checklist.currentCycle()?.periodStart?.toString() ?: ""
    }

    private companion object {
        const val TAG = "ChecklistRepository"
    }
}
