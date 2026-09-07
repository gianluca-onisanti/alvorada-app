package dev.gianluca.alvoradaapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A matemática de rodada é onde moram os erros que só aparecem meses depois: num mês
 * em que o dia 31 não existe, numa quinta semana que não acontece, na virada do ano.
 */
class ChecklistCycleTest {

    private val sp: ZoneId = ZoneId.of("America/Sao_Paulo")

    private fun at(text: String): ZonedDateTime = LocalDateTime.parse(text).atZone(sp)

    private fun millis(text: String): Long =
        LocalDateTime.parse(text).atZone(sp).toInstant().toEpochMilli()

    // ---- diário

    @Test
    fun `checklist diario comeca hoje e termina na virada`() {
        // Quarta, 10h. Diário vencendo às 22h.
        val cycle = ChecklistCycle.currentAt(
            Recurrence.weekly(DayMask.EVERY_DAY), 22, 0, at("2026-08-05T10:00"),
        )
        assertNotNull(cycle)
        assertEquals(LocalDate.parse("2026-08-05"), cycle!!.periodStart)
        assertEquals(millis("2026-08-05T22:00"), cycle.dueAt)
        assertEquals(millis("2026-08-06T00:00"), cycle.endsAt)
    }

    @Test
    fun `a rodada de hoje continua valendo depois do vencimento`() {
        // 23h: já venceu, mas ainda é a rodada de hoje. Calar o despertador só até
        // as 22h faria ele tocar de novo à noite por algo já feito.
        val cycle = ChecklistCycle.currentAt(
            Recurrence.weekly(DayMask.EVERY_DAY), 22, 0, at("2026-08-05T23:00"),
        )!!
        assertEquals(LocalDate.parse("2026-08-05"), cycle.periodStart)
        assertTrue(cycle.contains(millis("2026-08-05T23:30")))
        assertTrue(!cycle.contains(millis("2026-08-06T00:30")))
    }

    // ---- semanal

    @Test
    fun `checklist semanal de segunda vale a semana toda`() {
        // Sexta. A rodada corrente é a que começou na segunda, não a próxima.
        val segunda = Recurrence.weekly(DayMask.bitOf(java.time.DayOfWeek.MONDAY))
        val cycle = ChecklistCycle.currentAt(segunda, 20, 0, at("2026-08-07T15:00"))!!
        assertEquals(LocalDate.parse("2026-08-03"), cycle.periodStart)
        assertEquals(millis("2026-08-10T00:00"), cycle.endsAt)
    }

    // ---- quinzenal

    @Test
    fun `checklist quinzenal pula a semana intermediaria`() {
        val quinzenal = Recurrence(
            kind = RepeatKind.WEEK_INTERVAL,
            daysMask = DayMask.bitOf(java.time.DayOfWeek.MONDAY),
            intervalWeeks = 2,
            anchorDate = LocalDate.parse("2026-08-03"),
        )
        val cycle = ChecklistCycle.currentAt(quinzenal, 20, 0, at("2026-08-05T10:00"))!!
        assertEquals(LocalDate.parse("2026-08-03"), cycle.periodStart)
        // Duas semanas de silêncio, não uma.
        assertEquals(millis("2026-08-17T00:00"), cycle.endsAt)
    }

    // ---- mensal por dia do calendário

    @Test
    fun `checklist mensal do dia 1 vale ate o dia 1 seguinte`() {
        val mensal = Recurrence(
            kind = RepeatKind.MONTHLY_DAYS,
            monthDaysMask = MonthDayMask.bitOf(1),
        )
        val cycle = ChecklistCycle.currentAt(mensal, 12, 0, at("2026-08-20T09:00"))!!
        assertEquals(LocalDate.parse("2026-08-01"), cycle.periodStart)
        assertEquals(millis("2026-09-01T00:00"), cycle.endsAt)
    }

    @Test
    fun `dia 31 atravessa os meses que nao tem dia 31`() {
        // Este é o caso que uma varredura curta erraria: de 31/07 a rodada só vira
        // em 31/08, porque agosto é o próximo mês que tem dia 31 — setembro não tem.
        val mensal = Recurrence(
            kind = RepeatKind.MONTHLY_DAYS,
            monthDaysMask = MonthDayMask.bitOf(31),
        )
        val cycle = ChecklistCycle.currentAt(mensal, 12, 0, at("2026-08-15T09:00"))!!
        assertEquals(LocalDate.parse("2026-07-31"), cycle.periodStart)
        assertEquals(millis("2026-08-31T00:00"), cycle.endsAt)
    }

    @Test
    fun `ultimo dia do mes acompanha o tamanho do mes`() {
        val mensal = Recurrence(
            kind = RepeatKind.MONTHLY_DAYS,
            monthDaysMask = MonthDayMask.LAST_DAY,
        )
        val cycle = ChecklistCycle.currentAt(mensal, 12, 0, at("2026-02-10T09:00"))!!
        assertEquals(LocalDate.parse("2026-01-31"), cycle.periodStart)
        // 2026 não é bissexto: fevereiro fecha no dia 28.
        assertEquals(millis("2026-02-28T00:00"), cycle.endsAt)
    }

    // ---- mensal por ordinal

    @Test
    fun `primeira segunda do mes`() {
        val ordinal = Recurrence(
            kind = RepeatKind.MONTHLY_ORDINAL,
            daysMask = DayMask.bitOf(java.time.DayOfWeek.MONDAY),
            ordinalMask = OrdinalMask.bitOf(0),
        )
        val cycle = ChecklistCycle.currentAt(ordinal, 20, 0, at("2026-08-20T10:00"))!!
        assertEquals(LocalDate.parse("2026-08-03"), cycle.periodStart)
        assertEquals(millis("2026-09-07T00:00"), cycle.endsAt)
    }

    // ---- casos degenerados

    @Test
    fun `recorrencia que nunca dispara nao tem rodada`() {
        val vazia = Recurrence.weekly(DayMask.NONE)
        assertNull(ChecklistCycle.currentAt(vazia, 22, 0, at("2026-08-05T10:00")))
    }

    @Test
    fun `data unica nunca vira de rodada`() {
        // Sem próxima ocorrência não existe rodada seguinte para a qual virar, então
        // a rodada não fecha — e o despertador dela fica calado para sempre, que é o
        // certo: não há uma segunda vez para ele cobrar.
        val unica = Recurrence.once(LocalDate.parse("2026-08-05"))
        val cycle = ChecklistCycle.currentAt(unica, 22, 0, at("2026-08-05T10:00"))!!
        assertEquals(LocalDate.parse("2026-08-05"), cycle.periodStart)
        assertEquals(Long.MAX_VALUE, cycle.endsAt)
    }

    @Test
    fun `next entrega a rodada seguinte a partir de uma rodada`() {
        val diario = Recurrence.weekly(DayMask.EVERY_DAY)
        val hoje = ChecklistCycle.currentAt(diario, 22, 0, at("2026-08-05T10:00"))!!
        val amanha = ChecklistCycle.next(diario, 22, 0, hoje, sp)!!
        assertEquals(LocalDate.parse("2026-08-06"), amanha.periodStart)
        assertEquals(millis("2026-08-06T22:00"), amanha.dueAt)
        assertEquals(hoje.endsAt, millis("2026-08-06T00:00"))
    }
}
