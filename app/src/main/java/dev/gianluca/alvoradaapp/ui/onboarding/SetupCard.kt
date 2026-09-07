package dev.gianluca.alvoradaapp.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Aviso no topo do Painel enquanto alguma permissão do despertador estiver faltando.
 *
 * Não é dispensável, e é de propósito: quem tocou em "Depois" nas boas-vindas continua
 * com um alarme que não toca, e um aviso que se fecha para sempre esconderia o problema
 * até a manhã em que ele custa caro. Some sozinho quando os checks passam — inclusive
 * quando o sistema revoga uma permissão meses depois, que é o caso que ninguém procura.
 */
@Composable
fun SetupCard(pendingCount: Int, onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Âmbar: a mesma cor que a tela de Ajustes usa para item pendente.
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = if (pendingCount == 1) {
                        "1 ajuste pendente"
                    } else {
                        "$pendingCount ajustes pendentes"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Enquanto isso, o despertador pode não tocar — e você só descobriria na " +
                    "hora errada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenSettings) { Text("Revisar agora") }
        }
    }
}
