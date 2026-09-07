package dev.gianluca.alvoradaapp.core

/** Como um dia contou para a sequência. */
enum class DayOutcome {
    /** Bateu o limiar — a sequência avança. */
    MET,

    /** Ficou abaixo do limiar — consome escudo ou zera. */
    MISSED,

    /** Nenhuma missão prevista. Não avança nem quebra. */
    NEUTRAL,
}

data class StreakSnapshot(
    val current: Int,
    val longest: Int,
    val shields: Int,
)

/**
 * Regras da sequência de dias.
 *
 * Três decisões deliberadas, todas na mesma direção — impedir que o app vire fonte
 * de culpa:
 *
 * 1. **Limiar de 70%, não 100%.** Exigir o dia perfeito faz a primeira missão perdida
 *    esvaziar o sentido de tentar as outras.
 * 2. **Dia sem missão é neutro.** Um domingo sem despertador não pode quebrar nada;
 *    seria punir você por ter descansado de propósito.
 * 3. **Escudos.** Dois por mês, consumidos automaticamente. Uma sequência de 47 dias
 *    que zera por um dia ruim é a principal causa de abandono deste tipo de app —
 *    o escudo transforma o tropeço em custo, não em colapso.
 */
object StreakRules {

    const val THRESHOLD = 0.7f
    const val SHIELDS_PER_MONTH = 2

    fun outcomeOf(total: Int, completed: Int): DayOutcome = when {
        total == 0 -> DayOutcome.NEUTRAL
        completed.toFloat() / total >= THRESHOLD -> DayOutcome.MET
        else -> DayOutcome.MISSED
    }

    fun apply(state: StreakSnapshot, outcome: DayOutcome): StreakSnapshot = when (outcome) {
        DayOutcome.NEUTRAL -> state

        DayOutcome.MET -> {
            val next = state.current + 1
            state.copy(current = next, longest = maxOf(state.longest, next))
        }

        DayOutcome.MISSED ->
            if (state.shields > 0) state.copy(shields = state.shields - 1)
            else state.copy(current = 0)
    }

    /** Escudos voltam ao cheio na virada do mês. */
    fun replenish(state: StreakSnapshot): StreakSnapshot =
        state.copy(shields = SHIELDS_PER_MONTH)
}
