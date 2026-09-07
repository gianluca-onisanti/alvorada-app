package dev.gianluca.alvoradaapp.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * A barra superior de toda tela de primeiro nível.
 *
 * Na V1 cada tela montava a sua, o que significava sete `TopAppBar` iguais e sete
 * `@OptIn(ExperimentalMaterial3Api::class)`. Com o menu lateral isso deixou de ser
 * só repetição: o botão de abrir o menu teria que ser copiado nas sete. Concentrar
 * aqui dá um lugar único também para o tratamento translúcido.
 *
 * O container é transparente de propósito — é o gradiente de [AlvoradaBackground]
 * que aparece atrás do título. O `Scaffold` reserva a altura da barra, então nada
 * de conteúdo passa por baixo dela; o que fica atrás é só o fundo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlvoradaTopBar(
    title: String,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = "Abrir menu")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}
