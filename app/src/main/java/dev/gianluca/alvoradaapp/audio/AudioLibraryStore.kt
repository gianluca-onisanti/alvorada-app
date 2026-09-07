package dev.gianluca.alvoradaapp.audio

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/** Um arquivo da pasta, como o MediaStore o conhece. */
data class LibraryFile(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val addedAt: Long,
)

/**
 * Dono de `Music/Alvorada/`.
 *
 * A V1 copiava todo som escolhido para `filesDir`, porque uma URI do SAF depende de
 * permissão revogável e de um arquivo que pode ser movido. A V2 troca esse risco por
 * outro, de propósito: numa pasta pública o arquivo fica visível e o usuário (ou um
 * app de limpeza) pode apagá-lo — mas **sobrevive à desinstalação**, dá para alimentar
 * a pasta pelo gerenciador de arquivos, e o caminho passa a ser estável entre
 * instalações, o que conserta de graça a re-ancoragem de som próprio no backup.
 *
 * A rede de segurança contra o arquivo sumir já existia: `AlarmSoundPlayer` cai no som
 * padrão do sistema em qualquer falha, e a lista de despertadores marca quem perdeu o
 * áudio.
 *
 * Espelha o padrão de `EvidenceStore` — método-fábrica que devolve para onde escrever
 * em vez de escrever ele mesmo, `delete` que engole exceção, e o próprio
 * armazenamento como índice — adaptado ao MediaStore.
 */
class AudioLibraryStore(private val context: Context) {

    private val collection: Uri
        get() = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /**
     * Cria a entrada e devolve a URI para escrever nela.
     *
     * `IS_PENDING = 1` esconde o arquivo dos outros apps enquanto o download corre:
     * sem isso um player de música acharia um arquivo truncado no meio do caminho.
     * Quem escreve precisa chamar [publish] no fim.
     */
    suspend fun createPending(displayName: String, mimeType: String): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayName.sanitized())
                    put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                context.contentResolver.insert(collection, values)
            }.onFailure { Log.e(TAG, "Não consegui criar $displayName", it) }.getOrNull()
        }

    fun openOutput(uri: Uri): OutputStream? =
        runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()

    /** Torna o arquivo visível para o resto do sistema. */
    suspend fun publish(uri: Uri) = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
                null,
                null,
            )
        }.onFailure { Log.e(TAG, "Não consegui publicar $uri", it) }
        Unit
    }

    /**
     * Tudo que está em `Music/Alvorada/`, mais recente primeiro.
     *
     * Consulta o MediaStore em vez do banco do app de propósito: um MP3 largado na
     * pasta pelo gerenciador de arquivos precisa aparecer aqui, e ele nunca passou
     * por nenhuma tabela nossa. Ler arquivo de outro app é o que exige a permissão de
     * mídia em runtime.
     */
    suspend fun list(): List<LibraryFile> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("$RELATIVE_PATH%"),
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        add(
                            LibraryFile(
                                uri = ContentUris.withAppendedId(collection, id),
                                displayName = cursor.getString(nameCol) ?: "sem nome",
                                durationMs = cursor.getLong(durCol),
                                sizeBytes = cursor.getLong(sizeCol),
                                // DATE_ADDED vem em segundos, não em millis.
                                addedAt = cursor.getLong(dateCol) * 1000L,
                            )
                        )
                    }
                }
            }.orEmpty()
        }.onFailure { Log.e(TAG, "Não consegui listar a biblioteca", it) }
            .getOrDefault(emptyList())
    }

    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.delete(uri, null, null) > 0 }
            .getOrDefault(false)
    }

    suspend fun rename(uri: Uri, displayName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayName.sanitized())
                },
                null,
                null,
            ) > 0
        }.getOrDefault(false)
    }

    /** O caminho legível, para dizer ao usuário onde os arquivos estão. */
    fun folderLabel(): String = "${Environment.DIRECTORY_MUSIC}/$FOLDER"

    private fun String.sanitized(): String = sanitizeName(this)

    companion object {
        const val FOLDER = "Alvorada"
        val RELATIVE_PATH = "${Environment.DIRECTORY_MUSIC}/$FOLDER/"
        private const val TAG = "AudioLibraryStore"

        /**
         * O nome que o arquivo vai ter de verdade.
         *
         * Exposto porque quem registra a procedência precisa gravar o mesmo nome
         * que o MediaStore vai devolver depois — senão a linhagem mostraria um
         * nome de original que não bate com nenhuma linha da lista.
         */
        fun sanitizeName(raw: String): String =
            raw.replace(Regex("""[\\/:*?"<>|]"""), "_").take(120).ifBlank { "audio" }
    }
}
