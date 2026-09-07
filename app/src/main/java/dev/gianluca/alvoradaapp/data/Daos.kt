package dev.gianluca.alvoradaapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY sortOrder, name")
    suspend fun getAll(): List<FolderEntity>

    @Upsert
    suspend fun upsert(folder: FolderEntity): Long

    @Query("UPDATE folders SET collapsed = :collapsed WHERE id = :id")
    suspend fun setCollapsed(id: Long, collapsed: Boolean)

    @Delete
    suspend fun delete(folder: FolderEntity)
}

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE folderId = :folderId ORDER BY hour, minute")
    fun observeByFolder(folderId: Long): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    /** Fonte de verdade do reagendamento em massa (boot, update, mudança de fuso). */
    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun getEnabled(): List<AlarmEntity>

    @Upsert
    suspend fun upsert(alarm: AlarmEntity): Long

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** `null` desfaz o pulo. Ver `AlarmEntity.skipNextFireAt`. */
    @Query("UPDATE alarms SET skipNextFireAt = :fireAt WHERE id = :id")
    suspend fun setSkipNextFireAt(id: Long, fireAt: Long?)

    /** `null` destrava o despertador. Ver `AlarmEntity.suppressedUntil`. */
    @Query("UPDATE alarms SET suppressedUntil = :until WHERE id = :id")
    suspend fun setSuppressedUntil(id: Long, until: Long?)

    /** Reaponta um som próprio migrado para o novo caminho na biblioteca pública. */
    @Query("UPDATE alarms SET soundUri = :to WHERE soundUri = :from")
    suspend fun rewriteSoundUri(from: String, to: String)

    @Delete
    suspend fun delete(alarm: AlarmEntity)
}

@Dao
interface MissionDao {

    @Query("SELECT * FROM missions WHERE alarmId = :alarmId AND archived = 0 ORDER BY sortOrder, id")
    fun observeByAlarm(alarmId: Long): Flow<List<MissionEntity>>

    @Query("SELECT * FROM missions WHERE alarmId = :alarmId AND archived = 0 ORDER BY sortOrder, id")
    suspend fun getByAlarm(alarmId: Long): List<MissionEntity>

    /**
     * Missões do alarme ativas num dia específico. `:dayBit` é `1 shl (dow - 1)`,
     * calculado por `DayMask.bitOf`.
     */
    @Query(
        """
        SELECT * FROM missions
        WHERE alarmId = :alarmId AND archived = 0 AND (daysMask & :dayBit) != 0
        ORDER BY sortOrder, id
        """
    )
    suspend fun getByAlarmForDayBit(alarmId: Long, dayBit: Int): List<MissionEntity>

    @Query("SELECT * FROM missions WHERE id = :id")
    suspend fun getById(id: Long): MissionEntity?

    @Query("SELECT * FROM missions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<MissionEntity>

    @Query("SELECT * FROM missions WHERE archived = 0")
    fun observeAll(): Flow<List<MissionEntity>>

    @Upsert
    suspend fun upsert(mission: MissionEntity): Long

    /** Usado ao encolher os dias do alarme: poda os dias que deixaram de existir. */
    @Query("UPDATE missions SET daysMask = daysMask & :allowedMask WHERE alarmId = :alarmId")
    suspend fun pruneDaysTo(alarmId: Long, allowedMask: Int)

    /**
     * Copia os dias do despertador para quem escolheu segui-lo.
     *
     * Atribuição, e não interseção como na poda: seguir os dias do dono significa
     * também **ganhar** os dias que ele passou a ter, não só perder os que sumiram.
     */
    @Query("UPDATE missions SET daysMask = :mask WHERE alarmId = :alarmId AND followsAlarmDays = 1")
    suspend fun syncFollowersTo(alarmId: Long, mask: Int)

    @Query("DELETE FROM missions WHERE alarmId = :alarmId")
    suspend fun deleteByAlarm(alarmId: Long)

    @Delete
    suspend fun delete(mission: MissionEntity)
}

@Dao
interface OccurrenceDao {

    @Query("SELECT * FROM alarm_occurrences WHERE id = :id")
    suspend fun getById(id: Long): OccurrenceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(occurrence: OccurrenceEntity): Long

    @Update
    suspend fun update(occurrence: OccurrenceEntity)

    @Query("SELECT * FROM alarm_occurrences WHERE status IN ('FIRED', 'SNOOZED')")
    suspend fun getActive(): List<OccurrenceEntity>

