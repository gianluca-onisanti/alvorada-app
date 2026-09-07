package dev.gianluca.alvoradaapp.audio

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.R
import dev.gianluca.alvoradaapp.alarm.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Baixa um áudio já resolvido para `Music/Alvorada/`.
 *
 * Roda no `WorkManager`, e não numa corrotina da tela, porque um download não pode
 * morrer porque o usuário trocou de app no meio — que é justamente o que acontece
 * quando se espera um arquivo grande numa rede ruim.
 *
 * Escreve direto na entrada pendente do MediaStore: o arquivo só fica visível para o
 * resto do sistema quando termina, então nenhum player encontra um arquivo pela
 * metade.
 */
class AudioDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val streamUrl = inputData.getString(KEY_STREAM_URL) ?: return@withContext Result.failure()
        val name = inputData.getString(KEY_NAME) ?: "audio"
        val mime = inputData.getString(KEY_MIME) ?: "audio/mp4"
        val sourceUrl = inputData.getString(KEY_SOURCE_URL).orEmpty()
        val sourceTitle = inputData.getString(KEY_SOURCE_TITLE).orEmpty()
        val durationMs = inputData.getLong(KEY_DURATION_MS, 0L)

        val container = (applicationContext as AlvoradaApp).container
        val store = container.audioLibraryStore

        setForeground(foregroundInfo(name, 0))

        val target = runCatching { store.createPending(name, mime) }
            .getOrElse { failure ->
                Log.e(TAG, "Não consegui criar a entrada de $name", failure)
                return@withContext Result.failure(
                    error(failure.message ?: "Não consegui criar o arquivo.")
                )
            }

        val result = runCatching { download(streamUrl, target, store, name) }

        result.fold(
            onSuccess = {
                store.publish(target)
                // Depois de publicar, e não antes: o registro fala de um arquivo que
                // existe. Um catálogo apontando para uma entrada pendente que a linha
                // seguinte poderia apagar seria uma mentira barata de evitar.
                container.audioClipRepository.recordDownload(
                    uri = target.toString(),
                    // O nome saneado, que é o que o MediaStore realmente gravou.
                    displayName = AudioLibraryStore.sanitizeName(name),
                    durationMs = durationMs,
                    sourceUrl = sourceUrl,
                    sourceTitle = sourceTitle,
                )
                Log.i(TAG, "Baixado: $name")
                Result.success(workDataOf(KEY_RESULT_URI to target.toString()))
            },
            onFailure = { failure ->
                // Um arquivo pendente e vazio ficaria invisível para sempre, ocupando
                // espaço que ninguém consegue achar para apagar.
                store.delete(target)
                Log.e(TAG, "Download falhou: $name", failure)
                Result.failure(error(failure.message ?: "O download falhou."))
            },
        )
    }

    private suspend fun download(
        streamUrl: String,
        target: Uri,
        store: AudioLibraryStore,
        name: String,
    ) {
        val client: OkHttpClient = MediaResolvers.defaultClient()
        val response = client.newCall(Request.Builder().url(streamUrl).build()).execute()

        response.use {
            if (!it.isSuccessful) throw IllegalStateException("O servidor respondeu ${it.code}.")
            val body = it.body ?: throw IllegalStateException("A resposta veio vazia.")
            val total = body.contentLength()

            val output = store.openOutput(target)
                ?: throw IllegalStateException("Não consegui abrir o arquivo para escrita.")

            output.use { sink ->
                body.byteStream().use { source ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    var lastReported = -1

                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        copied += read

                        if (total > 0) {
                            val percent = ((copied * 100) / total).toInt()
                            // Só quando muda: `setProgress` e `setForeground` são IPC,
                            // e chamá-los a cada bloco de 8 KB custaria mais que o
                            // próprio download.
                            if (percent != lastReported) {
                                lastReported = percent
                                setProgress(workDataOf(KEY_PROGRESS to percent))
                                setForeground(foregroundInfo(name, percent))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun error(message: String): Data = workDataOf(KEY_ERROR to message)

    private fun foregroundInfo(name: String, percent: Int): ForegroundInfo {
        val notification: Notification =
            Notification.Builder(applicationContext, Notifications.CHANNEL_DOWNLOAD)
                .setContentTitle("Baixando áudio")
                .setContentText(name)
                .setSmallIcon(R.drawable.ic_alarm)
                .setProgress(100, percent, percent == 0)
                .setOngoing(true)
                .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_STREAM_URL = "streamUrl"
        const val KEY_NAME = "name"
        const val KEY_MIME = "mime"
        const val KEY_SOURCE_URL = "sourceUrl"
        const val KEY_SOURCE_TITLE = "sourceTitle"
        const val KEY_DURATION_MS = "durationMs"
        const val KEY_PROGRESS = "progress"
        const val KEY_RESULT_URI = "resultUri"
        const val KEY_ERROR = "error"

        private const val TAG = "AudioDownloadWorker"
        private const val NOTIFICATION_ID = 4801

        /** Nome único para acompanhar o progresso de fora sem guardar o id. */
        const val WORK_NAME = "audio-download"

        fun enqueue(context: Context, audio: RemoteAudio, fileName: String) {
            val request = OneTimeWorkRequestBuilder<AudioDownloadWorker>()
                .setInputData(
                    workDataOf(
                        KEY_STREAM_URL to audio.streamUrl,
                        KEY_NAME to fileName,
                        KEY_MIME to audio.mimeType,
                        KEY_SOURCE_URL to audio.sourceUrl,
                        KEY_SOURCE_TITLE to audio.title,
                        KEY_DURATION_MS to audio.durationMs,
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
