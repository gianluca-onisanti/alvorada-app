package dev.gianluca.alvoradaapp.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Recorta um trecho de áudio para um arquivo novo.
 *
 * Usa o `Transformer` do Media3 com `ClippingConfiguration`, que transmuxa quando o
 * formato permite — ou seja, na maioria dos casos o trecho sai **sem reencodar**, com
 * a mesma qualidade do original. Foi o que dispensou FFmpeg nativo aqui.
 *
 * O original nunca é destruído: recortar cria um arquivo novo. Um corte é uma
 * operação, não uma edição — e errar o ponto de corte em cima do único arquivo que
 * você tem seria uma forma boba de perder um som que deu trabalho para achar.
 */
class AudioTrimmer(
    private val context: Context,
    private val store: AudioLibraryStore,
) {

    /**
     * Grava `[startMs, endMs)` de [source] como um arquivo novo na biblioteca.
     *
     * Devolve a URI do arquivo criado, ou lança com uma mensagem legível.
     */
    suspend fun trim(
        source: Uri,
        startMs: Long,
        endMs: Long,
        displayName: String,
    ): Uri = withContext(Dispatchers.IO) {
        require(endMs > startMs) { "O fim do corte precisa vir depois do começo." }

        // O Transformer escreve em caminho de arquivo, e não em `content://`. O
        // intermediário no cache resolve isso, e some no fim de qualquer jeito.
        val staging = File(context.cacheDir, "trim-${System.currentTimeMillis()}.m4a")

        try {
            runTransformer(source, startMs, endMs, staging)

            val target = store.createPending(displayName, "audio/mp4")
                ?: throw IllegalStateException("Não consegui criar o arquivo do corte.")

            val output = store.openOutput(target)
                ?: throw IllegalStateException("Não consegui abrir o corte para escrita.")
            output.use { sink -> staging.inputStream().use { it.copyTo(sink) } }
            store.publish(target)

            Log.i(TAG, "Corte gravado: $displayName ($startMs..$endMs)")
            target
        } finally {
            staging.delete()
        }
    }

    /**
     * O `Transformer` é assíncrono e só fala por callback, e precisa ser construído e
     * disparado na thread principal — daí o pulo para `Dispatchers.Main` no meio de
     * uma função que roda em IO.
     */
    private suspend fun runTransformer(
        source: Uri,
        startMs: Long,
        endMs: Long,
        output: File,
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val item = MediaItem.Builder()
                .setUri(source)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()

            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        continuation.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException,
                    ) {
                        continuation.resumeWithException(
                            IllegalStateException(
                                "Não consegui recortar esse arquivo: ${exception.message}",
                                exception,
                            )
                        )
                    }
                })
                .build()

            continuation.invokeOnCancellation { transformer.cancel() }
            transformer.start(item, output.absolutePath)
        }
    }

    private companion object {
        const val TAG = "AudioTrimmer"
    }
}

/** Formata um instante do áudio como `m:ss`, para nomear o corte e rotular as alças. */
fun formatClipPosition(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
