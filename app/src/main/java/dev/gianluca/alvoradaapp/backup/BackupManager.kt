package dev.gianluca.alvoradaapp.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.gianluca.alvoradaapp.data.AlarmEntity
import dev.gianluca.alvoradaapp.data.ChecklistCycleEntity
import dev.gianluca.alvoradaapp.data.ChecklistEntity
import dev.gianluca.alvoradaapp.data.ChecklistItemEntity
import dev.gianluca.alvoradaapp.data.ChecklistItemStateEntity
import dev.gianluca.alvoradaapp.data.EvidencePhotoEntity
import dev.gianluca.alvoradaapp.data.FolderEntity
import dev.gianluca.alvoradaapp.data.MissionEntity
import dev.gianluca.alvoradaapp.data.MissionInstanceEntity
import dev.gianluca.alvoradaapp.data.OccurrenceEntity
import dev.gianluca.alvoradaapp.data.PointsLedgerEntity
import dev.gianluca.alvoradaapp.data.RewardEntity
import dev.gianluca.alvoradaapp.data.RewardRedemptionEntity
import dev.gianluca.alvoradaapp.data.StreakStateEntity
import dev.gianluca.alvoradaapp.data.AlvoradaDatabase
import dev.gianluca.alvoradaapp.data.missionDayMask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Foto no backup, referenciada por caminho **relativo**.
 *
 * O `filePath` gravado no banco é absoluto e inclui o diretório privado do app, que
 * muda a cada instalação. Guardá-lo no backup produziria um arquivo que restaura
 * silenciosamente para caminhos inexistentes — a galeria voltaria vazia sem nenhum
 * erro. O caminho relativo (`2026-08-02/12_1754.jpg`) é reancorado na importação.
 */
@Serializable
data class BackupPhoto(
    val id: Long,
    val missionInstanceId: Long,
    val relativePath: String,
    val takenAt: Long,
    val sortOrder: Int,
)

@Serializable
data class BackupEnvelope(
    val formatVersion: Int,
    val exportedAt: Long,
    val folders: List<FolderEntity> = emptyList(),
    val alarms: List<AlarmEntity> = emptyList(),
    val missions: List<MissionEntity> = emptyList(),
    val occurrences: List<OccurrenceEntity> = emptyList(),
    val instances: List<MissionInstanceEntity> = emptyList(),
    val photos: List<BackupPhoto> = emptyList(),
    val points: List<PointsLedgerEntity> = emptyList(),
    val rewards: List<RewardEntity> = emptyList(),
    val redemptions: List<RewardRedemptionEntity> = emptyList(),
    val streak: StreakStateEntity? = null,
    val checklists: List<ChecklistEntity> = emptyList(),
    val checklistItems: List<ChecklistItemEntity> = emptyList(),
    val checklistCycles: List<ChecklistCycleEntity> = emptyList(),
    val checklistItemStates: List<ChecklistItemStateEntity> = emptyList(),
)

sealed interface BackupResult {
    data class Exported(val photoCount: Int, val alarmCount: Int) : BackupResult
    data class Imported(val photoCount: Int, val alarmCount: Int) : BackupResult
    data class Failed(val message: String) : BackupResult
}

/**
 * Export e restauração completos, num único ZIP.
 *
 * As fotos vivem em `filesDir`, fora de qualquer backup automático do Android — o que
 * é bom para privacidade e péssimo para durabilidade: desinstalar o app apaga meses de
 * registro sem aviso. Este é o único caminho para tirar esses dados do aparelho, e por
 * isso a Fase 5 nunca foi opcional.
 *
 * A restauração **substitui** tudo, em vez de mesclar. Mesclar exigiria decidir o que
 * fazer com ids repetidos, missões editadas dos dois lados e fotos duplicadas — regras
 * que ninguém consegue prever e que falhariam de formas difíceis de perceber. Substituir
 * é uma semântica que cabe inteira num aviso de uma linha.
 */
