package dev.gianluca.alvoradaapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * O cálculo de próximo disparo é a única peça do agendamento testável sem aparelho —
 * e é onde moram os erros que só aparecem meses depois.
 */
class NextFireCalculatorTest {

    private val sp: ZoneId = ZoneId.of("America/Sao_Paulo")

    private fun at(text: String, zone: ZoneId = sp): ZonedDateTime =
        LocalDateTime.parse(text).atZone(zone)

    @Test
    fun `alarme diario com horario ainda por vir dispara hoje`() {
        // Quarta, 10h. Alarme diário às 22h.
        val from = at("2026-08-05T10:00")
        val next = NextFireCalculator.nextFire(22, 0, DayMask.EVERY_DAY, from)
        assertEquals(at("2026-08-05T22:00"), next)
    }

    @Test
    fun `alarme diario com horario ja passado pula para amanha`() {
        val from = at("2026-08-05T23:00")
        val next = NextFireCalculator.nextFire(22, 0, DayMask.EVERY_DAY, from)
        assertEquals(at("2026-08-06T22:00"), next)
    }

    @Test
    fun `horario exato conta como passado`() {
        // `isAfter` é estrito de propósito: reagendar no exato instante do disparo
        // não pode devolver o mesmo horário e criar um laço infinito.
        val from = at("2026-08-05T22:00")
        val next = NextFireCalculator.nextFire(22, 0, DayMask.EVERY_DAY, from)
        assertEquals(at("2026-08-06T22:00"), next)
    }

    @Test
    fun `alarme semanal cujo horario ja passou volta so na semana seguinte`() {
        // Quarta 2026-08-05, 10h. Alarme só às quartas, 07h.
        val onlyWednesday = DayMask.bitOf(java.time.DayOfWeek.WEDNESDAY)
        val from = at("2026-08-05T10:00")
        val next = NextFireCalculator.nextFire(7, 0, onlyWednesday, from)
        assertEquals(at("2026-08-12T07:00"), next)
    }

    @Test
    fun `alarme de dias uteis na sexta a noite pula o fim de semana`() {
        // Sexta 2026-08-07, 20h.
        val from = at("2026-08-07T20:00")
        val next = NextFireCalculator.nextFire(7, 0, DayMask.WEEKDAYS, from)
        assertEquals(at("2026-08-10T07:00"), next)
    }

    @Test
    fun `virada de mes e de ano`() {
        val from = at("2026-12-31T23:30")
        val next = NextFireCalculator.nextFire(7, 0, DayMask.EVERY_DAY, from)
        assertEquals(at("2027-01-01T07:00"), next)
    }

    @Test
    fun `mascara vazia nao agenda`() {
        val from = at("2026-08-05T10:00")
        assertNull(NextFireCalculator.nextFire(7, 0, DayMask.NONE, from))
    }

    @Test
    fun `horario inexistente no adiantamento do horario de verao e empurrado para frente`() {
        // Nova York adianta o relógio em 2026-03-08 às 02h: 02h30 não existe nesse dia.
        // Para um despertador, empurrar para 03h30 é melhor do que não tocar.
        val ny = ZoneId.of("America/New_York")
        val from = at("2026-03-07T12:00", ny)
        val next = NextFireCalculator.nextFire(2, 30, DayMask.EVERY_DAY, from)
        assertEquals(at("2026-03-08T03:30", ny), next)
    }

    // ------------------------------------------------- repetições de calendário

    @Test
    fun `quinzenal pula a semana intermediaria`() {
        val every2 = Recurrence(
            kind = RepeatKind.WEEK_INTERVAL,
            daysMask = DayMask.bitOf(java.time.DayOfWeek.FRIDAY),
            intervalWeeks = 2,
            anchorDate = java.time.LocalDate.parse("2026-08-03"),
        )
        val from = at("2026-08-07T10:00")
        assertEquals(at("2026-08-21T07:00"), NextFireCalculator.nextFire(7, 0, every2, from))
    }

    @Test
    fun `ultima sexta do mes atravessa a virada`() {
        val last = Recurrence(
            kind = RepeatKind.MONTHLY_ORDINAL,
            daysMask = DayMask.bitOf(java.time.DayOfWeek.FRIDAY),
            ordinalMask = OrdinalMask.LAST,
        )
        assertEquals(
            at("2026-08-28T07:00"),
            NextFireCalculator.nextFire(7, 0, last, at("2026-08-05T10:00")),
        )
        // Passada a última sexta de agosto, o próximo é a de setembro.
        assertEquals(
            at("2026-09-25T07:00"),
            NextFireCalculator.nextFire(7, 0, last, at("2026-08-28T09:00")),
        )
    }

    @Test
    fun `quinta sexta do mes pode faltar por meses`() {
        // Agosto e setembro de 2026 não têm uma quinta sexta; outubro tem, dia 30.
        // É exatamente por causa deste caso que a varredura vai muito além de 7 dias.
        val fifth = Recurrence(
            kind = RepeatKind.MONTHLY_ORDINAL,
            daysMask = DayMask.bitOf(java.time.DayOfWeek.FRIDAY),
            ordinalMask = OrdinalMask.bitOf(4),
        )
        assertEquals(
            at("2026-10-30T07:00"),
            NextFireCalculator.nextFire(7, 0, fifth, at("2026-08-05T10:00")),
        )
    }