    /**
     * Disparos já dispensados que ainda têm foto pendente. É o que o reagendamento
     * em massa consulta para reconstruir as cobranças depois de um reboot — sem isso
     * uma cobrança pendente sumiria ao reiniciar o aparelho.
     */
    @Query(
        """
        SELECT DISTINCT o.* FROM alarm_occurrences o
        INNER JOIN mission_instances i ON i.occurrenceId = o.id
        WHERE i.status = 'AWAITING_EVIDENCE'
        """
    )
    suspend fun getWithPendingEvidence(): List<OccurrenceEntity>
}

@Dao
interface MissionInstanceDao {

    @Query("SELECT * FROM mission_instances WHERE date = :date")
    fun observeByDate(date: String): Flow<List<MissionInstanceEntity>>

    @Query("SELECT * FROM mission_instances WHERE occurrenceId = :occurrenceId")
    suspend fun getByOccurrence(occurrenceId: Long): List<MissionInstanceEntity>

    /**
     * Pendências de evidência de um disparo. É o que a cobrança consulta para decidir
     * se ainda tem motivo para despertar de novo.
     */
    @Query(
        """
        SELECT * FROM mission_instances
        WHERE occurrenceId = :occurrenceId AND status = 'AWAITING_EVIDENCE'
        """
    )
    suspend fun getAwaitingEvidence(occurrenceId: Long): List<MissionInstanceEntity>

    @Query(
        """
        SELECT * FROM mission_instances
        WHERE occurrenceId = :occurrenceId AND status = 'AWAITING_EVIDENCE'
        """
    )
    fun observeAwaitingEvidence(occurrenceId: Long): Flow<List<MissionInstanceEntity>>

    /** Varredura diária: tudo que ficou aberto em dias já encerrados. */
    @Query(
        """
        SELECT * FROM mission_instances
        WHERE date < :today AND status IN ('PENDING', 'AWAITING_EVIDENCE')
        """
    )
    suspend fun getStaleBefore(today: String): List<MissionInstanceEntity>

    /** Tudo que ainda está em aberto, de qualquer dia. Base da tela de Pendências. */
    @Query(
        """
        SELECT * FROM mission_instances
        WHERE status IN ('PENDING', 'AWAITING_EVIDENCE')
        ORDER BY evidenceDeadline IS NULL, evidenceDeadline, date
        """
    )
    fun observeOpen(): Flow<List<MissionInstanceEntity>>

    /** Evita instância duplicada se o mesmo disparo for dispensado duas vezes. */
    @Query("SELECT COUNT(*) FROM mission_instances WHERE occurrenceId = :occurrenceId")
    suspend fun countForOccurrence(occurrenceId: Long): Int

    @Insert
    suspend fun insertAll(instances: List<MissionInstanceEntity>): List<Long>

    @Update
    suspend fun update(instance: MissionInstanceEntity)

    @Query("SELECT * FROM mission_instances WHERE id = :id")
    suspend fun getById(id: Long): MissionInstanceEntity?
}

/**
 * Consultas de leitura do painel e da galeria. Só JOIN e agregação — nenhuma escrita.
 */
@Dao
interface PanelDao {

    @Query(
        """
        SELECT i.id AS instanceId, i.status AS status, i.date AS date,
               i.evidenceDeadline AS evidenceDeadline, i.chaseCount AS chaseCount,
               i.completedAt AS completedAt, i.wasLate AS wasLate,
               m.id AS missionId, m.title AS missionTitle,
               m.requiresEvidence AS requiresEvidence, m.maxChases AS maxChases,
               f.id AS folderId, f.name AS folderName, f.colorHex AS folderColor
        FROM mission_instances i
        INNER JOIN missions m ON m.id = i.missionId
        INNER JOIN alarms a ON a.id = m.alarmId
        INNER JOIN folders f ON f.id = a.folderId
        WHERE i.date = :date
        ORDER BY f.sortOrder, f.name, m.sortOrder, m.id
        """
    )
    fun observeDay(date: String): Flow<List<DayMissionRow>>

    /** Tudo em aberto, de qualquer dia — a seção "precisa de você" do painel. */
    @Query(
        """
        SELECT i.id AS instanceId, i.status AS status, i.date AS date,
               i.evidenceDeadline AS evidenceDeadline, i.chaseCount AS chaseCount,
               i.completedAt AS completedAt, i.wasLate AS wasLate,
               m.id AS missionId, m.title AS missionTitle,
               m.requiresEvidence AS requiresEvidence, m.maxChases AS maxChases,
               f.id AS folderId, f.name AS folderName, f.colorHex AS folderColor
        FROM mission_instances i
        INNER JOIN missions m ON m.id = i.missionId
        INNER JOIN alarms a ON a.id = m.alarmId
        INNER JOIN folders f ON f.id = a.folderId
        WHERE i.status IN ('PENDING', 'AWAITING_EVIDENCE')
        ORDER BY i.evidenceDeadline IS NULL, i.evidenceDeadline, i.date
        """
    )
    fun observeOpen(): Flow<List<DayMissionRow>>

