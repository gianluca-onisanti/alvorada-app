package dev.gianluca.alvoradaapp.ui.audio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.audio.AudioDownloadWorker
import dev.gianluca.alvoradaapp.audio.RemoteAudio
import dev.gianluca.alvoradaapp.audio.ResolveException
import dev.gianluca.alvoradaapp.audio.formatClipPosition
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Colar um link e trazer o áudio para dentro.
 *
 * Em dois passos de propósito: **resolver** e depois **baixar**. Ver o título e a
 * duração antes de gastar rede é o que evita descobrir, três minutos depois, que o
 * link colado era do vídeo errado.
 */
@Composable
fun AudioImportDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    val resolvers = container.mediaResolvers
    val scope: CoroutineScope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var resolved by remember { mutableStateOf<RemoteAudio?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }

    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(AudioDownloadWorker.WORK_NAME)
        .collectAsState(initial = emptyList())
    val work = workInfos.firstOrNull()
    val progress = work?.progress?.getInt(AudioDownloadWorker.KEY_PROGRESS, 0) ?: 0

    LaunchedEffect(work?.state, downloading) {
        if (!downloading) return@LaunchedEffect
        when (work?.state) {
            WorkInfo.State.SUCCEEDED -> onDone()
            WorkInfo.State.FAILED -> {
                error = work.outputData.getString(AudioDownloadWorker.KEY_ERROR)
                    ?: "O download falhou."
                downloading = false
            }
            else -> Unit
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("Baixar áudio") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; resolved = null; error = null },
                    label = { Text("Link do vídeo ou do arquivo") },
                    enabled = !downloading,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "Aceita YouTube e endereços diretos de arquivo. O áudio vai parar " +
                        "em ${container.audioLibraryStore.folderLabel()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                resolved?.let { audio ->
                    Spacer(Modifier.height(12.dp))
                    Text(audio.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        buildString {
                            if (audio.durationMs > 0) {
                                append(formatClipPosition(audio.durationMs))
                                append("  ·  ")
                            }
                            audio.bitrateKbps?.let { append("$it kbps  ·  ") }
                            append(audio.mimeType)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (resolving || downloading) {
                    Spacer(Modifier.height(12.dp))
                    if (downloading && progress > 0) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            val audio = resolved
            if (audio == null) {
                TextButton(
                    enabled = url.isNotBlank() && !resolving,
                    onClick = {
                        resolving = true
                        error = null
                        scope.launch {
                            runCatching { resolvers.resolve(url) }
                                .onSuccess { resolved = it }
                                .onFailure {
                                    error = (it as? ResolveException)?.message
                                        ?: "Não consegui ler esse endereço."
                                }
                            resolving = false
                        }
                    },
                ) { Text("Buscar") }
            } else {
                TextButton(
                    enabled = !downloading,
                    onClick = {
                        downloading = true
                        error = null
                        AudioDownloadWorker.enqueue(
                            context = context,
                            audio = audio,
                            fileName = "${audio.title}.${audio.extension}",
                        )
                    },
                ) { Text("Baixar") }
            }
        },
        dismissButton = {
            TextButton(enabled = !downloading, onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
