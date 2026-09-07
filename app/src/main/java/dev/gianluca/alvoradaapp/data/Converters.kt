package dev.gianluca.alvoradaapp.data

import androidx.room.TypeConverter
import dev.gianluca.alvoradaapp.core.RepeatKind

/**
 * Enums são gravados pelo nome, não pelo ordinal — reordenar um enum não pode
 * reinterpretar silenciosamente os dados já gravados.
 */
class Converters {

    @TypeConverter
    fun occurrenceStatusToString(value: OccurrenceStatus): String = value.name

    @TypeConverter
    fun stringToOccurrenceStatus(value: String): OccurrenceStatus = OccurrenceStatus.valueOf(value)

    @TypeConverter
    fun missionStatusToString(value: MissionStatus): String = value.name

    @TypeConverter
    fun stringToMissionStatus(value: String): MissionStatus = MissionStatus.valueOf(value)

    @TypeConverter
    fun pointsReasonToString(value: PointsReason): String = value.name

    @TypeConverter
    fun stringToPointsReason(value: String): PointsReason = PointsReason.valueOf(value)

    @TypeConverter
    fun checklistCycleStatusToString(value: ChecklistCycleStatus): String = value.name

    /** Tolerante na leitura pelo mesmo motivo de [stringToRepeatKind]. */
    @TypeConverter
    fun stringToChecklistCycleStatus(value: String): ChecklistCycleStatus =
        runCatching { ChecklistCycleStatus.valueOf(value) }
            .getOrDefault(ChecklistCycleStatus.OPEN)

    @TypeConverter
    fun repeatKindToString(value: RepeatKind): String = value.name

    /**
     * Tolerante na leitura: um valor desconhecido — backup de uma versão futura,
     * banco mexido à mão — cai no comportamento semanal em vez de derrubar o app
     * na primeira consulta.
     */
    @TypeConverter
    fun stringToRepeatKind(value: String): RepeatKind =
        runCatching { RepeatKind.valueOf(value) }.getOrDefault(RepeatKind.WEEKLY)
}
