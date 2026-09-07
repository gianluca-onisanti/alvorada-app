package dev.gianluca.alvoradaapp.audio

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

/** Um áudio remoto, já resolvido e pronto para baixar. */
data class RemoteAudio(
    val streamUrl: String,
    val title: String,
    val durationMs: Long,
    val mimeType: String,
    val bitrateKbps: Int?,
    /** A URL que o usuário colou. Guardada para saber de onde o arquivo veio. */
    val sourceUrl: String,
) {
    /** A extensão que o arquivo deve levar, deduzida do tipo. */
    val extension: String
        get() = when {
            mimeType.contains("mp4") || mimeType.contains("m4a") || mimeType.contains("aac") -> "m4a"
            mimeType.contains("webm") || mimeType.contains("opus") -> "webm"
            mimeType.contains("mpeg") || mimeType.contains("mp3") -> "mp3"
            mimeType.contains("ogg") -> "ogg"
            mimeType.contains("wav") -> "wav"
            else -> "m4a"
        }
}

/** Falha legível para mostrar na tela. */
class ResolveException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Descobre o que baixar a partir de uma URL colada.
 *
 * A interface existe para isolar a única peça deste app que **vai** quebrar sozinha:
 * um extrator de YouTube depende do formato do player, e o YouTube muda o player sem
 * avisar. Atrás da interface, quando isso acontece, o que quebra é um resolvedor — e
 * o [DirectUrlResolver] continua funcionando como saída manual.
 */
interface MediaResolver {
    fun handles(url: String): Boolean
    suspend fun resolve(url: String): RemoteAudio
}

/**
 * Resolve URLs diretas de arquivo — um MP3 num servidor qualquer.
 *
 * Além de ser útil por si, é a **saída de emergência** do app: quando o extrator do
 * YouTube parar de funcionar, colar a URL direta do arquivo continua trazendo o áudio
 * para dentro sem depender de atualização nenhuma.
 */
class DirectUrlResolver(private val client: OkHttpClient) : MediaResolver {

    override fun handles(url: String): Boolean = url.startsWith("http", ignoreCase = true)

    override suspend fun resolve(url: String): RemoteAudio = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).head().build()
        val response = runCatching { client.newCall(request).execute() }
            .getOrElse { throw ResolveException("Não consegui alcançar esse endereço.", it) }

        response.use {
            if (!it.isSuccessful) {
                throw ResolveException("O servidor respondeu ${it.code} para esse endereço.")
            }
            val type = it.header("Content-Type").orEmpty().substringBefore(';').trim()
            if (!type.startsWith("audio/") && !type.startsWith("video/")) {
                throw ResolveException(
                    "Esse endereço não devolve um arquivo de áudio (veio \"$type\").",
                )
            }
            RemoteAudio(
                streamUrl = url,
                title = url.substringAfterLast('/').substringBefore('?').ifBlank { "audio" },
                durationMs = 0L,
                mimeType = type,
                bitrateKbps = null,
                sourceUrl = url,
            )
        }
    }
}

/**
 * Resolve o stream de áudio de um vídeo do YouTube, via NewPipeExtractor.
 *
 * Escolhe o stream **só de áudio** de maior bitrate. Isso evita baixar o vídeo inteiro
 * para jogar a imagem fora, e o formato que vem — m4a/AAC ou webm/Opus — o
 * `MediaPlayer` toca direto desde a API 21, então não há transcodificação nenhuma no
 * caminho: o arquivo entra na pasta com a qualidade que saiu.
 *
 * Ressalva registrada e conhecida: baixar do YouTube contraria os Termos de Serviço
 * dele. Num app pessoal distribuído por APK isso não impede nada, mas fecha a porta
 * da Play Store enquanto este código existir.
 */
class YouTubeResolver(private val client: OkHttpClient) : MediaResolver {

    override fun handles(url: String): Boolean =
        YOUTUBE_HOSTS.any { url.contains(it, ignoreCase = true) }

