package dev.gianluca.alvoradaapp.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Uma rodada de checklist: de quando até quando, e até que horas.
 *
 * [endsAt] é o instante em que a rodada seguinte começa, e é ele — não [dueAt] — que
 * define até quando o despertador de um item marcado fica calado. Os dois são
 * diferentes de propósito: um checklist diário que vence às 22h continua sendo do
 * dia até a meia-noite, e calar o alarme só até as 22h o faria tocar de novo à noite
 * por algo já feito.
 */
data class Cycle(
    val periodStart: LocalDate,
    val dueAt: Long,
    val endsAt: Long,
) {
    fun contains(instant: Long): Boolean = instant < endsAt
}

/**
 * A matemática de rodada, pura e determinística.
 *
 * Mora em `core` pela mesma razão que [NextFireCalculator] e [Recurrence]: erro aqui
 * só aparece meses depois, num mês em que o dia 31 não existe ou numa quinta semana
 * que não acontece. É onde os testes deste projeto ficam.
 *
 * Nada aqui inventa frequência nova. Os cinco modos de [Recurrence] já cobrem o que
 * um checklist precisa — diário é `WEEKLY` com todos os dias, quinzenal é
 * `WEEK_INTERVAL` com âncora, mensal é `MONTHLY_DAYS` ou `MONTHLY_ORDINAL`.
 */
object ChecklistCycle {

    /**
     * A rodada vigente em [at], ou `null` se a recorrência nunca dispara.
     *
     * "Vigente" é a última data que a recorrência aceita **até hoje**, e não a
     * próxima: um checklist mensal do dia 1 continua valendo no dia 20.
     */
    fun currentAt(
        recurrence: Recurrence,
        dueHour: Int,
        dueMinute: Int,
        at: ZonedDateTime,
    ): Cycle? {
        if (!recurrence.canFire) return null
        val start = lastMatchOnOrBefore(recurrence, at.toLocalDate()) ?: return null
        return build(recurrence, dueHour, dueMinute, start, at)
    }

    /** A rodada seguinte a [after], ou `null` se não houver outra. */
    fun next(
        recurrence: Recurrence,
        dueHour: Int,
        dueMinute: Int,
        after: Cycle,
        zone: java.time.ZoneId,
    ): Cycle? {
        if (!recurrence.canFire) return null
        val start = firstMatchAfter(recurrence, after.periodStart) ?: return null
        val at = Instant.ofEpochMilli(after.endsAt).atZone(zone)
        return build(recurrence, dueHour, dueMinute, start, at)
    }

    private fun build(
        recurrence: Recurrence,
        dueHour: Int,
        dueMinute: Int,
        start: LocalDate,
        reference: ZonedDateTime,
    ): Cycle {
        val zone = reference.zone
        val time = LocalTime.of(dueHour, dueMinute)
        val dueAt = start.atTime(time).atZone(zone).toInstant().toEpochMilli()

        // O fim da rodada é o começo da próxima. Sem uma próxima — uma recorrência
        // de data única — a rodada simplesmente não fecha, e o `Long.MAX_VALUE`
        // diria "para sempre", o que é o certo: não existe rodada seguinte para a
        // qual ela pudesse virar.
        val nextStart = firstMatchAfter(recurrence, start)
        val endsAt = nextStart
            ?.atStartOfDay(zone)
            ?.toInstant()
            ?.toEpochMilli()
            ?: Long.MAX_VALUE

        return Cycle(periodStart = start, dueAt = dueAt, endsAt = endsAt)
    }

    /**
     * A última data que a recorrência aceita em [date] ou antes.
     *
     * A varredura vai para trás pelo mesmo motivo que a de [NextFireCalculator] vai
     * para a frente: "dia 31" pula fevereiro inteiro e "5ª sexta" pode faltar por
     * três meses seguidos. O horizonte é o mesmo — mais barato que uma consulta ao
     * banco, e cobre qualquer buraco que o calendário produza.
     */
    private fun lastMatchOnOrBefore(recurrence: Recurrence, date: LocalDate): LocalDate? {
        for (offset in 0..SCAN_DAYS) {
            val candidate = date.minusDays(offset.toLong())
            if (recurrence.matches(candidate)) return candidate
        }
        return null
    }

    private fun firstMatchAfter(recurrence: Recurrence, date: LocalDate): LocalDate? {
        for (offset in 1..SCAN_DAYS) {
            val candidate = date.plusDays(offset.toLong())
            if (recurrence.matches(candidate)) return candidate
        }
        return null
    }

    /** Mesmo horizonte de `NextFireCalculator`, pelas mesmas razões de calendário. */
    private const val SCAN_DAYS = 550
}
