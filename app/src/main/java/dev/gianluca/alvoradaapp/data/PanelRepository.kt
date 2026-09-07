package dev.gianluca.alvoradaapp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Progresso de uma pasta num dia. */
data class FolderProgress(
    val folderId: Long,
    val name: String,
    val colorHex: String,
    val rows: List<DayMissionRow>,
) {
    val completed: Int get() = rows.count { it.status == MissionStatus.COMPLETED }
    val failed: Int get() = rows.count { it.status == MissionStatus.FAILED }
    val open: Int get() = rows.count {
        it.status == MissionStatus.PENDING || it.status == MissionStatus.AWAITING_EVIDENCE
    }
    val total: Int get() = rows.size
    val ratio: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

/** O panorama de um dia, agrupado por pasta. */
data class DaySummary(
    val date: String,
    val folders: List<FolderProgress>,
) {
    val completed: Int get() = folders.sumOf { it.completed }
    val total: Int get() = folders.sumOf { it.total }
    val ratio: Float get() = if (total == 0) 0f else completed.toFloat() / total
    val isEmpty: Boolean get() = total == 0
}

/** Uma missão que exige foto, com as fotos que já chegaram. */
data class EvidenceTask(
    val row: DayMissionRow,
    val photos: List<EvidencePhotoEntity>,
) {
    val hasPhotos: Boolean get() = photos.isNotEmpty()
}

/**
 * As duas metades da tela de Evidências.
 *
 * Ficam juntas no mesmo tipo porque só juntas respondem à pergunta real: não é "o que
 * falta", é "estou em dia?". Uma lista de pendências sem o que já foi entregue mostra
 * só o débito, e num app cujo objetivo é sustentar rotina isso é a metade errada.
 */
data class EvidenceBoard(
    val pending: List<EvidenceTask>,
    val done: List<EvidenceTask>,
) {
    val isEmpty: Boolean get() = pending.isEmpty() && done.isEmpty()
}

/** Como a galeria separa as fotos. O padrão é sempre [DAY]. */
enum class GalleryGrouping(val label: String) {
    DAY("Dia"),
    FOLDER("Categoria"),
    ALARM("Despertador"),
}

/**
 * Um bloco da galeria. [label] já vem pronto para exibição — exceto em [GalleryGrouping.DAY],
 * em que é a data ISO e a tela a formata como "Hoje", "Ontem" ou por extenso.
 */
data class GalleryGroup(
    val key: String,
    val label: String,
    val colorHex: String?,
    val photos: List<GalleryPhoto>,
    val sortKey: Int = 0,
)

/**
 * Leitura agregada. Separado do `MissionRepository` de propósito: ali tudo escreve e
 * tem efeito colateral no agendador; aqui nada muda estado, o que torna as duas
 * metades muito mais fáceis de raciocinar em separado.
 */
class PanelRepository(private val db: AlvoradaDatabase) {

    fun observeDay(date: String = today()): Flow<DaySummary> =
        db.panelDao().observeDay(date).map { rows ->
            DaySummary(date = date, folders = rows.groupIntoFolders())
        }

    /** Pendências de qualquer dia — o que ainda depende de você agora. */
    fun observeOpen(): Flow<List<DayMissionRow>> = db.panelDao().observeOpen()

    /** Últimos [days] dias, sempre completos: dias sem nada entram com zero. */
    fun observeRecentTallies(days: Int = 7): Flow<List<DayTally>> {
        val end = LocalDate.now()
        val start = end.minusDays((days - 1).toLong())
        return db.panelDao().observeTallies(start.toString(), end.toString()).map { tallies ->
            val byDate = tallies.associateBy { it.date }
            (0 until days).map { offset ->
                val date = start.plusDays(offset.toLong()).toString()
                byDate[date] ?: DayTally(date, total = 0, completed = 0)
            }
        }
    }

    /**
     * Todas as fotos, com o contexto de cada uma. Uma consulta só serve aos três
     * agrupamentos — trocar a visão da galeria é reordenar o que já está em memória,
     * não voltar ao banco.
     */
    fun observeGalleryPhotos(): Flow<List<GalleryPhoto>> = db.panelDao().observeGallery()

    /**
     * A tela de Evidências inteira, num Flow só.
     *
     * As fotos vêm todas de uma vez e são agrupadas em memória. A alternativa —
     * uma consulta de fotos por missão listada — multiplicaria as idas ao banco pelo
     * número de linhas, e reemitiria a tela em cascata a cada foto tirada.
     */
    fun observeEvidenceBoard(date: String = today()): Flow<EvidenceBoard> =
        combine(
            db.panelDao().observeAwaitingEvidence(),
            db.panelDao().observeEvidenceDone(date),
            db.evidencePhotoDao().observeAll(),
        ) { pending, done, photos ->
            val byInstance = photos.groupBy { it.missionInstanceId }
            EvidenceBoard(
                pending = pending.map { EvidenceTask(it, byInstance[it.instanceId].orEmpty()) },
                done = done.map { EvidenceTask(it, byInstance[it.instanceId].orEmpty()) },
            )
        }

    private fun List<DayMissionRow>.groupIntoFolders(): List<FolderProgress> =
        groupBy { it.folderId }.map { (folderId, rows) ->
            FolderProgress(
                folderId = folderId,
                name = rows.first().folderName,
                colorHex = rows.first().folderColor,
                rows = rows,
            )
        }

    companion object {
        fun today(): String = LocalDate.now().toString()

        /**
         * Transformação pura: mesma lista de fotos, três leituras diferentes.
         *
         * Fica fora do `Flow` de propósito. Se o agrupamento fizesse parte da consulta,
         * trocar a visão recomeçaria a coleta e a tela piscaria vazia no caminho —
         * por um reordenamento que o app já tem tudo para fazer na hora.
         */
        fun group(
            photos: List<GalleryPhoto>,
            grouping: GalleryGrouping,
        ): List<GalleryGroup> = when (grouping) {
            // Data desc: o mais recente primeiro é o que se procura ao abrir a galeria.
            GalleryGrouping.DAY -> photos.groupBy { it.date }
                .map { (date, group) -> GalleryGroup(date, date, null, group) }
                .sortedByDescending { it.key }

            GalleryGrouping.FOLDER -> photos.groupBy { it.folderId }
                .map { (id, group) ->
                    val first = group.first()
                    GalleryGroup("f$id", first.folderName, first.folderColor, group)
                }
                .sortedBy { it.label.lowercase() }

            // Por horário, igual à lista de despertadores: procurar a foto do remédio
            // das 8h não deveria exigir um modelo mental diferente do da outra tela.
            GalleryGrouping.ALARM -> photos.groupBy { it.alarmId }
                .map { (id, group) ->
                    val first = group.first()
                    GalleryGroup(
                        key = "a$id",
                        // Nome e horário juntos, como na lista de despertadores: só a
                        // hora obriga a lembrar qual dos dois alarmes das 7h é este.
                        label = "%s - %02d:%02d".format(
                            first.alarmLabel.ifBlank { "Sem nome" },
                            first.alarmHour,
                            first.alarmMinute,
                        ),
                        colorHex = first.folderColor,
                        photos = group,
                        sortKey = first.alarmHour * 60 + first.alarmMinute,
                    )
                }
                .sortedWith(compareBy({ it.sortKey }, { it.label.lowercase() }))
        }
    }
}
