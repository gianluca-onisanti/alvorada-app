package dev.gianluca.alvoradaapp.ui.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.audio.LibraryFile
import dev.gianluca.alvoradaapp.audio.formatClipPosition

/** Escolher um áudio da biblioteca para virar som de um despertador. */
@Composable
fun AudioLibraryPickerDialog(onDismiss: () -> Unit, onPick: (LibraryFile) -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    var files by remember { mutableStateOf<List<LibraryFile>>(emptyList()) }

    LaunchedEffect(Unit) { files = container.audioLibraryStore.list() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Da biblioteca") },
        text = {
            if (files.isEmpty()) {
                Text(
                    "A pasta ${container.audioLibraryStore.folderLabel()} ainda está " +
                        "vazia. Baixe um áudio na tela de Áudios, ou jogue um arquivo " +
                        "lá pelo gerenciador de arquivos.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(files, key = { it.uri.toString() }) { file ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(file) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(file.displayName, style = MaterialTheme.typography.bodyLarge)
                            if (file.durationMs > 0) {
                                Text(
                                    formatClipPosition(file.durationMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
