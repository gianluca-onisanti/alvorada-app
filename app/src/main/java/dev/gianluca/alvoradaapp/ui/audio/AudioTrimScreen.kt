package dev.gianluca.alvoradaapp.ui.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.audio.formatClipPosition
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * Recorta o trecho exato de um áudio.
 *
 * Três coisas fazem a precisão que o corte às cegas não dá: a **onda desenhada**, que
 * mostra onde a batida entra; os botões de **ajuste fino** de ±1s e ±100ms, porque
 * arrastar com o dedo não acerta décimo de segundo; e a **pré-escuta em loop** do
 * trecho selecionado, que é o que evita descobrir depois que o corte pegou o meio de
 * uma sílaba.
 *
 * A pré-escuta não grava nada: usa o `ClippingConfiguration` do ExoPlayer, que toca só
 * o intervalo. Só o botão de confirmar chama o `Transformer`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrimScreen(sourceUri: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    val scope = rememberCoroutineScope()
    val uri = remember(sourceUri) { sourceUri.toUri() }

    var durationMs by remember { mutableStateOf(0L) }
    var peaks by remember { mutableStateOf(FloatArray(0)) }
    var loadingPeaks by remember { mutableStateOf(true) }
    var startMs by remember { mutableStateOf(0L) }
    var endMs by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var baseName by remember { mutableStateOf("audio") }

    val player = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    LaunchedEffect(sourceUri) {
        val file = container.audioLibraryStore.list().firstOrNull { it.uri == uri }
        durationMs = file?.durationMs ?: 0L
        baseName = file?.displayName?.substringBeforeLast('.') ?: "audio"
        endMs = durationMs
        peaks = container.waveformExtractor.peaks(uri)
        loadingPeaks = false
    }

    fun applyClip() {
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()
        )
        player.prepare()
    }

    fun togglePlay() {
        if (playing) {
            player.stop()
            playing = false
        } else {
            applyClip()
            player.play()
            playing = true
        }
    }

    /** Move uma alça em [deltaMs], sem deixar o começo passar do fim. */
    fun nudge(isStart: Boolean, deltaMs: Long) {
        if (isStart) {
            startMs = (startMs + deltaMs).coerceIn(0L, endMs - MIN_CLIP_MS)
        } else {
            endMs = (endMs + deltaMs).coerceIn(startMs + MIN_CLIP_MS, durationMs)
        }
        if (playing) applyClip()
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Recortar") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(baseName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            if (loadingPeaks) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) { CircularProgressIndicator() }
            } else {
                Waveform(
                    peaks = peaks,
                    startFraction = if (durationMs == 0L) 0f else startMs.toFloat() / durationMs,
                    endFraction = if (durationMs == 0L) 1f else endMs.toFloat() / durationMs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            if (durationMs > 0) {
                RangeSlider(
                    value = startMs.toFloat()..endMs.toFloat(),
                    onValueChange = { range ->
                        startMs = range.start.roundToLong()
                        endMs = range.endInclusive.roundToLong()
                        if (playing) applyClip()
                    },
                    valueRange = 0f..durationMs.toFloat(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatClipPosition(startMs), style = MaterialTheme.typography.labelMedium)
                Text(
                    "trecho de ${formatClipPosition(endMs - startMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(formatClipPosition(endMs), style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(16.dp))
            NudgeRow("Começo", onNudge = { nudge(isStart = true, deltaMs = it) })
            Spacer(Modifier.height(8.dp))
            NudgeRow("Fim", onNudge = { nudge(isStart = false, deltaMs = it) })

            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = { togglePlay() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.height(0.dp))
                Text(if (playing) "  Parar" else "  Ouvir o trecho em loop")
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !saving && endMs - startMs >= MIN_CLIP_MS,
                onClick = {
                    saving = true
                    error = null
                    player.stop()
                    playing = false
                    scope.launch {
                        val label = "$baseName (corte ${formatClipPosition(startMs)}" +
                            "-${formatClipPosition(endMs)}).m4a"
                        runCatching {
                            container.audioTrimmer.trim(uri, startMs, endMs, label)
                        }
                            .onSuccess { onDone() }
                            .onFailure { error = it.message ?: "Não consegui recortar." }
                        saving = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Recortando…" else "Salvar corte") }

            Spacer(Modifier.height(8.dp))
            Text(
                "O arquivo original continua na biblioteca — o corte vira um arquivo novo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NudgeRow(label: String, onNudge: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onNudge(-1000) }) { Text("−1s") }
        OutlinedButton(onClick = { onNudge(-100) }) { Text("−100ms") }
        OutlinedButton(onClick = { onNudge(100) }) { Text("+100ms") }
        OutlinedButton(onClick = { onNudge(1000) }) { Text("+1s") }
    }
}

/**
 * A onda, com o trecho selecionado em destaque e o resto apagado.
 *
 * Desenhada num `Canvas` a partir dos picos já reduzidos: é uma barra por balde, com
 * a altura em torno do meio. Simples de propósito — o que se pede dela é mostrar onde
 * o som muda, não ser bonita.
 */
@Composable
private fun Waveform(
    peaks: FloatArray,
    startFraction: Float,
    endFraction: Float,
    modifier: Modifier = Modifier,
) {
    val selected = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Canvas(modifier) {
        if (peaks.isEmpty()) return@Canvas
        val barWidth = size.width / peaks.size
        val middle = size.height / 2f

        peaks.forEachIndexed { index, peak ->
            val fraction = index.toFloat() / peaks.size
            val inRange = fraction in startFraction..endFraction
            val half = (peak * middle).coerceAtLeast(1f)
            val x = index * barWidth

            drawLine(
                color = if (inRange) selected else muted,
                start = Offset(x, middle - half),
                end = Offset(x, middle + half),
                strokeWidth = barWidth * 0.7f,
            )
        }
    }
}

/** Menos que isto não é um som, é um clique. */
private const val MIN_CLIP_MS = 500L
