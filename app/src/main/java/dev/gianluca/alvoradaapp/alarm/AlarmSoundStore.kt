package dev.gianluca.alvoradaapp.alarm

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import dev.gianluca.alvoradaapp.audio.AudioLibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Um som escolhido pelo usuário, pronto para ser gravado no `AlarmEntity`. */
data class ChosenSound(
    val uri: String?,
    val isSystem: Boolean,
    val label: String,
)

/**
 * Resolve nomes de som e importa arquivos do usuário.
 *
 * Arquivos escolhidos pelo seletor de documentos são **copiados** para o storage do
 * app em vez de referenciados por `content://`. Uma URI do SAF depende de uma permissão
 * que o sistema pode revogar, e o arquivo original pode ser movido ou apagado — em
 * qualquer um dos casos o despertador acordaria mudo. Copiar custa alguns megabytes e
 * elimina a classe inteira de falha.
 */
class AlarmSoundStore(
    private val context: Context,
    private val library: AudioLibraryStore,
) {

    private val soundsDir: File
        get() = File(context.filesDir, "sounds").apply { mkdirs() }

    fun systemDefault(): ChosenSound = ChosenSound(
        uri = null,
        isSystem = true,
        label = "Padrão do sistema",
    )

    /** URI de referência para abrir o seletor de toques já na escolha atual. */
    fun defaultAlarmUri(): Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    fun fromSystemPicker(uri: Uri?): ChosenSound {
        if (uri == null) return systemDefault()
        val title = runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull()
        return ChosenSound(uri.toString(), isSystem = true, label = title ?: "Som do sistema")
    }

    /**
     * Copia o arquivo escolhido para a biblioteca pública. Retorna `null` se a leitura
     * falhar — melhor manter o som anterior do que gravar uma referência quebrada.
     *
     * Na V1 a cópia ia para `filesDir/sounds/`, invisível e apagada na
     * desinstalação. Agora vai para o mesmo `Music/Alvorada/` dos áudios baixados:
     * um lugar só para tudo que toca, visível no gerenciador de arquivos, com um
     * caminho estável entre instalações — o que conserta de graça a re-ancoragem de
     * som próprio no restauro de backup.
     */
    suspend fun importUserSound(source: Uri): ChosenSound? = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(source) ?: "som"
        val mime = context.contentResolver.getType(source) ?: "audio/mpeg"

        val target = library.createPending(displayName, mime) ?: return@withContext null

        val ok = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                library.openOutput(target)?.use { output -> input.copyTo(output) }
                    ?: error("Não foi possível escrever em $target")
            } ?: error("Não foi possível abrir $source")
        }.onFailure { Log.e(TAG, "Falha ao importar som de $source", it) }.isSuccess

        if (!ok) {
            library.delete(target)
            return@withContext null
        }
        library.publish(target)

        ChosenSound(uri = target.toString(), isSystem = false, label = displayName)
    }

    /**
     * Move os sons da V1 para a biblioteca pública, uma vez.
     *
     * Os `file://` antigos continuavam tocando — `filesDir/sounds/` não sumiu e o
     * `AlarmSoundPlayer` sempre aceitou esse esquema. A migração existe por outro
     * motivo: sem ela os sons já configurados ficariam de fora da tela de Áudios,
     * invisíveis e ainda condenados a sumir na próxima desinstalação.
     *
     * Devolve quantos foram movidos. Idempotente: quando a pasta antiga esvazia, não
     * há mais nada a fazer.
     */
    suspend fun migrateLegacySounds(rewrite: suspend (from: String, to: String) -> Unit): Int =
        withContext(Dispatchers.IO) {
            val legacy = soundsDir.listFiles()?.filter { it.isFile } ?: return@withContext 0
            var moved = 0

            for (file in legacy) {
                val target = library.createPending(file.name, "audio/mpeg") ?: continue
                val ok = runCatching {
                    library.openOutput(target)?.use { output ->
                        file.inputStream().use { it.copyTo(output) }
                    } ?: error("Não foi possível escrever em $target")
                }.onFailure { Log.e(TAG, "Falha ao migrar ${file.name}", it) }.isSuccess

                if (!ok) {
                    library.delete(target)
                    continue
                }
                library.publish(target)
                // Só apaga o original depois que o novo está publicado: uma queda no
                // meio do caminho deixa uma cópia a mais, nunca zero.
                rewrite(Uri.fromFile(file).toString(), target.toString())
                file.delete()
                moved++
            }

            if (moved > 0) Log.i(TAG, "$moved som(ns) migrado(s) para a biblioteca")
            moved
        }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()

    private fun String.sanitized(): String = replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)

    private companion object {
        const val TAG = "AlarmSoundStore"
    }
}
