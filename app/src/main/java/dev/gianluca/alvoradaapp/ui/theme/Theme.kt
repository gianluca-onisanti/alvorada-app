package dev.gianluca.alvoradaapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF7C5CFF)
private val PurpleDark = Color(0xFF5B3FD9)
private val Mint = Color(0xFF3DDC97)
private val Amber = Color(0xFFFFB020)
private val InkDark = Color(0xFF120E22)
private val InkSurface = Color(0xFF1E1834)

/**
 * Paleta deliberadamente sem vermelho de erro para estados de missão.
 * Missão não cumprida é informação, não repreensão — o app não deveria conseguir
 * te punir visualmente por um dia ruim.
 */
private val DarkColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    secondary = Mint,
    tertiary = Amber,
    background = InkDark,
    onBackground = Color(0xFFEDE9F7),
    surface = InkSurface,
    onSurface = Color(0xFFEDE9F7),
    surfaceVariant = Color(0xFF2A2246),
    onSurfaceVariant = Color(0xFFB9B0D4),
)

private val LightColors = lightColorScheme(
    primary = PurpleDark,
    onPrimary = Color.White,
    secondary = Color(0xFF1F9E6E),
    tertiary = Amber,
)

@Composable
fun AlvoradaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

/** A tela do alarme é sempre escura: às 6h da manhã, um flash branco é agressão. */
@Composable
fun AlvoradaAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
