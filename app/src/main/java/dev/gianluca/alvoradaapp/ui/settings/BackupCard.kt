package dev.gianluca.alvoradaapp.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.backup.BackupResult
import kotlinx.coroutines.launch

/**
 * Export e restauração.
 *
 * As fotos moram no diretório privado do app, fora de qualquer backup automático do
 * Android. Isso é ótimo para privacidade e péssimo para durabilidade: desinstalar
 * apaga meses de registro sem nenhum aviso. Este card é o único caminho para tirar
 * esses dados do aparelho.
 */
@Composable
fun BackupCard() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    val manager = container.backupManager
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = when (val result = manager.export(uri)) {
                is BackupResult.Exported ->
                    "Exportado: ${result.alarmCount} despertador(es) e ${result.photoCount} foto(s)"

                is BackupResult.Failed -> "Falhou: ${result.message}"
                else -> null
            }
            busy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Confirmação vem depois de escolher o arquivo: a pergunta fica concreta,
        // e não uma advertência abstrata antes de haver o que restaurar.
        pendingRestore = uri.toString()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Um único ZIP com todos os despertadores, missões, pontos e fotos. " +
                    "Desinstalar o app apaga tudo que está aqui dentro — este arquivo " +
                    "é a única cópia que sobrevive a isso.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { exportLauncher.launch(manager.suggestedFileName()) },
                    enabled = !busy,
                ) { Text("Exportar") }

                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip")) },
                    enabled = !busy,
                ) { Text("Restaurar") }
            }
            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    pendingRestore?.let { uriString ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Substituir tudo?") },
            text = {
                Text(
                    "A restauração apaga os despertadores, missões, pontos e fotos " +
                        "atuais e coloca os do backup no lugar. Não mescla, e não dá " +
                        "para desfazer.\n\nSe o que está no aparelho ainda importa, " +
                        "exporte antes."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    busy = true
                    scope.launch {
                        message = when (val result = manager.import(uriString.toUri())) {
                            is BackupResult.Imported -> {
                                // O banco novo tem outros despertadores — o que estava
                                // agendado no sistema não corresponde mais a nada.
                                container.alarmScheduler.rescheduleAll()
                                "Restaurado: ${result.alarmCount} despertador(es) e " +
                                    "${result.photoCount} foto(s)"
                            }

                            is BackupResult.Failed -> "Falhou: ${result.message}"
                            else -> null
                        }
                        busy = false
                    }
                }) { Text("Substituir") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Cancelar") }
            },
        )
    }
}
