package dev.gianluca.alvoradaapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.gianluca.alvoradaapp.R

/** Um destino de primeiro nível, como o menu e a barra de baixo o enxergam. */
data class NavDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

/**
 * O menu lateral.
 *
 * Substituiu a barra de cinco abas porque a V2 acrescenta destinos e cinco já era o
 * teto do que cabe numa barra sem virar sopa de ícones. Ajustes vive aqui agora: em
 * vez de uma chave escondida no cabeçalho do Painel, um item nomeado como os outros.
 *
 * [separatorAfter] marca onde entra a linha que separa o uso diário do resto — o que
 * está acima dela é o que também aparece na barra de baixo.
 */
@Composable
fun AlvoradaDrawer(
    destinations: List<NavDestination>,
    currentRoute: String?,
    separatorAfter: Int,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_alvorada),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.size(14.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text("Alvorada", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Acordar é só o começo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        destinations.forEachIndexed { index, destination ->
            NavigationDrawerItem(
                label = { Text(destination.label) },
                icon = { Icon(destination.icon, contentDescription = null) },
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination.route) },
                shape = MaterialTheme.shapes.small,
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (index == separatorAfter) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
