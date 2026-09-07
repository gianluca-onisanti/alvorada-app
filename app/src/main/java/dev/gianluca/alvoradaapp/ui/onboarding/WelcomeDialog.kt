package dev.gianluca.alvoradaapp.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Primeira coisa que o app mostra, uma única vez.
 *
 * Não é um tour: são três linhas do que o app faz e um empurrão para os Ajustes. O
 * despertador depende de cinco permissões que o Android nega em silêncio, e a hora de
 * descobrir isso é agora — não na manhã em que o alarme não tocar. Por isso "Configurar
 * agora" é o botão cheio e "Depois" é o discreto, e não o contrário.
 */
@Composable
fun WelcomeDialog(onConfigure: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bem-vindo ao Alvorada") },
        text = {
            Column {
                Text(
                    "Um despertador que só se dá por satisfeito quando o dia começa de " +
                        "verdade.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))

                Step(Icons.Filled.Alarm, "O despertador toca e traz as missões do dia.")
                Step(Icons.Filled.PhotoCamera, "Você prova o que fez com uma foto.")
                Step(Icons.Filled.Redeem, "Cumprir vira XP e moedas para gastar em prêmios.")

                Spacer(Modifier.height(16.dp))
                Text(
                    "Antes de confiar nele, vale passar pelos Ajustes: o Android bloqueia " +
                        "alarme exato, notificação e tela de bloqueio por padrão, e falha " +
                        "sempre em silêncio. Lá cada item tem um botão que leva direto à " +
                        "tela certa do sistema — e um teste de 1 minuto para confirmar que " +
                        "tudo funciona.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfigure) { Text("Configurar agora") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Depois") }
        },
    )
}

@Composable
private fun Step(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(8.dp))
}