    /**
     * Só o que espera foto, de qualquer dia, com o prazo mais apertado na frente.
     *
     * Separado do `observeOpen` porque a tela de Evidências responde uma pergunta mais
     * estreita: ali uma missão sem foto exigida não é pendência nenhuma, é ruído.
     */
    @Query(
        """
        SELECT i.id AS instanceId, i.status AS status, i.date AS date,
               i.evidenceDeadline AS evidenceDeadline, i.chaseCount AS chaseCount,
               i.completedAt AS completedAt, i.wasLate AS wasLate,
               m.id AS missionId, m.title AS missionTitle,
               m.requiresEvidence AS requiresEvidence, m.maxChases AS maxChases,
               f.id AS folderId, f.name AS folderName, f.colorHex AS folderColor
        FROM mission_instances i
        INNER JOIN missions m ON m.id = i.missionId
        INNER JOIN alarms a ON a.id = m.alarmId
        INNER JOIN folders f ON f.id = a.folderId
        WHERE i.status = 'AWAITING_EVIDENCE'
        ORDER BY i.evidenceDeadline IS NULL, i.evidenceDeadline, i.date
        """
    )
    fun observeAwaitingEvidence(): Flow<List<DayMissionRow>>

    /** Evidências já entregues num dia — o outro lado da mesma tela. */
    @Query(
        """
        SELECT i.id AS instanceId, i.status AS status, i.date AS date,
               i.evidenceDeadline AS evidenceDeadline, i.chaseCount AS chaseCount,
               i.completedAt AS completedAt, i.wasLate AS wasLate,
               m.id AS missionId, m.title AS missionTitle,
               m.requiresEvidence AS requiresEvidence, m.maxChases AS maxChases,
               f.id AS folderId, f.name AS folderName, f.colorHex AS folderColor
        FROM mission_instances i
        INNER JOIN missions m ON m.id = i.missionId
        INNER JOIN alarms a ON a.id = m.alarmId
        INNER JOIN folders f ON f.id = a.folderId
        WHERE i.date = :date AND i.status = 'COMPLETED' AND m.requiresEvidence = 1
        ORDER BY i.completedAt DESC
        """
    )
    fun observeEvidenceDone(date: String): Flow<List<DayMissionRow>>

    /**
     * Datas ISO ordenam lexicograficamente — é por isso que `date` é texto e não
     * número, e o que permite agregar um intervalo direto no SQLite.
     */
    @Query(
        """
        SELECT date AS date,
               COUNT(*) AS total,
               SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed
        FROM mission_instances
        WHERE date BETWEEN :from AND :to
        GROUP BY date
        ORDER BY date
        """
    )
    fun observeTallies(from: String, to: String): Flow<List<DayTally>>

    /** Mesma agregação, para a avaliação de sequência da varredura diária. */
    @Query(
        """
        SELECT date AS date,
               COUNT(*) AS total,
               SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed
        FROM mission_instances
        WHERE date BETWEEN :from AND :to
        GROUP BY date
        ORDER BY date
        """
    )
    suspend fun getTallies(from: String, to: String): List<DayTally>

    @Query(
        """
        SELECT p.id AS id, p.filePath AS filePath, p.takenAt AS takenAt,
               i.date AS date, m.title AS missionTitle,
               f.id AS folderId, f.name AS folderName, f.colorHex AS folderColor,
               a.id AS alarmId, a.label AS alarmLabel,
               a.hour AS alarmHour, a.minute AS alarmMinute
        FROM evidence_photos p
        INNER JOIN mission_instances i ON i.id = p.missionInstanceId
        INNER JOIN missions m ON m.id = i.missionId
        INNER JOIN alarms a ON a.id = m.alarmId
        INNER JOIN folders f ON f.id = a.folderId
        ORDER BY p.takenAt DESC
        """
    )
    fun observeGallery(): Flow<List<GalleryPhoto>>
}

@Dao
interface EvidencePhotoDao {

    @Query("SELECT * FROM evidence_photos WHERE missionInstanceId = :instanceId ORDER BY sortOrder, takenAt")
    suspend fun getByInstance(instanceId: Long): List<EvidencePhotoEntity>

