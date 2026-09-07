package dev.gianluca.alvoradaapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelingTest {

    @Test
    fun `nivel 1 comeca em zero`() {
        assertEquals(0, Leveling.xpForLevel(1))
        assertEquals(1, Leveling.levelOf(0))
    }

    @Test
    fun `limiares batem com a curva`() {
        assertEquals(50, Leveling.xpForLevel(2))
        assertEquals(150, Leveling.xpForLevel(3))
        assertEquals(300, Leveling.xpForLevel(4))
        assertEquals(500, Leveling.xpForLevel(5))
    }

    @Test
    fun `nivel sobe exatamente no limiar, nem um xp antes`() {
        assertEquals(1, Leveling.levelOf(49))
        assertEquals(2, Leveling.levelOf(50))
        assertEquals(2, Leveling.levelOf(149))
        assertEquals(3, Leveling.levelOf(150))
    }

    @Test
    fun `progresso dentro do nivel e coerente`() {
        // 100 XP: nível 2 (piso 50), faltando 50 dos 100 que o nível 3 exige.
        val progress = Leveling.progressOf(100)
        assertEquals(2, progress.level)
        assertEquals(50, progress.xpIntoLevel)
        assertEquals(100, progress.xpNeededForNext)
        assertEquals(0.5f, progress.ratio, 0.001f)
    }

    @Test
    fun `xp negativo nao quebra nem produz nivel zero`() {
        // Nada no app remove XP, mas um extrato corrompido não pode virar crash.
        val progress = Leveling.progressOf(-500)
        assertEquals(1, progress.level)
        assertEquals(0, progress.xpIntoLevel)
    }

    @Test
    fun `cada nivel exige mais que o anterior`() {
        var previousGap = 0
        for (level in 1..30) {
            val gap = Leveling.xpForLevel(level + 1) - Leveling.xpForLevel(level)
            assertTrue("nível $level não cresceu", gap > previousGap)
            previousGap = gap
        }
    }

    @Test
    fun `levelOf e xpForLevel sao consistentes entre si`() {
        for (level in 1..40) {
            val floorXp = Leveling.xpForLevel(level)
            assertEquals(level, Leveling.levelOf(floorXp))
            assertEquals(level, Leveling.levelOf(floorXp + 1))
            if (level > 1) assertEquals(level - 1, Leveling.levelOf(floorXp - 1))
        }
    }
}
