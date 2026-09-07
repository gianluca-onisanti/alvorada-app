package dev.gianluca.alvoradaapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Agosto de 2026 é o calendário de referência destes testes: 03/08 é uma segunda,
 * as sextas caem em 07, 14, 21 e 28 — o mês **não** tem uma quinta sexta, que é
 * exatamente o caso que separa "última" de "5ª".
 */
class RecurrenceTest {

    private val friday = DayMask.bitOf(DayOfWeek.FRIDAY)

    private fun date(text: String) = LocalDate.parse(text)

    // ------------------------------------------------------------------ intervalo

    @Test
    fun `quinzenal alterna as semanas a partir da ancora`() {
        val every2 = Recurrence(
            kind = RepeatKind.WEEK_INTERVAL,
            daysMask = friday,
            intervalWeeks = 2,
            anchorDate = date("2026-08-03"),
        )
        assertTrue(every2.matches(date("2026-08-07")))
        assertFalse(every2.matches(date("2026-08-14")))
        assertTrue(every2.matches(date("2026-08-21")))
        assertFalse(every2.matches(date("2026-08-28")))
    }

    @Test
    fun `a fase nao depende do dia da semana em que a ancora foi criada`() {
        // Âncora numa quinta e âncora na segunda da mesma semana precisam produzir
        // exatamente o mesmo calendário — a comparação é entre semanas, não entre datas.
        val fromMonday = Recurrence(
            RepeatKind.WEEK_INTERVAL, friday, 2, date("2026-08-03"),
        )
        val fromThursday = fromMonday.copy(anchorDate = date("2026-08-06"))
        listOf("2026-08-07", "2026-08-14", "2026-08-21", "2026-09-04").forEach {
            assertEquals(fromMonday.matches(date(it)), fromThursday.matches(date(it)))
        }
    }

    @Test
    fun `datas antes da ancora tambem respeitam o intervalo`() {
        // `floorMod` e não `%`: com resto negativo a fase inverteria, e um alarme
        // quinzenal criado hoje passaria a valer nas semanas erradas do passado.
        val every2 = Recurrence(
            RepeatKind.WEEK_INTERVAL, friday, 2, date("2026-08-03"),
        )
        assertTrue(every2.matches(date("2026-07-24")))
        assertFalse(every2.matches(date("2026-07-31")))
    }

    // ------------------------------------------------------------- semana do mês

    @Test
    fun `ultima sexta do mes`() {
        val last = Recurrence(
            kind = RepeatKind.MONTHLY_ORDINAL,
            daysMask = friday,
            ordinalMask = OrdinalMask.LAST,
        )
        assertTrue(last.matches(date("2026-08-28")))
        assertFalse(last.matches(date("2026-08-21")))
        assertTrue(last.matches(date("2026-09-25")))
    }

    @Test
    fun `ultima nao e o mesmo que quinta`() {
        // Agosto/2026 tem quatro sextas. "Última" acontece; "5ª" não.
        val fifth = Recurrence(
            RepeatKind.MONTHLY_ORDINAL, friday, ordinalMask = OrdinalMask.bitOf(4),
        )
        assertFalse((1..31).any { fifth.matches(LocalDate.of(2026, 8, it)) })

        val last = fifth.copy(ordinalMask = OrdinalMask.LAST)
        assertTrue((1..31).any { last.matches(LocalDate.of(2026, 8, it)) })
    }

    @Test
    fun `primeira e terceira sexta`() {
        val mask = OrdinalMask.bitOf(0) or OrdinalMask.bitOf(2)
        val r = Recurrence(RepeatKind.MONTHLY_ORDINAL, friday, ordinalMask = mask)
        assertTrue(r.matches(date("2026-08-07")))
        assertFalse(r.matches(date("2026-08-14")))
        assertTrue(r.matches(date("2026-08-21")))
    }

    @Test
    fun `o dia da semana continua valendo no modo ordinal`() {
        val r = Recurrence(RepeatKind.MONTHLY_ORDINAL, friday, ordinalMask = OrdinalMask.LAST)
        // 31/08/2026 é uma segunda e é o último dia do mês — mas não é sexta.
        assertFalse(r.matches(date("2026-08-31")))
    }

    // --------------------------------------------------------------- dias do mês

    @Test
    fun `dias fixos do calendario`() {
        val r = Recurrence(
            kind = RepeatKind.MONTHLY_DAYS,
            monthDaysMask = MonthDayMask.bitOf(15) or MonthDayMask.bitOf(30),
        )
        assertTrue(r.matches(date("2026-08-15")))
        assertTrue(r.matches(date("2026-08-30")))
        assertFalse(r.matches(date("2026-08-16")))
    }

