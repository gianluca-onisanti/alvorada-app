package dev.gianluca.alvoradaapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Paleta deliberadamente sem vermelho de erro para estados de missão.
 * Missão não cumprida é informação, não repreensão — o app não deveria conseguir
 * te punir visualmente por um dia ruim. Os papéis `error*` ficam no padrão do
 * Material justamente porque nenhuma tela os usa.
 *
 * As duas paletas são escritas por inteiro, e não em quatro papéis como na V1: as
 * telas se apoiam em `primaryContainer`, `surfaceVariant` e na família
 * `surfaceContainer*` em vários lugares, e deixá-los caírem no padrão fazia o tema
 * claro exibir um lavanda de fábrica que não é a cara do app.
 */
private val DarkColors = darkColorScheme(
    primary = Purple,
    // Texto escuro sobre o roxo neon, não branco: além de ler como painel
    // iluminado, dobra o contraste que `#7C5CFF` com branco entrega.
    onPrimary = Color(0xFF0B0716),
    primaryContainer = Color(0xFF2A1F52),
    onPrimaryContainer = Color(0xFFDCD2FF),
    inversePrimary = PurpleDeep,

    secondary = Mint,
    onSecondary = Color(0xFF06251A),
    secondaryContainer = Color(0xFF123A2C),
    onSecondaryContainer = Color(0xFFA7F3D0),

    tertiary = Amber,
    onTertiary = Color(0xFF251700),
    tertiaryContainer = Color(0xFF3D2A05),
    onTertiaryContainer = Color(0xFFFFDFA8),

    background = InkBase,
    onBackground = InkText,
    surface = InkSurface,
    onSurface = InkText,
    surfaceVariant = InkVariant,
    onSurfaceVariant = InkTextMuted,
    surfaceTint = Purple,

    surfaceContainerLowest = Color(0xFF08050F),
    surfaceContainerLow = Color(0xFF100C1E),
    surfaceContainer = Color(0xFF171128),
    surfaceContainerHigh = InkElevated,
    surfaceContainerHighest = Color(0xFF262040),

    inverseSurface = InkText,
    inverseOnSurface = InkSurface,
    outline = Color(0xFF4A3F72),
    outlineVariant = Color(0xFF2E2750),
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = PurpleDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF1B0A5C),
    inversePrimary = Color(0xFFC4B4FF),

    secondary = MintDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC4F2DC),
    onSecondaryContainer = Color(0xFF00271A),

    // No claro o âmbar não pode ser papel de texto: `#FFB020` sobre branco não se
    // lê. Ele desce para um tom legível e a versão viva sobra para o container.
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDFA8),
    onTertiaryContainer = Color(0xFF2B1B00),

    background = Color(0xFFF6F4FC),
    onBackground = Color(0xFF1A1526),
    surface = Color.White,
    onSurface = Color(0xFF1A1526),
    surfaceVariant = Color(0xFFE8E3F5),
    onSurfaceVariant = Color(0xFF4B4463),
    surfaceTint = PurpleDeep,

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF8FE),
    surfaceContainer = Color(0xFFF3F0FA),
    surfaceContainerHigh = Color(0xFFEDE9F7),
    surfaceContainerHighest = Color(0xFFE7E2F3),

    inverseSurface = Color(0xFF2F2A40),
    inverseOnSurface = Color(0xFFF4F1FB),
    outline = Color(0xFF7B7397),
    outlineVariant = Color(0xFFCBC4DE),
    scrim = Color.Black,
)

@Composable
fun AlvoradaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AlvoradaShapes,
        typography = AlvoradaTypography,
        content = content,
    )
}

/**
 * A tela do alarme é sempre escura: às 6h da manhã, um flash branco é agressão.
 *
 * `shapes` e `typography` precisam ser repetidos aqui. Esta é a segunda porta de
 * entrada do tema, e esquecê-la faria a tela do alarme manter a geometria e a fonte
 * antigas em silêncio — sem erro de compilação para avisar.
 */
@Composable
fun AlvoradaAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        shapes = AlvoradaShapes,
        typography = AlvoradaTypography,
        content = content,
    )
}
