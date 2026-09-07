package dev.gianluca.alvoradaapp.data

/**
 * Projeções de leitura — resultados de JOIN que não correspondem a nenhuma tabela.
 *
 * O painel precisa de missão + pasta na mesma linha, e a pasta está a dois saltos de
 * distância (`mission → alarm → folder`). Resolver isso em SQL e não em Kotlin evita
 * combinar quatro `Flow` só para reconstruir uma relação que o banco já conhece.
 */

/** Uma missão de um dia, já com a pasta a que pertence. */
data class DayMissionRow(
    val instanceId: Long,
    val status: MissionStatus,
    val date: String,
    val evidenceDeadline: Long?,
    val chaseCount: Int,
    val completedAt: Long?,
    val wasLate: Boolean,
    val missionId: Long,
    val missionTitle: String,
    val requiresEvidence: Boolean,
    val maxChases: Int,
    val folderId: Long,
    val folderName: String,
    val folderColor: String,
)

/** Contagem de um dia, para o heatmap da semana. Agregado no SQLite. */
data class DayTally(
    val date: String,
    val total: Int,
    val completed: Int,
) {
    val ratio: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

/**
 * Uma foto com o contexto que a torna legível meses depois.
 *
 * Carrega as três chaves de agrupamento — dia, pasta e despertador — porque o custo de
 * trazê-las no mesmo JOIN é zero e alternar a visão da galeria não pode significar
 * refazer a consulta.
 */
data class GalleryPhoto(
    val id: Long,
    val filePath: String,
    val takenAt: Long,
    val date: String,
    val missionTitle: String,
    val folderId: Long,
    val folderName: String,
    val folderColor: String,
    val alarmId: Long,
    val alarmLabel: String,
    val alarmHour: Int,
    val alarmMinute: Int,
)
