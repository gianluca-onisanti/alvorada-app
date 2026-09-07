package dev.gianluca.alvoradaapp.data

import android.util.Log
import dev.gianluca.alvoradaapp.core.DayOutcome
import dev.gianluca.alvoradaapp.core.LevelProgress
import dev.gianluca.alvoradaapp.core.Leveling
import dev.gianluca.alvoradaapp.core.StreakRules
import dev.gianluca.alvoradaapp.core.StreakSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/** Tudo que o painel precisa mostrar sobre progresso, numa leitura só. */
data class ProgressSummary(
    val totalXp: Int,
    val level: LevelProgress,
    val coins: Int,
    val streak: StreakSnapshot,
)

/** Resultado de uma tentativa de resgate. */
sealed interface RedeemResult {
    data class Ok(val remaining: Int) : RedeemResult
    data class NotEnough(val missing: Int) : RedeemResult
    data object Unknown : RedeemResult
}

/**
 * Pontos, níveis, sequência e loja.
 *
 * O extrato (`points_ledger`) é append-only e **é** a fonte de verdade: saldo e XP são
 * sempre somas dele, nunca campos guardados. Isso custa uma agregação por leitura e
 * elimina a classe inteira de bugs em que o saldo e o histórico discordam — e num app
 * de recompensa pessoal, um saldo que não bate com o histórico destrói a confiança
 * no sistema inteiro.
 */
class PointsRepository(private val db: AlvoradaDatabase) {

    // ------------------------------------------------------------------ leitura

    fun observeProgress(): Flow<ProgressSummary> =
        kotlinx.coroutines.flow.combine(
            db.pointsDao().observeTotalXp(),
            db.pointsDao().observeCoinBalance(),
            db.streakDao().observe(),
        ) { xp, coins, streak ->
            ProgressSummary(
                totalXp = xp,
                level = Leveling.progressOf(xp),
                coins = coins.coerceAtLeast(0),
                streak = streak?.toSnapshot() ?: StreakSnapshot(0, 0, StreakRules.SHIELDS_PER_MONTH),
            )
        }

    fun observeXpToday(): Flow<Int> =
        db.pointsDao().observeXpSince(startOfToday()).map { it.coerceAtLeast(0) }

    fun observeRewards(): Flow<List<RewardEntity>> = db.rewardDao().observeActive()

    fun observeRecentRedemptions(limit: Int = 5): Flow<List<RewardEntity>> =
        db.rewardDao().observeRecentRedemptions(limit)

    // ------------------------------------------------------------------ ganhos

    /**
     * Credita o cumprimento de uma missão.
     *
     * Idempotente por instância: `complete()` chamado duas vezes — por toque duplo ou
     * por uma corrida entre a tela do alarme e o painel — não pode pontuar em dobro.
     */
    suspend fun awardMission(
        instanceId: Long,
        xp: Int,
        coins: Int,
        late: Boolean,
    ) {
        if (db.pointsDao().countForInstance(instanceId) > 0) {
            Log.i(TAG, "Instância $instanceId já pontuada — ignorando")
            return
        }
        db.pointsDao().insert(
            PointsLedgerEntity(
                timestamp = System.currentTimeMillis(),
                xpDelta = xp,
                coinDelta = coins,
                reason = if (late) PointsReason.MISSION_LATE else PointsReason.MISSION_ON_TIME,
                missionInstanceId = instanceId,
            )
        )
    }

    suspend fun awardPerfectDay(xp: Int = PERFECT_DAY_XP, coins: Int = PERFECT_DAY_COINS) {
        db.pointsDao().insert(
            PointsLedgerEntity(
                timestamp = System.currentTimeMillis(),
                xpDelta = xp,
                coinDelta = coins,
                reason = PointsReason.PERFECT_DAY,
            )
        )
    }

