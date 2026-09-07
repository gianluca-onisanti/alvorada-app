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

    // `labelLarge` é o estilo do texto de **todo** botão e chip do Material, e por isso
    // fica no tracking padrão. A primeira versão abria 0.8sp aqui e o efeito foi
    // bonito e errado: 0.7sp por caractere infla um rótulo em ~10%, o suficiente para
    // "Adicionar item" quebrar em duas linhas dentro do botão. Rótulo de botão é
    // largura disputada, não moldura.
    labelLarge = Base.labelLarge.copy(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
    ),
    // Tracking moderado: `labelMedium` carrega as linhas curtas de metadado da lista
    // ("Checklist cumprido · volta em…"), que também disputam largura. O tracking
    // largo de verdade mora em `SectionTitle`, que é onde ele foi feito para estar —
    // texto curto, em maiúsculas, com a linha inteira só para si.
    labelMedium = Base.labelMedium.copy(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
    ),
    // `labelSmall` só rotula números soltos ("moedas", "nível"), onde não há o que
    // quebrar — mantém o tracking cheio.
    labelSmall = Base.labelSmall.copy(
        fontFamily = Display,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.0.sp,
    ),
)
