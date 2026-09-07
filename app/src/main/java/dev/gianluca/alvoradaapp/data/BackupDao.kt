package dev.gianluca.alvoradaapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Despejo e restauração completos.
 *
 * As inserções mantêm os ids originais e por isso precisam respeitar a ordem das
 * chaves estrangeiras: pasta antes de despertador, despertador antes de missão, e
 * assim por diante. Inverter a ordem quebra a restauração com erro de constraint —
 * daí [restoreAll] ser uma transação única, para não deixar o banco pela metade.
 */
@Dao
interface BackupDao {

    @Query("SELECT * FROM folders") suspend fun folders(): List<FolderEntity>
    @Query("SELECT * FROM alarms") suspend fun alarms(): List<AlarmEntity>
    @Query("SELECT * FROM missions") suspend fun missions(): List<MissionEntity>
    @Query("SELECT * FROM alarm_occurrences") suspend fun occurrences(): List<OccurrenceEntity>
    @Query("SELECT * FROM mission_instances") suspend fun instances(): List<MissionInstanceEntity>
    @Query("SELECT * FROM evidence_photos") suspend fun photos(): List<EvidencePhotoEntity>
    @Query("SELECT * FROM points_ledger") suspend fun points(): List<PointsLedgerEntity>
    @Query("SELECT * FROM rewards") suspend fun rewards(): List<RewardEntity>
    @Query("SELECT * FROM reward_redemptions") suspend fun redemptions(): List<RewardRedemptionEntity>
    @Query("SELECT * FROM streak_state WHERE id = 1") suspend fun streak(): StreakStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFolders(items: List<FolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAlarms(items: List<AlarmEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMissions(items: List<MissionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOccurrences(items: List<OccurrenceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putInstances(items: List<MissionInstanceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPhotos(items: List<EvidencePhotoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPoints(items: List<PointsLedgerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRewards(items: List<RewardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRedemptions(items: List<RewardRedemptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putStreak(item: StreakStateEntity)

    @Query("DELETE FROM evidence_photos") suspend fun wipePhotos()
    @Query("DELETE FROM mission_instances") suspend fun wipeInstances()
    @Query("DELETE FROM alarm_occurrences") suspend fun wipeOccurrences()
    @Query("DELETE FROM missions") suspend fun wipeMissions()
    @Query("DELETE FROM alarms") suspend fun wipeAlarms()
    @Query("DELETE FROM folders") suspend fun wipeFolders()
    @Query("DELETE FROM points_ledger") suspend fun wipePoints()
    @Query("DELETE FROM reward_redemptions") suspend fun wipeRedemptions()
    @Query("DELETE FROM rewards") suspend fun wipeRewards()
    @Query("DELETE FROM streak_state") suspend fun wipeStreak()

    /**
     * Substitui todo o conteúdo. Transação única: ou o backup entra inteiro, ou o
     * banco fica como estava. Meio-restauro seria pior do que restauro nenhum.
     */
    @Transaction
    suspend fun restoreAll(
        folders: List<FolderEntity>,
        alarms: List<AlarmEntity>,
        missions: List<MissionEntity>,
        occurrences: List<OccurrenceEntity>,
        instances: List<MissionInstanceEntity>,
        photos: List<EvidencePhotoEntity>,
        points: List<PointsLedgerEntity>,
        rewards: List<RewardEntity>,
        redemptions: List<RewardRedemptionEntity>,
        streak: StreakStateEntity?,
    ) {
        // Apagar na ordem inversa das dependências.
        wipePhotos(); wipeInstances(); wipeOccurrences(); wipeMissions(); wipeAlarms()
        wipePoints(); wipeRedemptions(); wipeRewards(); wipeFolders(); wipeStreak()

        putFolders(folders)
        putAlarms(alarms)
        putMissions(missions)
        putOccurrences(occurrences)
        putInstances(instances)
        putPhotos(photos)
        putRewards(rewards)
        putRedemptions(redemptions)
        putPoints(points)
        streak?.let { putStreak(it) }
    }
}
