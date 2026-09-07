package dev.gianluca.alvoradaapp.data

import android.util.Log
import dev.gianluca.alvoradaapp.audio.AudioLibraryStore

/**
 * A procedência de um arquivo, pronta para a tela.
 *
 * [parentUri] vem separado do nome porque é ele que habilita a ação: com o original
 * ainda na pasta, recortar de novo pode sair da fonte em vez de sair do corte.
 */
data class ClipOrigin(
    val clip: AudioClipEntity,
    val parentName: String?,
    val parentUri: String?,
) {
    val isTrim: Boolean get() = clip.trimStartMs != null && clip.trimEndMs != null
    val canRetrimFromOriginal: Boolean get() = isTrim && parentUri != null
}

/**
 * De onde cada áudio da biblioteca veio.
 *
 * Escrita em dois pontos só — o download e o recorte — porque são os dois únicos
 * momentos em que o app **sabe** de algo que o arquivo não conta sobre si. Tudo o mais
 * que aparece na biblioteca (um MP3 largado na pasta, um som importado do seletor)
 * segue sem registro, e a tela simplesmente não mostra origem para ele.
 *
 * Não é dona de nada: o MediaStore continua sendo quem sabe quais arquivos existem.
 * Por isso [catalog] poda o que sumiu em vez de confiar no que está guardado.
 */
class AudioClipRepository(db: AlvoradaDatabase) {

    private val dao = db.audioClipDao()

    /** Registra um arquivo recém-baixado, com o link que o originou. */
    suspend fun recordDownload(
        uri: String,
        displayName: String,
        durationMs: Long,
        sourceUrl: String,
        sourceTitle: String,
    ) {
        insert(
            AudioClipEntity(
                mediaStoreUri = uri,
                displayName = displayName,
                relativePath = AudioLibraryStore.RELATIVE_PATH,
                durationMs = durationMs,
                sourceUrl = sourceUrl,
                sourceTitle = sourceTitle,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Registra um corte e o amarra ao original.
     *
     * Cataloga o original na hora, se ele ainda não tiver registro. Sem isso, cortar
     * um MP3 largado na pasta na mão perderia a linhagem — justamente o caso em que
     * ela é mais útil, porque não há link de download para rebaixar.
     *
     * A origem do original é herdada: um corte de um vídeo do YouTube continua sendo
     * daquele vídeo, e perguntar "de onde isto veio?" tem que responder o link, não
     * "de um arquivo aqui do lado".
     */
    suspend fun recordTrim(
        uri: String,
        displayName: String,
        sourceUri: String,
        sourceName: String,
        sourceDurationMs: Long,
        startMs: Long,
        endMs: Long,
    ) {
        val parent = ensureRow(sourceUri, sourceName, sourceDurationMs)
        insert(
            AudioClipEntity(
                mediaStoreUri = uri,
                displayName = displayName,
                relativePath = AudioLibraryStore.RELATIVE_PATH,
                durationMs = endMs - startMs,
                sourceUrl = parent?.sourceUrl,
                sourceTitle = parent?.sourceTitle,
                parentClipId = parent?.id,
                trimStartMs = startMs,
                trimEndMs = endMs,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun originOf(uri: String): ClipOrigin? = dao.byUri(uri)?.let { resolve(it) }

    /**
     * A origem de cada arquivo vivo, indexada pela URI.
     *
     * Poda o resto de passagem: apagar um áudio pelo gerenciador de arquivos é normal
     * e não passa pelo app, então a tabela só se mantém honesta se cada leitura
     * conferir contra a lista real. A guarda do vazio é o que impede uma consulta que
     * falhou — permissão negada devolve lista vazia, não erro — de limpar tudo.
     */
    suspend fun catalog(aliveUris: List<String>): Map<String, ClipOrigin> {
        if (aliveUris.isEmpty()) return emptyMap()
        runCatching { dao.pruneMissing(aliveUris) }
            .onFailure { Log.w(TAG, "Não consegui podar o catálogo", it) }

        val alive = aliveUris.toSet()
        val rows = dao.all()
        val byId = rows.associateBy { it.id }
        return rows
            // Redundante depois de uma poda bem-sucedida, e necessário quando ela
            // falha: melhor não mostrar origem do que mostrar a de um arquivo morto.
            .filter { it.mediaStoreUri in alive }
            .associate { clip ->
                val parent = clip.parentClipId?.let { byId[it] }
                clip.mediaStoreUri to ClipOrigin(
                    clip = clip,
                    parentName = parent?.displayName,
                    parentUri = parent?.mediaStoreUri,
                )
            }
    }

    /** Esquece um arquivo apagado pela própria tela. */
    suspend fun forget(uri: String) {
        runCatching { dao.deleteByUri(uri) }
            .onFailure { Log.w(TAG, "Não consegui esquecer $uri", it) }
    }

    private suspend fun resolve(clip: AudioClipEntity): ClipOrigin {
        val parent = clip.parentClipId?.let { dao.byId(it) }
        return ClipOrigin(clip, parent?.displayName, parent?.mediaStoreUri)
    }

    private suspend fun ensureRow(
        uri: String,
        displayName: String,
        durationMs: Long,
    ): AudioClipEntity? {
        dao.byUri(uri)?.let { return it }
        insert(
            AudioClipEntity(
                mediaStoreUri = uri,
                displayName = displayName,
                relativePath = AudioLibraryStore.RELATIVE_PATH,
                durationMs = durationMs,
                createdAt = System.currentTimeMillis(),
            )
        )
        return dao.byUri(uri)
    }

    /**
     * Nunca deixa uma falha de catálogo derrubar a operação que a chamou.
     *
     * Um download que baixou e um corte que gravou terminaram: o arquivo está lá. Não
     * conseguir anotar de onde ele veio é uma perda pequena, e transformá-la em erro
     * faria a tela dizer que falhou algo que deu certo.
     */
    private suspend fun insert(clip: AudioClipEntity) {
        runCatching { dao.insert(clip) }
            .onFailure { Log.w(TAG, "Não consegui registrar ${clip.displayName}", it) }
    }

    private companion object {
        const val TAG = "AudioClipRepository"
    }
}
