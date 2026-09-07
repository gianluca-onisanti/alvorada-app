package dev.gianluca.alvoradaapp.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakRulesTest {

    private fun snapshot(current: Int, longest: Int = current, shields: Int = 2) =
        StreakSnapshot(current, longest, shields)

    @Test
    fun `dia sem missao nenhuma e neutro`() {
        // Um domingo sem despertador não pode quebrar sequência — seria punir
        // alguém por ter descansado de propósito.
        assertEquals(DayOutcome.NEUTRAL, StreakRules.outcomeOf(total = 0, completed = 0))

        val before = snapshot(current = 12)
        assertEquals(before, StreakRules.apply(before, DayOutcome.NEUTRAL))
    }

    @Test
    fun `limiar e setenta por cento, nao cem`() {
        assertEquals(DayOutcome.MET, StreakRules.outcomeOf(total = 10, completed = 7))
        assertEquals(DayOutcome.MISSED, StreakRules.outcomeOf(total = 10, completed = 6))
        assertEquals(DayOutcome.MET, StreakRules.outcomeOf(total = 3, completed = 3))
    }

    @Test
    fun `dia cumprido avanca e atualiza o recorde`() {
        val result = StreakRules.apply(snapshot(current = 5, longest = 5), DayOutcome.MET)
        assertEquals(6, result.current)
        assertEquals(6, result.longest)
    }

    @Test
    fun `recorde nao regride quando a sequencia atual e menor`() {
        val result = StreakRules.apply(snapshot(current = 2, longest = 40), DayOutcome.MET)
        assertEquals(3, result.current)
        assertEquals(40, result.longest)
    }

    @Test
    fun `escudo protege a sequencia e se consome`() {
        val result = StreakRules.apply(snapshot(current = 47, shields = 2), DayOutcome.MISSED)
        assertEquals(47, result.current)
        assertEquals(1, result.shields)
    }

    @Test
    fun `sem escudo a sequencia zera mas o recorde permanece`() {
        val result = StreakRules.apply(
            snapshot(current = 47, longest = 47, shields = 0),
            DayOutcome.MISSED,
        )
        assertEquals(0, result.current)
        assertEquals(47, result.longest)
    }

    @Test
    fun `dois dias ruins seguidos gastam os dois escudos antes de zerar`() {
        var state = snapshot(current = 10, shields = 2)
        state = StreakRules.apply(state, DayOutcome.MISSED)
        state = StreakRules.apply(state, DayOutcome.MISSED)
        assertEquals(10, state.current)
        assertEquals(0, state.shields)

        state = StreakRules.apply(state, DayOutcome.MISSED)
        assertEquals(0, state.current)
    }

    @Test
    fun `virada de mes devolve os escudos`() {
        val exhausted = snapshot(current = 3, shields = 0)
        assertEquals(StreakRules.SHIELDS_PER_MONTH, StreakRules.replenish(exhausted).shields)
    }
}
