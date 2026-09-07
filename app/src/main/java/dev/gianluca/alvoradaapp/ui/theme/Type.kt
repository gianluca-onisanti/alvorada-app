package dev.gianluca.alvoradaapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.gianluca.alvoradaapp.R

/**
 * Chakra Petch (OFL, licença em `licenses/ChakraPetch-OFL.txt`). Angular e de
 * numerais largos — combina com os cantos de [AlvoradaShapes] e é o que carrega o
 * registro futurista sem depender de cor.
 *
 * Empacotada em vez de baixada em tempo de execução: um despertador precisa
 * desenhar a hora com o avião ligado, sem rede e sem Play Services.
 */
private val Display = FontFamily(
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

private val Base = Typography()

/**
 * A fonte de display fica na moldura — relógio, números do painel, cabeçalhos,
 * rótulos de botão e de seção. **O corpo de texto segue na fonte do sistema**, que
 * é a mais legível às 6h da manhã e a que o usuário já ajustou no aparelho.
 *
 * `titleMedium` e `titleSmall` também ficam de fora: eles carregam título de missão
 * e de despertador, que é conteúdo do usuário, não moldura.
 */
val AlvoradaTypography = Base.copy(
    displayLarge = Base.displayLarge.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    displayMedium = Base.displayMedium.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    displaySmall = Base.displaySmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),

    headlineLarge = Base.headlineLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),

    titleLarge = Base.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),

    // Tracking aberto nos rótulos: é o detalhe que faz um texto curto em maiúsculas
    // ler como interface de instrumento em vez de texto apertado. `SectionTitle` já
    // passa tudo para maiúsculas, então é aqui que aquele efeito acontece.
    labelLarge = Base.labelLarge.copy(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
    ),
    labelMedium = Base.labelMedium.copy(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.0.sp,
    ),
    labelSmall = Base.labelSmall.copy(
        fontFamily = Display,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.0.sp,
    ),
)
