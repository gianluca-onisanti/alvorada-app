package dev.gianluca.alvoradaapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.gianluca.alvoradaapp.core.RepeatKind
import kotlinx.serialization.Serializable

/**
 * Convenções de tipo neste schema:
 * - instantes absolutos são epoch millis (`Long`);
 * - datas civis são `String` no formato ISO `yyyy-MM-dd`, que ordena lexicograficamente
 *   e serve direto como chave de agrupamento na galeria;
 * - dias da semana são bitmask `Int`, bit 0 = segunda … bit 6 = domingo (ver `DayMask`).
 */

@Entity(tableName = "folders")
@Serializable
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#7C5CFF",
    val iconKey: String = "folder",
    val sortOrder: Int = 0,
    /**
     * Pasta recolhida na lista. Mora no banco, e não em estado de tela, porque
     * recolher uma pasta é uma preferência duradoura — reabrir sozinha a cada
     * abertura do app seria uma pequena traição repetida todo dia.
     */
    val collapsed: Boolean = false,
)

@Entity(
    tableName = "alarms",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("folderId")],
)
@Serializable
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val label: String,
    val hour: Int,
    val minute: Int,
    val daysMask: Int,
    /**
     * Como o alarme se repete. `WEEKLY` continua sendo o padrão e ignora todos os
     * campos abaixo — os despertadores existentes seguem se comportando exatamente
     * como antes. Ver `core/Recurrence.kt` para o significado de cada máscara.
     */
    val repeatKind: RepeatKind = RepeatKind.WEEKLY,
    val intervalWeeks: Int = 2,
    /** ISO `yyyy-MM-dd`. Semana de referência da contagem de [intervalWeeks]. */
    val anchorDate: String? = null,
    val ordinalMask: Int = 0,
    val monthDaysMask: Int = 0,
    /** `null` = som de alarme padrão do sistema. */
    val soundUri: String? = null,
    val soundIsSystem: Boolean = true,
    /**
     * Nome exibível do som, resolvido uma vez no momento da escolha.
     * Guardado em vez de derivado porque resolver o título de um `content://` exige
     * I/O — não dá para fazer isso ao desenhar cada linha da lista.
     */
    val soundLabel: String? = null,
    val volumePercent: Int = 100,
    val escalateVolume: Boolean = true,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 3,
    val enabled: Boolean = true,
    /**
     * Epoch millis do único toque que deve ser pulado — "desligar só a próxima ativação".
     *
     * Guardar o instante, e não um `skipNext: Boolean`, é o que faz o pulo caducar
     * sozinho: ele só é respeitado enquanto continuar batendo com o toque que
     * aconteceria de fato (ver `NextFireCalculator.outlook`). Passou da hora, ou o
     * despertador foi reconfigurado, e o valor deixa de significar qualquer coisa —
     * sem nenhuma rotina de limpeza para esquecer de chamar.
     */
    val skipNextFireAt: Long? = null,
    val createdAt: Long,
)

/**
 * `daysMask` precisa ser subconjunto do `daysMask` do alarme dono — não faz sentido
 * uma missão de domingo num despertador que não toca domingo. A UI restringe o
 * seletor e a poda acontece ao editar os dias do alarme.
 */
@Entity(
    tableName = "missions",
    foreignKeys = [
        ForeignKey(
            entity = AlarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["alarmId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("alarmId")],
)
@Serializable
data class MissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val title: String,
    val notes: String = "",
    val daysMask: Int,
    /**
     * A missão acompanha os dias do despertador em vez de ter dias próprios.
     *
     * Ligado, [daysMask] deixa de ser uma escolha e passa a ser uma cópia mantida em
     * dia a cada gravação do despertador. É o padrão porque é o que quase sempre se
     * quer: mudar o despertador de Seg–Sex para todo dia e descobrir semanas depois
     * que as missões continuaram presas nos dias antigos é uma falha silenciosa —
     * o despertador toca, e nada é cobrado.
     */
    val followsAlarmDays: Boolean = true,
    val requiresEvidence: Boolean = false,
    /** Minutos entre dispensar o alarme e o prazo da foto. */
    val evidenceWindowMinutes: Int = 30,
    /** Intervalo entre cobranças depois que o prazo estoura. */
    val chaseIntervalMinutes: Int = 10,
    val maxChases: Int = 3,
    val xpValue: Int = DEFAULT_XP,
    val coinValue: Int = DEFAULT_COINS,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
) {
    /** Vale o padrão nos dois? Então não há nada a dizer sobre o valor desta missão. */
    val hasDefaultValue: Boolean
        get() = xpValue == DEFAULT_XP && coinValue == DEFAULT_COINS

    companion object {
        const val DEFAULT_XP = 10
        const val DEFAULT_COINS = 10
    }
}

@Entity(
    tableName = "alarm_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = AlarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["alarmId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("alarmId"), Index("scheduledFor")],
)
@Serializable
data class OccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val scheduledFor: Long,
    val firedAt: Long? = null,
    val dismissedAt: Long? = null,
    val snoozeCount: Int = 0,
    val status: OccurrenceStatus = OccurrenceStatus.SCHEDULED,
)

@Entity(
    tableName = "mission_instances",
    foreignKeys = [
        ForeignKey(
            entity = MissionEntity::class,
            parentColumns = ["id"],
            childColumns = ["missionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = OccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("missionId"), Index("occurrenceId"), Index("date"), Index("status")],
)
@Serializable
data class MissionInstanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val missionId: Long,
    val occurrenceId: Long,
    /** ISO `yyyy-MM-dd`. */
    val date: String,
    val status: MissionStatus = MissionStatus.PENDING,
    /** Epoch millis do prazo da foto; `null` quando a missão não exige evidência. */
    val evidenceDeadline: Long? = null,
    val completedAt: Long? = null,
    val chaseCount: Int = 0,
    val wasLate: Boolean = false,
    val xpAwarded: Int = 0,
    val coinAwarded: Int = 0,
)

@Entity(
    tableName = "evidence_photos",
    foreignKeys = [
        ForeignKey(
            entity = MissionInstanceEntity::class,
            parentColumns = ["id"],
            childColumns = ["missionInstanceId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("missionInstanceId")],
)
@Serializable
data class EvidencePhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val missionInstanceId: Long,
    /** Caminho absoluto em `filesDir/evidence/yyyy-MM-dd/`. */
    val filePath: String,
    val takenAt: Long,
    val sortOrder: Int = 0,
)

/** Extrato append-only. Saldo e nível são sempre derivados daqui, nunca guardados. */
@Entity(tableName = "points_ledger", indices = [Index("timestamp")])
@Serializable
data class PointsLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val xpDelta: Int,
    val coinDelta: Int,
    val reason: PointsReason,
    val missionInstanceId: Long? = null,
    val rewardId: Long? = null,
)

@Entity(tableName = "rewards")
@Serializable
data class RewardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val costCoins: Int,
    val iconKey: String = "gift",
    val archived: Boolean = false,
)

@Entity(tableName = "reward_redemptions", indices = [Index("rewardId")])
@Serializable
data class RewardRedemptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rewardId: Long,
    val redeemedAt: Long,
    val costPaid: Int,
)

/** Tabela de linha única — sempre `id = 1`. */
@Entity(tableName = "streak_state")
@Serializable
data class StreakStateEntity(
    @PrimaryKey val id: Int = 1,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val shieldsAvailable: Int = 2,
    /** ISO `yyyy-MM-dd` do último dia já avaliado pela varredura diária. */
    val lastEvaluatedDate: String? = null,
)
