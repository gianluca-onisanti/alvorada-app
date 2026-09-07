package dev.gianluca.alvoradaapp.core

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Como um despertador se repete.
 *
 * O seletor de dias da semana cobre 90% dos casos e continua sendo o padrão — mas
 * "última sexta do mês" e "dia 15 e 30" não cabem num bitmask de 7 bits. Em vez de
 * inventar um campo novo para cada formato, o tipo de repetição vira explícito e cada
 * variante usa só os campos que lhe interessam.
 *
 * Tudo aqui é puro e determinístico: dada uma data, a recorrência responde sim ou não.
 * O agendador não precisa saber de nenhuma dessas regras — só perguntar.
 */
@Serializable
enum class RepeatKind {
    /** Dias fixos da semana, toda semana. O padrão. */
    WEEKLY,

    /** Dias fixos da semana, mas só a cada N semanas contadas a partir de uma âncora. */
    WEEK_INTERVAL,

    /** Ocorrência do dia da semana dentro do mês: 1ª, 2ª… ou a última sexta. */
    MONTHLY_ORDINAL,

    /** Dias do calendário: 15 e 30, ou o último dia do mês. */
    MONTHLY_DAYS,

    /**
     * Uma data só, e nunca mais.
     *
     * É o despertador autodestruível: depois que ele toca e você o desliga, não sobra
     * nada — nem o agendamento, nem a linha no banco. Um alarme de uma vez só que
     * continuasse na lista desligado viraria entulho, e entulho é o que faz a lista
     * de despertadores deixar de ser confiável.
     */
    ONCE,
}

/**
 * Quais ocorrências de um dia da semana dentro do mês valem.
 *
 * Bits 0..4 = 1ª a 5ª ocorrência; bit 5 = a última, seja ela a 4ª ou a 5ª. "Última" é
 * um conceito separado de "5ª" de propósito: meses com quatro sextas não têm 5ª sexta,
 * e quem pede "última sexta do mês" quer que ela aconteça todo mês.
 */
object OrdinalMask {

    const val NONE = 0
    const val LAST = 1 shl 5

    /** [index] 0 = primeira … 4 = quinta. */
    fun bitOf(index: Int): Int = 1 shl index

    fun contains(mask: Int, index: Int): Boolean = mask and bitOf(index) != 0

    fun toggle(mask: Int, index: Int): Int = mask xor bitOf(index)

    fun hasLast(mask: Int): Boolean = mask and LAST != 0

    fun toggleLast(mask: Int): Int = mask xor LAST

    /** A que ocorrência do mês esta data corresponde: 0 = primeira. */
    fun ordinalOf(date: LocalDate): Int = (date.dayOfMonth - 1) / 7

    /** `true` se não existe outro dia da semana igual mais adiante no mesmo mês. */
    fun isLastOfMonth(date: LocalDate): Boolean = date.plusWeeks(1).month != date.month

    fun matches(mask: Int, date: LocalDate): Boolean =
        contains(mask, ordinalOf(date)) || (hasLast(mask) && isLastOfMonth(date))

    private val LABELS = listOf("1ª", "2ª", "3ª", "4ª", "5ª")

    fun label(index: Int): String = LABELS[index]

    /** "1ª e última", "2ª e 4ª". Vazio quando nada está marcado. */
    fun describe(mask: Int): String {
        val parts = LABELS.indices.filter { contains(mask, it) }.map { LABELS[it] } +
            if (hasLast(mask)) listOf("última") else emptyList()
        return parts.joinToPtBr()
    }
}

/**
 * Dias do calendário.
 *
 * Bits 0..30 = dias 1 a 31; bit 31 = o último dia do mês. O dia 31 marcado simplesmente
 * não acontece em fevereiro — o que é o comportamento honesto para quem pediu "dia 31".
 * Quem quer o fechamento do mês marca "último dia", que é sempre o dia certo.
 */
object MonthDayMask {

    const val NONE = 0
    const val LAST_DAY = 1 shl 31

    fun bitOf(day: Int): Int = 1 shl (day - 1)

    fun contains(mask: Int, day: Int): Boolean = mask and bitOf(day) != 0

    fun toggle(mask: Int, day: Int): Int = mask xor bitOf(day)

    fun hasLastDay(mask: Int): Boolean = mask and LAST_DAY != 0

    fun toggleLastDay(mask: Int): Int = mask xor LAST_DAY

    fun matches(mask: Int, date: LocalDate): Boolean =
        contains(mask, date.dayOfMonth) ||
            (hasLastDay(mask) && date.dayOfMonth == date.lengthOfMonth())

    fun daysIn(mask: Int): List<Int> = (1..31).filter { contains(mask, it) }

    /** "Dia 15" · "Dias 15, 30 e último". */
    fun describe(mask: Int): String {
        val days = daysIn(mask).map { it.toString() } +
            if (hasLastDay(mask)) listOf("último") else emptyList()
        if (days.isEmpty()) return "Nenhum dia"
        val prefix = if (days.size == 1) "Dia" else "Dias"
        return "$prefix ${days.joinToPtBr()}"
    }
}

/**
 * Deliberadamente **não** serializável: o backup grava as colunas achatadas da tabela
 * de alarmes, não este objeto. Serializar `LocalDate` exigiria um serializer próprio
 * para ganhar nada — a fronteira de persistência já é `AlarmEntity`.
 */
