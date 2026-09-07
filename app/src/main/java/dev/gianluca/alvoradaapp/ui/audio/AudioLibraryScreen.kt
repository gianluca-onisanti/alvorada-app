package dev.gianluca.alvoradaapp.ui.audio

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.alarm.AlarmSoundPlayer
import dev.gianluca.alvoradaapp.audio.LibraryFile
import dev.gianluca.alvoradaapp.audio.formatClipPosition
import dev.gianluca.alvoradaapp.ui.components.AlvoradaTopBar
import dev.gianluca.alvoradaapp.ui.components.EmptyState
import dev.gianluca.alvoradaapp.ui.components.GlassCard
import kotlinx.coroutines.launch

/**
 * Os áudios de `Music/Alvorada/`.
 *
 * Lista o **MediaStore**, e não uma tabela do app: um MP3 largado na pasta pelo
 * gerenciador de arquivos precisa aparecer aqui, e ele nunca passou por nenhuma
 * tabela nossa. Ler arquivo que o app não criou é exatamente o que a permissão de
 * mídia habilita — daí o pedido no topo quando ela falta.
 */
@Composable
fun AudioLibraryScreen(onTrim: (String) -> Unit, onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    val store = container.audioLibraryStore
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<LibraryFile>>(emptyList()) }
    var reloadKey by remember { mutableStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<LibraryFile?>(null) }
    var playing by remember { mutableStateOf<Uri?>(null) }

    val mediaPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, mediaPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        reloadKey++
    }

    LaunchedEffect(reloadKey) { files = store.list() }

    // Pré-escuta com o mesmo player do alarme, para ouvir o som como ele vai soar
    // às 6h — no canal de alarme, e não no de mídia.
    val preview = remember { AlarmSoundPlayer(context) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }

    fun togglePlay(file: LibraryFile) {
        if (playing == file.uri) {
            preview.stop()
            playing = null
        } else {
            preview.start(
                soundUri = file.uri,
                volumePercent = 100,
                escalate = false,
                vibrate = false,
                loop = false,
            )
            playing = file.uri
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = { AlvoradaTopBar("Áudios", onOpenDrawer) },
        floatingActionButton = {
            FloatingActionButton(onClick = { importing = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Baixar áudio")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!hasPermission) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    container = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Deixe o app ler a pasta", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Sem esta permissão a biblioteca só enxerga o que o próprio " +
                                "app baixou. Com ela, um MP3 que você jogar em " +
                                "${store.folderLabel()} pelo gerenciador de arquivos " +
                                "também aparece aqui.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { requestPermission.launch(mediaPermission) }) {
                            Text("Permitir")
                        }
                    }
                }
            }

            if (files.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.LibraryMusic,
                    title = "Nenhum áudio ainda",
                    body = "Cole o link de um vídeo ou de um arquivo e ele vem parar em " +
                        "${store.folderLabel()}. Depois é só recortar o trecho e usar " +
                        "no despertador.",
                )
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(files, key = { it.uri.toString() }) { file ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { togglePlay(file) }) {
                                Icon(
                                    if (playing == file.uri) Icons.Filled.Stop
                                    else Icons.Filled.PlayArrow,
                                    contentDescription = if (playing == file.uri) {
                                        "Parar"
                                    } else {
                                        "Ouvir"
                                    },
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(file.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    buildString {
                                        if (file.durationMs > 0) {
                                            append(formatClipPosition(file.durationMs))
                                            append("  ·  ")
                                        }
                                        append("${file.sizeBytes / 1024} KB")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onTrim(file.uri.toString()) }) {
                                Icon(Icons.Filled.ContentCut, contentDescription = "Recortar")
                            }
                            IconButton(onClick = { deleting = file }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Excluir")
                            }
                        }
                    }
                }
            }
        }
    }

    if (importing) {
        AudioImportDialog(
            onDismiss = { importing = false },
            onDone = { importing = false; reloadKey++ },
        )
    }

    deleting?.let { file ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir áudio?") },
            text = {
                Text(
                    "\"${file.displayName}\" some de ${store.folderLabel()}. " +
                        "Despertadores que usam este som voltam ao padrão do sistema.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        store.delete(file.uri)
                        deleting = null
                        reloadKey++
                    }
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}