    @Query("SELECT * FROM evidence_photos WHERE missionInstanceId = :instanceId ORDER BY sortOrder, takenAt")
    fun observeByInstance(instanceId: Long): Flow<List<EvidencePhotoEntity>>

    @Query("SELECT COUNT(*) FROM evidence_photos WHERE missionInstanceId = :instanceId")
    suspend fun countForInstance(instanceId: Long): Int

    @Query(
        """
        SELECT p.* FROM evidence_photos p
        INNER JOIN mission_instances i ON i.id = p.missionInstanceId
        ORDER BY p.takenAt DESC
        """
    )
    fun observeAllNewestFirst(): Flow<List<EvidencePhotoEntity>>

    /** Todas as fotos, para agrupar por missão em memória sem uma consulta por linha. */
    @Query("SELECT * FROM evidence_photos ORDER BY sortOrder, takenAt")
    fun observeAll(): Flow<List<EvidencePhotoEntity>>

    @Insert
    suspend fun insert(photo: EvidencePhotoEntity): Long

    @Delete
    suspend fun delete(photo: EvidencePhotoEntity)
}

@Dao
interface PointsDao {

    @Insert
    suspend fun insert(entry: PointsLedgerEntity): Long

    @Query("SELECT COALESCE(SUM(xpDelta), 0) FROM points_ledger")
    fun observeTotalXp(): Flow<Int>

    @Query("SELECT COALESCE(SUM(coinDelta), 0) FROM points_ledger")
    fun observeCoinBalance(): Flow<Int>

    @Query("SELECT COALESCE(SUM(coinDelta), 0) FROM points_ledger")
    suspend fun coinBalance(): Int

    @Query("SELECT COALESCE(SUM(xpDelta), 0) FROM points_ledger")
    suspend fun totalXp(): Int

    /** Guarda de idempotência: um mesmo cumprimento não pode pontuar duas vezes. */
    @Query("SELECT COUNT(*) FROM points_ledger WHERE missionInstanceId = :instanceId")
    suspend fun countForInstance(instanceId: Long): Int

    /** A mesma guarda, para um item de checklist marcado. */
    @Query("SELECT COUNT(*) FROM points_ledger WHERE checklistItemStateId = :stateId")
    suspend fun countForChecklistItemState(stateId: Long): Int

    /** A mesma guarda, para o bônus de rodada fechada. */
    @Query("SELECT COUNT(*) FROM points_ledger WHERE checklistCycleId = :cycleId")
    suspend fun countForChecklistCycle(cycleId: Long): Int

    @Query("SELECT COALESCE(SUM(xpDelta), 0) FROM points_ledger WHERE timestamp >= :since")
    fun observeXpSince(since: Long): Flow<Int>

    @Query("SELECT * FROM points_ledger ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PointsLedgerEntity>>
}

@Dao
interface RewardDao {

