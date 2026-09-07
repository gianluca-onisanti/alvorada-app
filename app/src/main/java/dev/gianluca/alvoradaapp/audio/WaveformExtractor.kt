package dev.gianluca.alvoradaapp.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min

/**
 * Reduz um arquivo de áudio a N picos de amplitude, para desenhar a onda.
 *
 * Existe por causa de uma frase do pedido: cortar "com boa precisão". Numa barra de
 * rolagem lisa não dá para achar onde a batida entra — você arrasta, ouve, erra, e
 * repete. Com a onda desenhada o ponto certo é **visível**, e as alças caem nele de
 * primeira.
 *
 * Decodifica de verdade para PCM com `MediaExtractor` + `MediaCodec`, porque não
 * existe atalho: nem o MediaStore nem o Media3 expõem envelope de amplitude. É o
 * trecho mais pesado do app, e por isso roda uma vez por arquivo e fica em cache.
 */
class WaveformExtractor(private val context: Context) {

    private val cache = mutableMapOf<String, FloatArray>()

    /**
     * Devolve [buckets] valores entre 0 e 1. Lista vazia quando o arquivo não dá para
     * decodificar — a tela então cai numa barra lisa, que é pior mas não quebra nada.
     */
    suspend fun peaks(uri: Uri, buckets: Int = DEFAULT_BUCKETS): FloatArray =
        withContext(Dispatchers.IO) {
            cache[uri.toString()]?.let { return@withContext it }
            val result = runCatching { decode(uri, buckets) }
                .onFailure { Log.w(TAG, "Não consegui desenhar a onda de $uri", it) }
                .getOrDefault(FloatArray(0))
            if (result.isNotEmpty()) cache[uri.toString()] = result
            result
        }

    private fun decode(uri: Uri, buckets: Int): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: run {
            extractor.release()
            return FloatArray(0)
        }

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val durationUs = runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        // Acumula por balde em vez de guardar o PCM inteiro: uma música de 4 minutos
        // são ~40 MB de amostras, e o que a tela precisa são algumas centenas de
        // números.
        val sums = FloatArray(buckets)
        val counts = IntArray(buckets)

        val info = MediaCodec.BufferInfo()
        var sawInputEnd = false
        var sawOutputEnd = false

        try {
            while (!sawOutputEnd) {
                if (!sawInputEnd) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            sawInputEnd = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex >= 0) {
                    if (info.size > 0 && durationUs > 0) {
                        val buffer = codec.getOutputBuffer(outIndex)!!
                        val bucket = ((info.presentationTimeUs.toDouble() / durationUs) * buckets)
                            .toInt()
                            .coerceIn(0, buckets - 1)
                        accumulate(buffer, info, sums, counts, bucket)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEnd = true
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }

        val peaks = FloatArray(buckets) { i ->
            if (counts[i] == 0) 0f else sums[i] / counts[i]
        }
        // Normaliza pelo pico: uma gravação baixinha desenharia uma linha quase reta,
        // que não ajuda ninguém a achar o ponto de corte.
        val max = peaks.maxOrNull() ?: 0f
        return if (max <= 0f) peaks else FloatArray(buckets) { peaks[it] / max }
    }

    private fun accumulate(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        sums: FloatArray,
        counts: IntArray,
        bucket: Int,
    ) {
        val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val samples = min(shorts.remaining(), info.size / 2)
        if (samples <= 0) return

        var sum = 0f
        // Amostra esparsa: a média de 1 em cada 16 amostras dá a mesma silhueta e
        // decodifica visivelmente mais rápido num arquivo longo.
        var i = 0
        var taken = 0
        while (i < samples) {
            sum += abs(shorts.get(i).toFloat()) / Short.MAX_VALUE
            taken++
            i += SAMPLE_STRIDE
        }
        if (taken == 0) return
        sums[bucket] += sum / taken
        counts[bucket]++
    }

    private companion object {
        const val TAG = "WaveformExtractor"
        const val DEFAULT_BUCKETS = 240
        const val TIMEOUT_US = 10_000L
        const val SAMPLE_STRIDE = 16
    }
}