    @Test
    fun `dias fixos do mes`() {
        val r = Recurrence(
            kind = RepeatKind.MONTHLY_DAYS,
            monthDaysMask = MonthDayMask.bitOf(15) or MonthDayMask.bitOf(30),
        )
        assertEquals(
            at("2026-08-15T07:00"),
            NextFireCalculator.nextFire(7, 0, r, at("2026-08-05T10:00")),
        )
        assertEquals(
            at("2026-08-30T07:00"),
            NextFireCalculator.nextFire(7, 0, r, at("2026-08-15T10:00")),
        )
    }

    @Test
    fun `dia 31 pula fevereiro inteiro`() {
        val r = Recurrence(
            kind = RepeatKind.MONTHLY_DAYS,
            monthDaysMask = MonthDayMask.bitOf(31),
        )
        assertEquals(
            at("2026-03-31T07:00"),
            NextFireCalculator.nextFire(7, 0, r, at("2026-01-31T10:00")),
        )
    }

    @Test
    fun `configuracao incompleta nao agenda`() {
        val from = at("2026-08-05T10:00")
        val semDia = Recurrence(RepeatKind.MONTHLY_DAYS)
        val semOcorrencia = Recurrence(
            RepeatKind.MONTHLY_ORDINAL,
            DayMask.bitOf(java.time.DayOfWeek.FRIDAY),
        )
        assertNull(NextFireCalculator.nextFire(7, 0, semDia, from))
        assertNull(NextFireCalculator.nextFire(7, 0, semOcorrencia, from))
    }

    @Test
    fun `variante em millis concorda com a variante tipada`() {
        val from = at("2026-08-05T10:00")
        val typed = NextFireCalculator.nextFire(22, 0, DayMask.EVERY_DAY, from)
        val millis = NextFireCalculator.nextFireMillis(22, 0, DayMask.EVERY_DAY, from)
        assertEquals(typed!!.toInstant().toEpochMilli(), millis)
    }

    // ------------------------------------------------------- data única e pulo

    @Test
    fun `data unica dispara no dia marcado e nunca mais`() {
        val once = Recurrence.once(java.time.LocalDate.parse("2026-08-18"))
        assertEquals(at("2026-08-18T07:00"), NextFireCalculator.nextFire(7, 0, once, at("2026-08-05T10:00")))
        // Passada a hora, não sobra próximo disparo — é o que faz o app apagá-lo.
        assertNull(NextFireCalculator.nextFire(7, 0, once, at("2026-08-18T07:30")))
    }

    @Test
    fun `data unica alem do horizonte de varredura ainda e encontrada`() {
        // Dois anos à frente: passa longe dos 550 dias que a varredura semanal cobre.
        val once = Recurrence.once(java.time.LocalDate.parse("2028-08-18"))
        assertEquals(at("2028-08-18T07:00"), NextFireCalculator.nextFire(7, 0, once, at("2026-08-05T10:00")))
    }

    @Test
    fun `pular o proximo toque adia para o dia seguinte`() {
        val from = at("2026-08-05T22:00")
        val diario = Recurrence.weekly(DayMask.EVERY_DAY)
        val amanha = NextFireCalculator.nextFireMillis(7, 0, diario, from)

        val outlook = NextFireCalculator.outlook(7, 0, diario, from, skipFireAt = amanha)
        assertEquals(amanha, outlook.skipped)
        assertEquals(at("2026-08-07T07:00").toInstant().toEpochMilli(), outlook.nextFire)
    }

    @Test
    fun `pulo semanal devolve o toque da semana seguinte`() {
        // Só às quartas: pular uma quarta custa sete dias, não um.
        val quarta = Recurrence.weekly(DayMask.bitOf(java.time.DayOfWeek.WEDNESDAY))
        val from = at("2026-08-05T10:00")
        val proxima = NextFireCalculator.nextFireMillis(7, 0, quarta, from)

        val outlook = NextFireCalculator.outlook(7, 0, quarta, from, skipFireAt = proxima)
        assertEquals(at("2026-08-19T07:00").toInstant().toEpochMilli(), outlook.nextFire)
    }

    @Test
    fun `pulo que nao bate com o proximo toque e simplesmente ignorado`() {
        // É o que faz o pulo caducar sozinho: horário passado, despertador reconfigurado,
        // recorrência trocada. Nenhum desses casos precisa de uma limpeza explícita.
        val from = at("2026-08-05T22:00")
        val diario = Recurrence.weekly(DayMask.EVERY_DAY)
        val esperado = NextFireCalculator.nextFireMillis(7, 0, diario, from)

        val velho = NextFireCalculator.outlook(7, 0, diario, from, skipFireAt = 1_000L)
        assertEquals(esperado, velho.nextFire)
        assertFalse(velho.isSkipping)

        val semPulo = NextFireCalculator.outlook(7, 0, diario, from, skipFireAt = null)
        assertEquals(esperado, semPulo.nextFire)
        assertFalse(semPulo.isSkipping)
    }

    @Test
    fun `pular a unica ativacao de um despertador de uma vez so nao deixa nada`() {
        val once = Recurrence.once(java.time.LocalDate.parse("2026-08-18"))
        val from = at("2026-08-05T10:00")
        val unico = NextFireCalculator.nextFireMillis(7, 0, once, from)

        val outlook = NextFireCalculator.outlook(7, 0, once, from, skipFireAt = unico)
        assertEquals(unico, outlook.skipped)
        assertNull(outlook.nextFire)
    }
}