    override suspend fun resolve(url: String): RemoteAudio = withContext(Dispatchers.IO) {
        ensureInitialized()

        val info = runCatching { StreamInfo.getInfo(ServiceList.YouTube, url) }
            .getOrElse {
                Log.e(TAG, "Extração falhou para $url", it)
                throw ResolveException(
                    "Não consegui ler esse link do YouTube. Isso costuma acontecer " +
                        "quando o YouTube muda o player. Tente colar a URL direta do " +
                        "arquivo de áudio.",
                    it,
                )
            }

        val candidates = info.audioStreams?.filterNotNull().orEmpty()
        if (candidates.isEmpty()) {
            throw ResolveException("Esse vídeo não expôs nenhuma faixa só de áudio.")
        }

        val best = candidates
            .mapNotNull { stream ->
                val mime = stream.format?.mimeType ?: return@mapNotNull null
                val rank = STORABLE_MIMES.indexOf(mime)
                if (rank < 0) null else Scored(stream, rank)
            }
            // Formato primeiro, bitrate depois.
            .minWithOrNull(compareBy({ it.rank }, { -it.stream.averageBitrate }))
            ?.stream
            ?: throw ResolveException(
                "Esse vídeo só oferece áudio em formatos que a pasta de músicas do " +
                    "Android não aceita. Tente outro vídeo.",
            )

        RemoteAudio(
            streamUrl = best.content ?: throw ResolveException("A faixa de áudio veio vazia."),
            title = info.name ?: "audio",
            durationMs = info.duration * 1000L,
            mimeType = best.format?.mimeType ?: "audio/mp4",
            bitrateKbps = best.averageBitrate.takeIf { it > 0 },
            sourceUrl = url,
        )
    }

    /**
     * O NewPipe precisa de um `Downloader` próprio, e de ser inicializado uma vez por
     * processo. Fazer isso preguiçosamente aqui evita pagar o custo em quem nunca
     * cola um link do YouTube.
     */
    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(OkHttpDownloader(client))
            initialized = true
        }
    }

    private class Scored(val stream: AudioStream, val rank: Int)

    private companion object {
        const val TAG = "YouTubeResolver"
        @Volatile var initialized = false
        val YOUTUBE_HOSTS = listOf("youtube.com", "youtu.be", "youtube-nocookie.com")

        /**
         * Os formatos que o MediaStore aceita na coleção de áudio, em ordem de
         * preferência.
         *
         * A escolha era pelo **maior bitrate**, e no YouTube essa faixa é quase sempre
         * Opus dentro de WebM — exatamente o formato que a pasta de músicas recusa,
         * com `IllegalArgumentException: Unsupported MIME type audio/webm`. O download
         * baixava e morria na hora de gravar, dizendo só "não consegui criar o
         * arquivo".
         *
         * Verificado no aparelho, inserindo cada tipo direto no provider: `audio/mp4`,
         * `audio/mpeg` e `audio/ogg` entram; `audio/webm` não. `audio/mp4` (m4a/AAC)
         * vem primeiro porque também é o que o `MediaPlayer` toca desde sempre.
         *
         * A diferença entre Opus a 160 kbps e AAC a 128 num despertador às 6h é
         * teórica. A diferença entre um arquivo que grava e um que não grava, não.
         */
        val STORABLE_MIMES = listOf("audio/mp4", "audio/mpeg", "audio/ogg")
    }
}

/** Ponte entre o `Downloader` do NewPipe e o OkHttp que o app já carrega. */
private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: NpRequest): NpResponse {
        val builder = Request.Builder().url(request.url())
        request.headers().forEach { (name, values) ->
            values.forEach { builder.addHeader(name, it) }
        }
        val body = request.dataToSend()?.toRequestBodyOrNull()
        builder.method(request.httpMethod(), body)

        client.newCall(builder.build()).execute().use { response ->
            return NpResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString(),
            )
        }
    }

    private fun ByteArray.toRequestBodyOrNull() = toRequestBody()
}

/**
 * A lista de resolvedores, na ordem em que respondem.
 *
 * O do YouTube vem primeiro porque é mais específico; o direto é o fallback que aceita
 * qualquer `http`.
 */
class MediaResolvers(client: OkHttpClient = defaultClient()) {

    private val resolvers = listOf(YouTubeResolver(client), DirectUrlResolver(client))

    suspend fun resolve(url: String): RemoteAudio {
        val trimmed = url.trim()
        if (trimmed.isBlank()) throw ResolveException("Cole um endereço primeiro.")
        val resolver = resolvers.firstOrNull { it.handles(trimmed) }
            ?: throw ResolveException("Não reconheci esse endereço. Ele começa com http?")
        return resolver.resolve(trimmed)
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
