package dev.gianluca.alvoradaapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class DayMaskTest {

    @Test
    fun `bit 0 e segunda e bit 6 e domingo`() {
        assertEquals(0b0000001, DayMask.bitOf(DayOfWeek.MONDAY))
        assertEquals(0b1000000, DayMask.bitOf(DayOfWeek.SUNDAY))
    }

    @Test
    fun `constantes cobrem os dias esperados`() {
        assertEquals(
            listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
            ),
            DayMask.daysIn(DayMask.WEEKDAYS),
        )
        assertEquals(
            listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            DayMask.daysIn(DayMask.WEEKEND),
        )
        assertEquals(7, DayMask.daysIn(DayMask.EVERY_DAY).size)
    }

    @Test
    fun `toggle liga e desliga`() {
        val mask = DayMask.toggle(DayMask.NONE, DayOfWeek.FRIDAY)
        assertTrue(DayMask.contains(mask, DayOfWeek.FRIDAY))
        assertFalse(DayMask.contains(DayMask.toggle(mask, DayOfWeek.FRIDAY), DayOfWeek.FRIDAY))
    }

    @Test
    fun `missao so pode existir em dias que o alarme toca`() {
        // A regra de integridade do schema: daysMask da missão ⊆ daysMask do alarme.
        val alarmDays = DayMask.WEEKDAYS
        val gym = DayMask.bitOf(DayOfWeek.MONDAY) or DayMask.bitOf(DayOfWeek.WEDNESDAY)
        val weekendChore = DayMask.bitOf(DayOfWeek.SUNDAY)

        assertTrue(DayMask.isSubsetOf(gym, alarmDays))
        assertFalse(DayMask.isSubsetOf(weekendChore, alarmDays))
    }

    @Test
    fun `poda remove os dias que o alarme deixou de cobrir`() {
        // Alarme encolhe de todo dia para dias úteis: a missão de domingo some.
        val mission = DayMask.bitOf(DayOfWeek.FRIDAY) or DayMask.bitOf(DayOfWeek.SUNDAY)
        val pruned = DayMask.intersect(mission, DayMask.WEEKDAYS)

        assertEquals(listOf(DayOfWeek.FRIDAY), DayMask.daysIn(pruned))
    }

    @Test
    fun `descricao usa atalhos para os casos comuns`() {
        assertEquals("Todo dia", DayMask.describe(DayMask.EVERY_DAY))
        assertEquals("Seg–Sex", DayMask.describe(DayMask.WEEKDAYS))
        assertEquals("Nenhum dia", DayMask.describe(DayMask.NONE))
        assertEquals(
            "Seg, Qua",
            DayMask.describe(DayMask.bitOf(DayOfWeek.MONDAY) or DayMask.bitOf(DayOfWeek.WEDNESDAY)),
        )
    }
}
