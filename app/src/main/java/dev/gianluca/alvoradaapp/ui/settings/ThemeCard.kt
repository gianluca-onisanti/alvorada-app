package dev.gianluca.alvoradaapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.data.ThemeChoice
import kotlinx.coroutines.launch

/**
 * Claro, escuro ou o que o sistema disser.
 *
 * "Seguir o sistema" continua sendo o padrão, que era o único comportamento da V1.
 * A escolha explícita existe porque o tema do aparelho e o do app não precisam
 * concordar: dá para querer o celular claro de dia e este app escuro sempre, já
 * que ele é aberto de madrugada mais do que em qualquer outro horário.
 *
 * A tela do alarme não obedece a isto e nunca vai: ela é escura em qualquer
 * configuração, porque às 6h da manhã um flash branco é agressão.
 */
@Composable
fun ThemeCard() {
    val context = LocalContext.current
    val preferences = remember {
        (context.applicationContext as AlvoradaApp).container.preferences
    }
    val scope = rememberCoroutineScope()
    val current by preferences.themeChoice.collectAsState(initial = ThemeChoice.SYSTEM)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Tema", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "A tela do despertador é sempre escura, independente daqui.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = current == choice,
                        onClick = { scope.launch { preferences.setThemeChoice(choice) } },
                        label = { Text(choice.label) },
                    )
                }
            }
        }
    }
}
