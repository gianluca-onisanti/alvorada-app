package dev.gianluca.alvoradaapp.core

import kotlin.math.floor
import kotlin.math.sqrt

data class LevelProgress(
    val level: Int,
    val xpIntoLevel: Int,
    val xpNeededForNext: Int,
) {
    val ratio: Float
        get() = if (xpNeededForNext == 0) 1f else xpIntoLevel.toFloat() / xpNeededForNext
}

/**
 * Curva de nível a partir do XP acumulado.
 *
 * Quadrática e não exponencial de propósito: os primeiros níveis vêm rápido, quando
 * ainda não há hábito para sustentar a motivação, e o espaçamento cresce de forma
 * previsível — nunca ao ponto de o próximo nível parecer inalcançável.
 *
 * **XP nunca decresce.** Nada no app remove XP: atraso custa moedas, soneca custa
 * moedas, missão não cumprida não custa nada. O nível é um registro do que você já
 * fez, e o passado não deixa de ter acontecido por causa de uma semana ruim.
 */
object Leveling {

    private const val STEP = 25

    /** XP acumulado necessário para alcançar [level]. Nível 1 começa em zero. */
    fun xpForLevel(level: Int): Int {
        val l = level.coerceAtLeast(1)
        return STEP * l * (l - 1)
    }

    fun levelOf(xp: Int): Int {
        if (xp <= 0) return 1
        // Inverso de xp = 25·L·(L−1): L = ⌊(1 + √(1 + 4·xp/25)) / 2⌋
        val l = floor((1 + sqrt(1 + 4.0 * xp / STEP)) / 2).toInt()
        return l.coerceAtLeast(1)
    }

    fun progressOf(xp: Int): LevelProgress {
        val safeXp = xp.coerceAtLeast(0)
        val level = levelOf(safeXp)
        val floorXp = xpForLevel(level)
        val nextXp = xpForLevel(level + 1)
        return LevelProgress(
            level = level,
            xpIntoLevel = safeXp - floorXp,
            xpNeededForNext = nextXp - floorXp,
        )
    }
}