    @Test
    fun `dia 31 simplesmente nao acontece em fevereiro`() {
        val r = Recurrence(
            RepeatKind.MONTHLY_DAYS,
            monthDaysMask = MonthDayMask.bitOf(31),
        )
        assertTrue(r.matches(date("2026-01-31")))
        assertFalse((1..28).any { r.matches(LocalDate.of(2026, 2, it)) })
    }

    @Test
    fun `ultimo dia do mes acompanha o tamanho do mes`() {
        val r = Recurrence(RepeatKind.MONTHLY_DAYS, monthDaysMask = MonthDayMask.LAST_DAY)
        assertTrue(r.matches(date("2026-02-28")))
        assertTrue(r.matches(date("2026-04-30")))
        assertTrue(r.matches(date("2026-08-31")))
        assertFalse(r.matches(date("2026-08-30")))
    }

    // ------------------------------------------------------------------ contratos

    @Test
    fun `nao dispara sem as pecas que o modo exige`() {
        assertFalse(Recurrence(RepeatKind.WEEKLY, DayMask.NONE).canFire)
        assertFalse(Recurrence(RepeatKind.MONTHLY_ORDINAL, friday).canFire)
        assertFalse(Recurrence(RepeatKind.MONTHLY_ORDINAL, DayMask.NONE, ordinalMask = OrdinalMask.LAST).canFire)
        assertFalse(Recurrence(RepeatKind.MONTHLY_DAYS).canFire)
        assertTrue(Recurrence(RepeatKind.MONTHLY_DAYS, monthDaysMask = MonthDayMask.LAST_DAY).canFire)
    }

    @Test
    fun `missoes por dia do calendario valem em qualquer dia da semana`() {
        // O dia da semana do dia 15 muda todo mês. Restringir a missão por ele faria
        // com que ela deixasse de existir na maioria dos meses.
        val monthly = Recurrence(
            RepeatKind.MONTHLY_DAYS,
            daysMask = friday,
            monthDaysMask = MonthDayMask.bitOf(15),
        )
        assertEquals(DayMask.EVERY_DAY, monthly.missionDayMask)
        assertEquals(friday, Recurrence(RepeatKind.WEEKLY, friday).missionDayMask)
    }

    @Test
    fun `rotulos legiveis`() {
        assertEquals("Todo dia", Recurrence.weekly(DayMask.EVERY_DAY).describe())
        assertEquals(
            "Sex · a cada 2 semanas",
            Recurrence(RepeatKind.WEEK_INTERVAL, friday, 2).describe(),
        )
        assertEquals(
            "Sex · última do mês",
            Recurrence(RepeatKind.MONTHLY_ORDINAL, friday, ordinalMask = OrdinalMask.LAST).describe(),
        )
        assertEquals(
            "Sex · 1ª e última do mês",
            Recurrence(
                RepeatKind.MONTHLY_ORDINAL,
                friday,
                ordinalMask = OrdinalMask.bitOf(0) or OrdinalMask.LAST,
            ).describe(),
        )
        assertEquals(
            "Dias 15, 30 e último",
            Recurrence(
                RepeatKind.MONTHLY_DAYS,
                monthDaysMask = MonthDayMask.bitOf(15) or
                    MonthDayMask.bitOf(30) or
                    MonthDayMask.LAST_DAY,
            ).describe(),
        )
        assertEquals(
            "Dia 15",
            Recurrence(RepeatKind.MONTHLY_DAYS, monthDaysMask = MonthDayMask.bitOf(15)).describe(),
        )
    }

    // -------------------------------------------------------------------- data única

    @Test
    fun `data unica so casa com o proprio dia`() {
        val once = Recurrence.once(date("2026-08-18"))
        assertTrue(once.matches(date("2026-08-18")))
        assertFalse(once.matches(date("2026-08-17")))
        assertFalse(once.matches(date("2026-08-19")))
        // Nem no mesmo dia da semana do mês seguinte: "uma vez" é uma vez.
        assertFalse(once.matches(date("2026-09-18")))
    }

    @Test
    fun `data unica sem data nunca dispara`() {
        assertFalse(Recurrence(RepeatKind.ONCE).canFire)
        assertTrue(Recurrence.once(date("2026-08-18")).canFire)
    }

    @Test
    fun `despertador de uma vez so nao aceita missoes`() {
        // Nenhum dia disponível é a forma de o schema dizer "aqui não cabe missão":
        // ela morreria junto com o despertador no primeiro toque.
        assertEquals(DayMask.NONE, Recurrence.once(date("2026-08-18")).missionDayMask)
    }
}