class BackupManager(
    private val context: Context,
    private val db: AlvoradaDatabase,
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val evidenceRoot: File
        get() = File(context.filesDir, "evidence").apply { mkdirs() }

    fun suggestedFileName(): String = "alvorada-backup-${LocalDate.now()}.zip"

    // ------------------------------------------------------------------ export

    suspend fun export(target: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val dao = db.backupDao()
            val photos = dao.photos()

            val envelope = BackupEnvelope(
                formatVersion = FORMAT_VERSION,
                exportedAt = System.currentTimeMillis(),
                folders = dao.folders(),
                alarms = dao.alarms(),
                missions = dao.missions(),
                occurrences = dao.occurrences(),
                instances = dao.instances(),
                photos = photos.map {
                    BackupPhoto(
                        id = it.id,
                        missionInstanceId = it.missionInstanceId,
                        relativePath = it.filePath.toRelativeEvidencePath(),
                        takenAt = it.takenAt,
                        sortOrder = it.sortOrder,
                    )
                },
                points = dao.points(),
                rewards = dao.rewards(),
                redemptions = dao.redemptions(),
                streak = dao.streak(),
                checklists = dao.checklists(),
                checklistItems = dao.checklistItems(),
                checklistCycles = dao.checklistCycles(),
                checklistItemStates = dao.checklistItemStates(),
            )

            var written = 0
            context.contentResolver.openOutputStream(target)?.use { out ->
                ZipOutputStream(out.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry(MANIFEST))
                    // Serializer explícito: a variante reificada exige o import da
                    // extensão e, sem ele, resolve para a sobrecarga de dois
                    // parâmetros com um erro de tipo bem pouco óbvio.
                    zip.write(json.encodeToString(BackupEnvelope.serializer(), envelope).toByteArray())
                    zip.closeEntry()

                    envelope.photos.forEach { photo ->
                        val file = File(evidenceRoot, photo.relativePath)
                        // Foto referenciada no banco mas ausente em disco não aborta o
                        // export: melhor um backup com 39 de 40 fotos do que nenhum.
                        if (!file.exists()) {
                            Log.w(TAG, "Foto ausente ao exportar: ${photo.relativePath}")
                            return@forEach
                        }
                        zip.putNextEntry(ZipEntry("$PHOTO_DIR/${photo.relativePath}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        written++
                    }
                }
            } ?: error("Não foi possível escrever no arquivo escolhido")

            BackupResult.Exported(photoCount = written, alarmCount = envelope.alarms.size)
        }.getOrElse {
            Log.e(TAG, "Falha ao exportar", it)
            BackupResult.Failed(it.message ?: "Falha desconhecida ao exportar")
        }
    }

    // ------------------------------------------------------------------ import

    suspend fun import(source: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            var envelope: BackupEnvelope? = null
            val staged = File(context.cacheDir, "restore-${System.currentTimeMillis()}")
            staged.mkdirs()

            // Descompacta para uma área temporária antes de tocar no banco: se o ZIP
            // estiver corrompido, o estado atual continua intacto.
            context.contentResolver.openInputStream(source)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        val current = entry
                        val name = current.name
                        when {
                            name == MANIFEST || name == LEGACY_MANIFEST -> envelope = json.decodeFromString(
                                BackupEnvelope.serializer(),
                                zip.readBytes().decodeToString(),
                            )

                            name.startsWith("$PHOTO_DIR/") && !current.isDirectory -> {
                                val relative = name.removePrefix("$PHOTO_DIR/").sanitizedRelative()
                                if (relative != null) {
                                    val out = File(staged, relative)
                                    out.parentFile?.mkdirs()
                                    out.outputStream().use { zip.copyTo(it) }
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Não foi possível ler o arquivo escolhido")

            val data = envelope ?: error("Arquivo sem manifesto — não parece um backup do Alvorada")
            if (data.formatVersion > FORMAT_VERSION) {
                error("Backup gerado por uma versão mais nova do app (formato ${data.formatVersion})")
            }

            // Só agora o estado atual é descartado.
            evidenceRoot.deleteRecursively()
            evidenceRoot.mkdirs()
            staged.copyRecursively(evidenceRoot, overwrite = true)
            staged.deleteRecursively()

            val restoredPhotos = data.photos.map {
                EvidencePhotoEntity(
                    id = it.id,
                    missionInstanceId = it.missionInstanceId,
                    // Reancorado no `filesDir` desta instalação.
                    filePath = File(evidenceRoot, it.relativePath).absolutePath,
                    takenAt = it.takenAt,
                    sortOrder = it.sortOrder,
                )
            }

            db.backupDao().restoreAll(
                folders = data.folders,
                alarms = data.alarms,
                missions = data.missionsWithFollowFlag(),
                occurrences = data.occurrences,
                instances = data.instances,
                photos = restoredPhotos,
                points = data.points,
                rewards = data.rewards,
                redemptions = data.redemptions,
                streak = data.streak,
                checklists = data.checklists,
                checklistItems = data.checklistItems,
                checklistCycles = data.checklistCycles,
                checklistItemStates = data.checklistItemStates,
            )

            BackupResult.Imported(restoredPhotos.size, data.alarms.size)
        }.getOrElse {
            Log.e(TAG, "Falha ao importar", it)
            BackupResult.Failed(it.message ?: "Falha desconhecida ao importar")
        }
    }

    /**
     * Deduz "seguir os dias do despertador" num backup que não tinha esse campo.
     *
     * O padrão da coluna é seguir, o que reescreveria escolhas reais na restauração:
     * uma missão restrita a segunda e quarta passaria a valer nos cinco dias do
     * despertador na próxima vez que ele fosse salvo. A mesma regra da migration
     * resolve aqui — só segue quem já estava exatamente igual ao dono.
     */
    private fun BackupEnvelope.missionsWithFollowFlag(): List<MissionEntity> {
        if (formatVersion >= 2) return missions
        val maskOf = alarms.associate { it.id to it.missionDayMask() }
        return missions.map { it.copy(followsAlarmDays = it.daysMask == maskOf[it.alarmId]) }
    }

    /** `…/files/evidence/2026-08-02/12_1754.jpg` → `2026-08-02/12_1754.jpg`. */
    private fun String.toRelativeEvidencePath(): String {
        val file = File(this)
        val day = file.parentFile?.name ?: return file.name
        return "$day/${file.name}"
    }

    /**
     * Um ZIP pode conter `../` e escrever fora do destino (Zip Slip). Só passam nomes
     * no formato `<dia>/<arquivo>`, sem travessia de diretório.
     */
    private fun String.sanitizedRelative(): String? {
        val parts = replace('\\', '/').split('/').filter { it.isNotBlank() }
        if (parts.size != 2) return null
        if (parts.any { it == "." || it == ".." }) return null
        return "${parts[0]}/${parts[1]}"
    }

    private companion object {
        const val TAG = "BackupManager"

        /**
         * 2 = missões sabem se seguem os dias do despertador.
         *
         * A leitura continua aceitando a versão 1 e preenchendo o campo por dedução;
         * o número sobe para que uma versão antiga do app recuse um arquivo novo em
         * vez de restaurá-lo pela metade.
         */
        /**
         * 3 traz as quatro tabelas de checklist.
         *
         * Subir o número é o que faz uma versão anterior do app **recusar** o
         * arquivo em vez de restaurá-lo pela metade: os campos novos são opcionais
         * na desserialização, então sem o número ela aceitaria o backup e perderia
         * os checklists em silêncio.
         */
        const val FORMAT_VERSION = 3
        const val MANIFEST = "alvorada-backup.json"

        /**
         * Nome do manifesto antes de o app se chamar Alvorada.
         *
         * A leitura aceita os dois. Trocar só o nome escrito transformaria todo backup
         * já exportado em "arquivo sem manifesto" — um arquivo íntegro, recusado por
         * causa de uma decisão de marca. O custo de manter é uma constante.
         */
        const val LEGACY_MANIFEST = "wake-backup.json"
        const val PHOTO_DIR = "evidence"
    }
}
