package dev.gianluca.alvoradaapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gianluca.alvoradaapp.ui.theme.GLASS_ALPHA
import dev.gianluca.alvoradaapp.ui.theme.GLASS_BORDER_ALPHA
import dev.gianluca.alvoradaapp.ui.theme.GLASS_SHEEN_ALPHA

/**
 * Peças visuais repetidas em mais de uma tela.
 *
 * Existem para que "título de seção" e "estado vazio" tenham a mesma forma em todo
 * lugar — a inconsistência entre telas é o que faz um app parecer costurado à mão,
 * mesmo quando cada tela isolada está bem resolvida.
 */

/**
 * O fundo do app, e o que as superfícies de vidro deixam passar.
 *
 * O gradiente não é enfeite: [GlassCard] é translúcido, e sobre um fundo de cor
 * chapada a translucidez não se lê — o cartão só parece apagado. O roxo no topo dá
 * a fonte de luz que faz o resto da tela parecer iluminada por ela.
 */
@Composable
fun AlvoradaBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier = modifier.fillMaxSize(), color = scheme.background) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scheme.primary.copy(alpha = 0.10f),
                        0.30f to scheme.background,
                        1f to scheme.surfaceContainerLow,
                    )
                )
        ) {
            content()
        }
    }
}

/**
 * Cartão translúcido: a superfície da V2.
 *
 * Vidro de verdade exigiria desfocar o que está **atrás** do cartão, e
 * `RenderEffect.createBlurEffect` só existe da API 31 em diante — o `minSdk` aqui é
 * 29. A leitura de vidro vem então de três coisas que funcionam em qualquer versão
 * e custam zero de desempenho: opacidade sobre o gradiente de [AlvoradaBackground],
 * um fio de contorno na cor primária, e um realce no topo que sugere espessura.
 *
 * [container] recebe o mesmo papel de cor que o `CardDefaults.cardColors` recebia
 * antes — normalmente `surfaceVariant` ou `primaryContainer` — e a opacidade é
 * aplicada aqui, num lugar só.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = container.copy(alpha = GLASS_ALPHA),
            contentColor = contentColorFor(container),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = GLASS_BORDER_ALPHA),
        ),
        // Sem sombra: a borda já separa o cartão do fundo, e sombra sob superfície
        // translúcida entrega que não há vidro nenhum ali.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = GLASS_SHEEN_ALPHA),
                    0.40f to Color.Transparent,
                )
            ),
            content = content,
        )
    }
}

/** Título de seção com a contagem à direita, quando ela diz algo. */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            // O tracking largo vive aqui, e não no token: este é o único texto do app
            // curto o bastante, e com a linha inteira só para si, para ganhar com ele
            // sem custar quebra de linha em outro lugar.
            letterSpacing = 1.4.sp,
            color = color,
            modifier = Modifier.weight(1f),
        )
        trailing?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A cor da categoria, do tamanho de um marcador.
 *
 * Quadrado, e não redondo: é a mesma geometria de [CategoryStripe] e das superfícies,
 * e um único círculo no meio de uma tela de cantos retos chama atenção para si.
 */
@Composable
fun ColorDot(colorHex: String, modifier: Modifier = Modifier, size: Int = 10) {
    Box(
        modifier
            .size(size.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(parseColor(colorHex))
    )
}

/**
 * Barra vertical na borda do card, na cor da categoria.
 *
 * Identifica a origem sem gastar uma linha de texto nem competir com o conteúdo —
 * a cor é lida na periferia da visão, antes de qualquer palavra.
 */
@Composable
fun CategoryStripe(colorHex: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxHeight()
            .width(5.dp)
            .background(parseColor(colorHex))
    )
}

/** Estado vazio: o que não existe ainda, e o que faz existir. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
