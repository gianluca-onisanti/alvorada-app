package dev.gianluca.alvoradaapp.ui.components

import androidx.compose.ui.graphics.Color
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "daqui a 7h12" — a informação que responde à única pergunta que se faz ao olhar
 * a lista de despertadores à noite.
 */
fun formatTimeUntil(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val duration = Duration.between(Instant.ofEpochMilli(nowMillis), Instant.ofEpochMilli(epochMillis))
    if (duration.isNegative || duration.isZero) return "agora"

    val days = duration.toDays()
    val hours = duration.toHours() % 24
    val minutes = duration.toMinutes() % 60

    return when {
        days > 0 -> "daqui a ${days}d ${hours}h"
        hours > 0 -> "daqui a ${hours}h ${minutes}min"
        minutes > 0 -> "daqui a ${minutes}min"
        else -> "daqui a menos de 1min"
    }
}

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val FIRE_MOMENT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, dd/MM 'às' HH:mm", PT_BR)

/**
 * "sex, 28/08 às 06:30 · daqui a 24d 3h".
 *
 * O relativo sozinho basta para um alarme de amanhã, mas não para um de "última sexta
 * do mês": "daqui a 24d" não dá para conferir de cabeça, e a data dá.
 */
fun formatFireMoment(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String =
    "${formatFireClock(epochMillis)}  ·  ${formatTimeUntil(epochMillis, nowMillis)}"

/** Só o momento, sem o relativo — para quando a frase em volta já situa no tempo. */
fun formatFireClock(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(FIRE_MOMENT)

fun formatClock(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

/** Cor da pasta. Volta ao roxo padrão se o hex gravado estiver corrompido. */
fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrDefault(Color(0xFF7C5CFF))