data class Recurrence(
    val kind: RepeatKind = RepeatKind.WEEKLY,
    /** Dias da semana. Usado por tudo, menos [RepeatKind.MONTHLY_DAYS]. */
    val daysMask: Int = DayMask.NONE,
    /** Só [RepeatKind.WEEK_INTERVAL]: 2 = quinzenal, 3 = a cada três semanas… */
    val intervalWeeks: Int = 2,
    /**
     * Semana de referência da contagem do intervalo. Sem âncora, "a cada duas semanas"
     * não significa nada — duas semanas a partir de quando?
     *
     * Em [RepeatKind.ONCE] este mesmo campo guarda a data única. Reusar a coluna em vez
     * de criar outra evita um segundo campo de data que só um dos modos leria — e que
     * ficaria eternamente fora de sincronia com este.
     */
    val anchorDate: LocalDate? = null,
    /** Só [RepeatKind.MONTHLY_ORDINAL]. Ver [OrdinalMask]. */
    val ordinalMask: Int = OrdinalMask.NONE,
    /** Só [RepeatKind.MONTHLY_DAYS]. Ver [MonthDayMask]. */
    val monthDaysMask: Int = MonthDayMask.NONE,
) {

    /**
     * `false` quando a configuração nunca produz um disparo — nenhum dia marcado,
     * ou dias marcados sem nenhuma ocorrência do mês escolhida. A UI usa isto para
     * impedir que o despertador seja salvo mudo.
     */
    val canFire: Boolean
        get() = when (kind) {
            RepeatKind.WEEKLY -> daysMask != DayMask.NONE
            RepeatKind.WEEK_INTERVAL -> daysMask != DayMask.NONE && intervalWeeks >= 1
            RepeatKind.MONTHLY_ORDINAL ->
                daysMask != DayMask.NONE && ordinalMask != OrdinalMask.NONE

            RepeatKind.MONTHLY_DAYS -> monthDaysMask != MonthDayMask.NONE

            // Data no passado ainda é uma configuração válida — só não tem mais
            // futuro. Quem responde isso é o cálculo do próximo disparo, não aqui.
            RepeatKind.ONCE -> anchorDate != null
        }

    val isOneShot: Boolean get() = kind == RepeatKind.ONCE

    /**
     * Em que dias da semana as missões deste despertador podem existir.
     *
     * Com dias do calendário o dia da semana é imprevisível — restringir a missão a
     * "segunda" faria com que ela simplesmente não existisse nos meses em que o dia 15
     * cai numa terça. Por isso, nesse modo, toda missão vale sempre que o alarme toca.
     *
     * O despertador de uma vez só devolve nenhum dia: ele não vive tempo suficiente
     * para cobrar nada, e uma missão nascida nele morreria junto no primeiro toque.
     */
    val missionDayMask: Int
        get() = when (kind) {
            RepeatKind.MONTHLY_DAYS -> DayMask.EVERY_DAY
            RepeatKind.ONCE -> DayMask.NONE
            else -> daysMask
        }

    fun matches(date: LocalDate): Boolean = when (kind) {
        RepeatKind.WEEKLY -> DayMask.contains(daysMask, date.dayOfWeek)

        RepeatKind.ONCE -> date == anchorDate

        RepeatKind.WEEK_INTERVAL ->
            DayMask.contains(daysMask, date.dayOfWeek) && weekPhase(date) == 0L

        RepeatKind.MONTHLY_ORDINAL ->
            DayMask.contains(daysMask, date.dayOfWeek) && OrdinalMask.matches(ordinalMask, date)

        RepeatKind.MONTHLY_DAYS -> MonthDayMask.matches(monthDaysMask, date)
    }

    /**
     * Quantas semanas de distância da âncora, módulo o intervalo. Zero = semana válida.
     *
     * A comparação é feita entre as segundas-feiras das duas semanas, não entre as datas:
     * assim a fase não muda conforme o dia da semana em que a âncora foi criada.
     */
    private fun weekPhase(date: LocalDate): Long {
        val anchorMonday = (anchorDate ?: DEFAULT_ANCHOR).with(DayOfWeek.MONDAY)
        val weeks = ChronoUnit.WEEKS.between(anchorMonday, date.with(DayOfWeek.MONDAY))
        return Math.floorMod(weeks, intervalWeeks.coerceAtLeast(1).toLong())
    }

    /** Rótulo curto para a lista de despertadores. */
    fun describe(): String = when (kind) {
        RepeatKind.WEEKLY -> DayMask.describe(daysMask)

        RepeatKind.WEEK_INTERVAL ->
            if (intervalWeeks <= 1) DayMask.describe(daysMask)
            else "${DayMask.describe(daysMask)} · a cada $intervalWeeks semanas"

        RepeatKind.MONTHLY_ORDINAL ->
            "${DayMask.describe(daysMask)} · ${OrdinalMask.describe(ordinalMask)} do mês"

        RepeatKind.MONTHLY_DAYS -> MonthDayMask.describe(monthDaysMask)

        RepeatKind.ONCE -> anchorDate
            ?.let { "Uma vez em %02d/%02d".format(it.dayOfMonth, it.monthValue) }
            ?: "Sem data"
    }

    companion object {
        /** Segunda-feira. Só serve para a fase ficar estável quando não há âncora. */
        val DEFAULT_ANCHOR: LocalDate = LocalDate.of(2024, 1, 1)

        fun weekly(daysMask: Int) = Recurrence(RepeatKind.WEEKLY, daysMask)

        fun once(date: LocalDate) = Recurrence(RepeatKind.ONCE, anchorDate = date)
    }
}

/** "a", "a e b", "a, b e c" — o "e" antes do último item, como se escreve em português. */
private fun List<String>.joinToPtBr(): String = when (size) {
    0 -> ""
    1 -> first()
    else -> dropLast(1).joinToString(", ") + " e " + last()
}
