package dev.gianluca.alvoradaapp.ui.audio

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.alarm.AlarmSoundPlayer
import dev.gianluca.alvoradaapp.audio.LibraryFile
import dev.gianluca.alvoradaapp.audio.formatClipPosition
import dev.gianluca.alvoradaapp.data.ClipOrigin
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
    var origins by remember { mutableStateOf<Map<String, ClipOrigin>>(emptyMap()) }
    var reloadKey by remember { mutableStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<LibraryFile?>(null) }
    var renaming by remember { mutableStateOf<LibraryFile?>(null) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<Uri?>(null) }
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

    LaunchedEffect(reloadKey) {
        files = store.list()
        // Depois da listagem, e a partir dela: a lista real é o que decide o que
        // continua existindo, e o catálogo é podado contra ela.
        origins = container.audioClipRepository.catalog(files.map { it.uri.toString() })
    }

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
                                // A origem só aparece quando existe. Um arquivo largado
                                // na pasta na mão não tem procedência a mostrar, e
                                // inventar uma linha genérica para ele seria ruído.
                                describeOrigin(origins[file.uri.toString()])?.let { origin ->
                                    Text(
                                        origin,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(onClick = { onTrim(file.uri.toString()) }) {
                                Icon(Icons.Filled.ContentCut, contentDescription = "Recortar")
                            }
                            // Recortar fica no ícone porque é a ação de todo dia;
                            // renomear e excluir entram no menu. Um quarto botão nesta
                            // linha comeria a largura do nome, que é o que se precisa
                            // ler para achar o arquivo.
                            Box {
                                IconButton(onClick = { menuFor = file.uri }) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "Mais ações",
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuFor == file.uri,
                                    onDismissRequest = { menuFor = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Renomear") },
                                        onClick = { menuFor = null; renaming = file },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Excluir") },
                                        onClick = { menuFor = null; deleting = file },
                                    )
                                }
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

    renaming?.let { file ->
        RenameDialog(
            file = file,
            error = renameError,
            onDismiss = { renaming = null; renameError = null },
            onConfirm = { newName ->
                scope.launch {
                    runCatching { store.rename(file.uri, newName) }
                        .onSuccess { saved ->
                            // O catálogo acompanha, senão a linhagem de um corte
                            // passaria a citar um nome de original que não existe.
                            container.audioClipRepository.rename(file.uri.toString(), saved)
                            renaming = null
                            renameError = null
                            reloadKey++
                        }
                        .onFailure { renameError = it.message ?: "Não consegui renomear." }
                }
            },
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
                        container.audioClipRepository.forget(file.uri.toString())
                        deleting = null
                        reloadKey++
                    }
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

/**
 * Trocar o nome de um arquivo.
 *
 * Só o nome: a extensão fica de fora do campo e é recolocada pelo store, porque o
 * MediaStore recusa um arquivo cuja extensão não combine com o tipo gravado — e
 * digitar ".mp3" num m4a é um erro fácil demais de cometer.
 */
@Composable
private fun RenameDialog(
    file: LibraryFile,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val extension = file.displayName.substringAfterLast('.', "")
    var name by remember(file.uri) {
        mutableStateOf(file.displayName.substringBeforeLast('.'))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renomear áudio") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    suffix = { if (extension.isNotBlank()) Text(".$extension") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Uma linha só sobre de onde o arquivo veio.
 *
 * A linhagem ganha da procedência quando as duas existem: saber que este é o recorte
 * de 0:12 a 0:31 de outro arquivo é o que responde "por que tenho dois parecidos?",
 * enquanto o link de origem os dois compartilham.
 */
private fun describeOrigin(origin: ClipOrigin?): String? {
    val clip = origin?.clip ?: return null
    val start = clip.trimStartMs
    val end = clip.trimEndMs

    if (start != null && end != null) {
        val of = origin.parentName?.let { " de \"$it\"" }.orEmpty()
        return "Corte$of · ${formatClipPosition(start)}–${formatClipPosition(end)}"
    }
    val title = clip.sourceTitle?.takeIf { it.isNotBlank() }
    val url = clip.sourceUrl?.takeIf { it.isNotBlank() } ?: return null
    return "Baixado de ${title ?: url}"
}