    @Query("SELECT * FROM rewards WHERE archived = 0 ORDER BY costCoins")
    fun observeActive(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM rewards WHERE id = :id")
    suspend fun getById(id: Long): RewardEntity?

    @Upsert
    suspend fun upsert(reward: RewardEntity): Long

    @Insert
    suspend fun insertRedemption(redemption: RewardRedemptionEntity): Long

    @Query(
        """
        SELECT r.* FROM reward_redemptions rr
        INNER JOIN rewards r ON r.id = rr.rewardId
        ORDER BY rr.redeemedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentRedemptions(limit: Int): Flow<List<RewardEntity>>

    @Delete
    suspend fun delete(reward: RewardEntity)
}

@Dao
interface StreakDao {

    @Query("SELECT * FROM streak_state WHERE id = 1")
    fun observe(): Flow<StreakStateEntity?>

    @Query("SELECT * FROM streak_state WHERE id = 1")
    suspend fun get(): StreakStateEntity?

    @Upsert
    suspend fun upsert(state: StreakStateEntity)
}

@Dao
interface ChecklistDao {

    // ---- cadastro

    @Query("SELECT * FROM checklists ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklists WHERE enabled = 1")
    suspend fun getEnabled(): List<ChecklistEntity>

    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun getById(id: Long): ChecklistEntity?

    @Upsert
    suspend fun upsert(checklist: ChecklistEntity): Long

    @Delete
    suspend fun delete(checklist: ChecklistEntity)

    // ---- itens

    @Query(
        "SELECT * FROM checklist_items WHERE checklistId = :checklistId AND archived = 0 " +
            "ORDER BY sortOrder, id"
    )
    fun observeItems(checklistId: Long): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeAllItems(): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE checklistId = :checklistId AND archived = 0")
    suspend fun getItems(checklistId: Long): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE id = :id")
    suspend fun getItem(id: Long): ChecklistItemEntity?

    /**
     * Os itens que apontam para este despertador.
     *
     * É por aqui que a tela do alarme descobre o que mostrar quando toca, e por aqui
     * que apagar um checklist sabe quais despertadores precisa destravar.
     */
    @Query("SELECT * FROM checklist_items WHERE alarmId = :alarmId AND archived = 0")
    suspend fun getItemsForAlarm(alarmId: Long): List<ChecklistItemEntity>

    @Upsert
    suspend fun upsertItem(item: ChecklistItemEntity): Long

    @Delete
    suspend fun deleteItem(item: ChecklistItemEntity)

    // ---- rodadas

    /**
     * `IGNORE` e não `ABORT`: o índice único em (checklistId, periodStart) é a
     * garantia real de rodada única, e duas aberturas simultâneas — a tela abrindo
     * junto com a varredura das 03h — são uma corrida esperada, não um erro.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCycle(cycle: ChecklistCycleEntity): Long

    @Query("SELECT * FROM checklist_cycles WHERE checklistId = :checklistId AND periodStart = :periodStart")
    suspend fun getCycle(checklistId: Long, periodStart: String): ChecklistCycleEntity?

    @Query("SELECT * FROM checklist_cycles WHERE id = :id")
    suspend fun getCycleById(id: Long): ChecklistCycleEntity?

    @Query("SELECT * FROM checklist_cycles WHERE status = 'OPEN'")
    suspend fun getOpenCycles(): List<ChecklistCycleEntity>

    @Query("SELECT * FROM checklist_cycles WHERE status IN ('OPEN', 'COMPLETED')")
    fun observeLiveCycles(): Flow<List<ChecklistCycleEntity>>

    @Update
    suspend fun updateCycle(cycle: ChecklistCycleEntity)

    // ---- estado dos itens na rodada

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStates(states: List<ChecklistItemStateEntity>): List<Long>

    @Query("SELECT * FROM checklist_item_states WHERE cycleId = :cycleId")
    suspend fun getStates(cycleId: Long): List<ChecklistItemStateEntity>

    @Query("SELECT * FROM checklist_item_states WHERE cycleId = :cycleId AND itemId = :itemId")
    suspend fun getState(cycleId: Long, itemId: Long): ChecklistItemStateEntity?

    @Query(
        "SELECT s.* FROM checklist_item_states s " +
            "INNER JOIN checklist_cycles c ON c.id = s.cycleId " +
            "WHERE c.status IN ('OPEN', 'COMPLETED')"
    )
    fun observeLiveStates(): Flow<List<ChecklistItemStateEntity>>

    @Update
    suspend fun updateState(state: ChecklistItemStateEntity)
}

/**
 * Origem e linhagem dos arquivos da biblioteca.
 *
 * Sem `Flow`: a biblioteca é montada a partir do MediaStore, que não observa, então um
 * fluxo aqui prometeria uma reatividade que a outra metade da tela não tem.
 */
@Dao
interface AudioClipDao {

    @Query("SELECT * FROM audio_clips WHERE mediaStoreUri = :uri")
    suspend fun byUri(uri: String): AudioClipEntity?

    @Query("SELECT * FROM audio_clips WHERE id = :id")
    suspend fun byId(id: Long): AudioClipEntity?

    @Query("SELECT * FROM audio_clips")
    suspend fun all(): List<AudioClipEntity>

    /**
     * `IGNORE`, e não `REPLACE`: o índice único em `mediaStoreUri` já garante um
     * registro por arquivo, e `REPLACE` apagaria e reinseriria a linha com um id novo —
     * o que dispararia o `SET_NULL` dos cortes que apontam para ela e apagaria
     * justamente a linhagem que esta tabela existe para guardar.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(clip: AudioClipEntity): Long

    @Query("DELETE FROM audio_clips WHERE mediaStoreUri = :uri")
    suspend fun deleteByUri(uri: String)

    /**
     * Descarta o registro de arquivos que não estão mais na pasta.
     *
     * O MediaStore é a fonte de verdade sobre o que existe, e ele pode mudar sem
     * passar pelo app — apagar um arquivo pelo gerenciador é o caso normal. Quem
     * chama tem que garantir que [aliveUris] veio de uma listagem que realmente
     * funcionou; passar uma lista vazia porque a consulta falhou limparia a tabela
     * inteira. Ver a guarda em `AudioClipRepository.catalog`.
     */
    @Query("DELETE FROM audio_clips WHERE mediaStoreUri NOT IN (:aliveUris)")
    suspend fun pruneMissing(aliveUris: List<String>)
}
