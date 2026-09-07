package dev.gianluca.alvoradaapp.ui.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import dev.gianluca.alvoradaapp.audio.AudioLibraryStore
import dev.gianluca.alvoradaapp.audio.formatClipPosition
import dev.gianluca.alvoradaapp.data.ClipOrigin
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
 *
 * Recortar um corte é o caminho que a tela desencoraja: cada passagem pelo
 * `Transformer` reencoda em cima do que já foi reencodado. Quando o arquivo aberto tem
 * um original catalogado e ele ainda está na pasta, a tela oferece trocar a fonte — o
 * segundo corte sai da mesma origem que o primeiro, e não em cima dele.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrimScreen(sourceUri: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    val scope = rememberCoroutineScope()
    // A fonte é estado, e não parâmetro: trocar para o original é o ponto inteiro do
    // atalho abaixo, e ele não deveria custar uma volta pela navegação.
    var activeUri by remember(sourceUri) { mutableStateOf(sourceUri.toUri()) }
    var origin by remember(sourceUri) { mutableStateOf<ClipOrigin?>(null) }

    var durationMs by remember { mutableStateOf(0L) }
    var peaks by remember { mutableStateOf(FloatArray(0)) }
    var loadingPeaks by remember { mutableStateOf(true) }
    var startMs by remember { mutableStateOf(0L) }
    var endMs by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var baseName by remember { mutableStateOf("audio") }
    /** O nome com extensão, que é como o MediaStore conhece o arquivo. */
    var sourceName by remember { mutableStateOf("audio") }
    var clipName by remember(sourceUri) { mutableStateOf("") }
    /** Enquanto falso, o nome acompanha as alças; o primeiro toque no campo o congela. */
    var nameEdited by remember(sourceUri) { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    LaunchedEffect(activeUri) {
        loadingPeaks = true
        val files = container.audioLibraryStore.list()
        val file = files.firstOrNull { it.uri == activeUri }
        durationMs = file?.durationMs ?: 0L
        sourceName = file?.displayName ?: "audio"
        baseName = sourceName.substringBeforeLast('.')
        startMs = 0L
        endMs = durationMs

        // A linhagem só vale enquanto o original estiver mesmo na pasta. A tabela pode
        // saber de um arquivo que o gerenciador já apagou — oferecer "recortar do
        // original" e falhar ao carregar seria pior do que não oferecer nada.
        val known = container.audioClipRepository.originOf(activeUri.toString())
        val parentAlive = known?.parentUri?.let { uri ->
            files.any { it.uri.toString() == uri }
        } == true
        origin = if (known != null && !parentAlive) {
            known.copy(parentName = null, parentUri = null)
        } else {
            known
        }

        peaks = container.waveformExtractor.peaks(activeUri)
        loadingPeaks = false
    }

    // Os dois-pontos de "0:12" viram "_" no nome do arquivo, então o sugerido usa
    // outra marca. É o nome que vai aparecer na lista pelos próximos meses.
    val suggestedName = "$baseName (corte ${formatClipPosition(startMs).replace(':', 'm')}" +
        "-${formatClipPosition(endMs).replace(':', 'm')})"
    LaunchedEffect(suggestedName, nameEdited) {
        if (!nameEdited) clipName = suggestedName
    }

    fun applyClip() {
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(activeUri)
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

            // Só aparece quando há de fato um original na pasta para voltar. Um aviso
            // sobre perda de qualidade sem a ação que a evita seria só má notícia.
            origin?.takeIf { it.canRetrimFromOriginal }?.let { lineage ->
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            player.stop()
                            playing = false
                            activeUri = lineage.parentUri!!.toUri()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Isto já é um corte de \"${lineage.parentName}\" · " +
                            "toque para recortar do original",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

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
            OutlinedTextField(
                value = clipName,
                onValueChange = { clipName = it; nameEdited = true },
                label = { Text("Salvar como") },
                suffix = { Text(".m4a") },
                enabled = !saving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !saving && clipName.isNotBlank() &&
                    endMs - startMs >= MIN_CLIP_MS,
                onClick = {
                    saving = true
                    error = null
                    player.stop()
                    playing = false
                    scope.launch {
                        val label = "${clipName.trim().ifBlank { suggestedName }}.m4a"
                        val from = activeUri
                        val fromName = sourceName
                        val fromDuration = durationMs
                        val cutStart = startMs
                        val cutEnd = endMs
                        runCatching {
                            container.audioTrimmer.trim(from, cutStart, cutEnd, label)
                        }
                            .onSuccess { target ->
                                // Depois do corte gravado, e sem poder derrubá-lo: o
                                // arquivo é o resultado, a linhagem é a anotação.
                                container.audioClipRepository.recordTrim(
                                    uri = target.toString(),
                                    displayName = AudioLibraryStore.sanitizeName(label),
                                    sourceUri = from.toString(),
                                    sourceName = fromName,
                                    sourceDurationMs = fromDuration,
                                    startMs = cutStart,
                                    endMs = cutEnd,
                                )
                                onDone()
                            }
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

/**
 * Os quatro passos de ajuste fino de uma alça.
 *
 * Os botões dividem a largura por peso, com o padding interno apertado. Com o padding
 * padrão do Material — 24dp de cada lado — quatro botões e o rótulo passavam de 350dp
 * e quebravam em duas linhas num aparelho comum; o rótulo tem largura fixa pelo mesmo
 * motivo, para não roubar espaço de quem precisa dele.
 */
@Composable
private fun NudgeRow(label: String, onNudge: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(52.dp),
        )
        NUDGES.forEach { (delta, text) ->
            OutlinedButton(
                onClick = { onNudge(delta) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

private val NUDGES = listOf(
    -1000L to "−1s",
    -100L to "−100ms",
    100L to "+100ms",
    1000L to "+1s",
)

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
