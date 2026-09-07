package dev.gianluca.alvoradaapp.core

import java.time.DayOfWeek

/**
 * Dias da semana como bitmask de 7 bits: bit 0 = segunda … bit 6 = domingo.
 *
 * O mapeamento acompanha `DayOfWeek.value` do java.time (MONDAY = 1 … SUNDAY = 7),
 * então a conversão é sempre `value - 1`. Guardar como `Int` deixa o filtro
 * "missões deste dia" resolvível direto em SQL com `daysMask & :dayBit`.
 */
object DayMask {

    const val NONE = 0
    const val EVERY_DAY = 0b1111111
    const val WEEKDAYS = 0b0011111   // seg–sex
    const val WEEKEND = 0b1100000    // sáb–dom

    fun bitOf(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bitOf(day) != 0

    fun with(mask: Int, day: DayOfWeek): Int = mask or bitOf(day)

    fun without(mask: Int, day: DayOfWeek): Int = mask and bitOf(day).inv()

    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor bitOf(day)

    fun daysIn(mask: Int): List<DayOfWeek> = DayOfWeek.values().filter { contains(mask, it) }

    /** `true` se todo dia de [subset] também existe em [superset]. */
    fun isSubsetOf(subset: Int, superset: Int): Boolean = subset and superset.inv() == 0

    /** Usado ao encolher os dias de um alarme: as missões não podem sobrar fora. */
    fun intersect(a: Int, b: Int): Int = a and b

    private val SHORT_LABELS = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

    fun shortLabel(day: DayOfWeek): String = SHORT_LABELS[day.value - 1]

    /** Rótulo curto para a lista de despertadores: "Todo dia", "Seg–Sex", "Seg, Qua, Sex". */
    fun describe(mask: Int): String = when (mask) {
        NONE -> "Nenhum dia"
        EVERY_DAY -> "Todo dia"
        WEEKDAYS -> "Seg–Sex"
        WEEKEND -> "Fim de semana"
        else -> daysIn(mask).joinToString(", ") { shortLabel(it) }
    }
}