    /**
     * Soneca custa moedas, nunca XP — e nunca leva o saldo abaixo de zero.
     *
     * Saldo negativo transformaria a loja num buraco: você acumularia dívida sem
     * perceber e a próxima recompensa ficaria inalcançável por motivo invisível.
     */
    suspend fun penalizeSnooze(cost: Int = SNOOZE_COST) {
        val balance = db.pointsDao().coinBalance()
        val debit = minOf(cost, balance.coerceAtLeast(0))
        if (debit == 0) return
        db.pointsDao().insert(
            PointsLedgerEntity(
                timestamp = System.currentTimeMillis(),
                xpDelta = 0,
                coinDelta = -debit,
                reason = PointsReason.SNOOZE_PENALTY,
            )
        )
    }

    // ------------------------------------------------------------------ loja

    suspend fun saveReward(reward: RewardEntity): Long = db.rewardDao().upsert(reward)

    suspend fun deleteReward(reward: RewardEntity) = db.rewardDao().delete(reward)

    suspend fun redeem(rewardId: Long): RedeemResult {
        val reward = db.rewardDao().getById(rewardId) ?: return RedeemResult.Unknown
        val balance = db.pointsDao().coinBalance()
        if (balance < reward.costCoins) {
            return RedeemResult.NotEnough(reward.costCoins - balance)
        }

        val now = System.currentTimeMillis()
        db.rewardDao().insertRedemption(
            RewardRedemptionEntity(rewardId = reward.id, redeemedAt = now, costPaid = reward.costCoins)
        )
        db.pointsDao().insert(
            PointsLedgerEntity(
                timestamp = now,
                xpDelta = 0,
                coinDelta = -reward.costCoins,
                reason = PointsReason.REWARD_PURCHASE,
                rewardId = reward.id,
            )
        )
        return RedeemResult.Ok(balance - reward.costCoins)
    }

    // ------------------------------------------------------------------ sequência

    /**
     * Avalia todos os dias ainda não avaliados, até ontem.
     *
     * Percorre dia a dia em vez de olhar só o último: se o celular ficou desligado
     * três dias, os três precisam entrar na conta — inclusive gastando escudos na
     * ordem certa. Hoje nunca é avaliado, porque ainda dá tempo de cumprir.
     */
    suspend fun evaluateStreak(today: LocalDate = LocalDate.now()) {
        val state = db.streakDao().get() ?: StreakStateEntity()
        val lastEvaluated = state.lastEvaluatedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val from = lastEvaluated?.plusDays(1) ?: today.minusDays(1)
        val to = today.minusDays(1)
        if (from.isAfter(to)) return

        val tallies = db.panelDao().getTallies(from.toString(), to.toString()).associateBy { it.date }

        var snapshot = state.toSnapshot()
        var cursor = from
        var previous = lastEvaluated

        while (!cursor.isAfter(to)) {
            if (previous != null && cursor.month != previous.month) {
                snapshot = StreakRules.replenish(snapshot)
            }
            val tally = tallies[cursor.toString()]
            val outcome = if (tally == null) {
                DayOutcome.NEUTRAL
            } else {
                StreakRules.outcomeOf(tally.total, tally.completed)
            }
            snapshot = StreakRules.apply(snapshot, outcome)

            // Bônus de dia perfeito sai daqui, e não do momento de concluir a última
            // missão: cada dia é avaliado exatamente uma vez, o que dispensa qualquer
            // guarda extra de idempotência.
            if (tally != null && tally.total > 0 && tally.completed == tally.total) {
                awardPerfectDay()
            }
            previous = cursor
            cursor = cursor.plusDays(1)
        }

        db.streakDao().upsert(
            StreakStateEntity(
                id = 1,
                currentStreak = snapshot.current,
                longestStreak = snapshot.longest,
                shieldsAvailable = snapshot.shields,
                lastEvaluatedDate = to.toString(),
            )
        )
        Log.i(TAG, "Sequência avaliada até $to: ${snapshot.current} dia(s), ${snapshot.shields} escudo(s)")
    }

    private fun StreakStateEntity.toSnapshot() =
        StreakSnapshot(currentStreak, longestStreak, shieldsAvailable)

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        private const val TAG = "PointsRepository"
        const val SNOOZE_COST = 5
        const val PERFECT_DAY_XP = 25
        const val PERFECT_DAY_COINS = 25

        /** Fração das moedas paga quando a missão fecha fora do prazo. */
        const val LATE_COIN_RATIO = 0.6f
    }
}
