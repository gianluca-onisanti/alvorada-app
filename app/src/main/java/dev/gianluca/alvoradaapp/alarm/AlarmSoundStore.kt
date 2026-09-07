package dev.gianluca.alvoradaapp.alarm

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
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
class AlarmSoundStore(private val context: Context) {

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
     * Copia o arquivo escolhido para o storage do app. Retorna `null` se a leitura
     * falhar — melhor manter o som anterior do que gravar uma referência quebrada.
     */
    suspend fun importUserSound(source: Uri): ChosenSound? = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(source) ?: "som"
        val target = File(soundsDir, "${System.currentTimeMillis()}_${displayName.sanitized()}")

        val ok = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Não foi possível abrir $source")
        }.onFailure { Log.e(TAG, "Falha ao importar som de $source", it) }.isSuccess

        if (!ok) {
            target.delete()
            return@withContext null
        }

        ChosenSound(
            uri = Uri.fromFile(target).toString(),
            isSystem = false,
            label = displayName,
        )
    }

    /** Remove um som importado que não é mais usado por nenhum despertador. */
    fun deleteImported(uriString: String?) {
        val uri = uriString?.let(Uri::parse) ?: return
        if (uri.scheme != "file") return
        val file = uri.path?.let(::File) ?: return
        if (file.parentFile == soundsDir && file.exists()) file.delete()
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
