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
    /**
     * Epoch millis em que a supressão por checklist deixa de valer — o fim da rodada
     * que calou este despertador. Enquanto o instante não chegar, todo disparo
     * anterior a ele é ignorado.
     *
     * Guardar o instante, e não um `suprimido: Boolean`, é o mesmo princípio de
     * [skipNextFireAt] pelo mesmo motivo: o valor caduca sozinho quando a rodada
     * vira, sem nenhuma rotina de limpeza para alguém esquecer de chamar.
     *
     * São campos distintos porque respondem a coisas distintas. [skipNextFireAt] é um
     * gesto manual sobre **um** toque; este é consequência de um item marcado, e
     * precisa cobrir **vários** toques — um checklist semanal num despertador diário
     * tem sete para calar, e um instante só não daria conta.
     */
    val suppressedUntil: Long? = null,
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
    /**
     * Guarda de idempotência dos créditos de checklist, no papel que
     * [missionInstanceId] cumpre para missões: marcar e desmarcar o mesmo item não
     * pode pontuar duas vezes.
     */
    val checklistItemStateId: Long? = null,
    /** Guarda do bônus de rodada fechada. Uma rodada credita o bônus uma vez só. */
    val checklistCycleId: Long? = null,
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

// ---------------------------------------------------------------------------
// Checklists
//
// Boa parte dos despertadores é, na prática, uma lista recorrente: escovar os
// dentes todo dia, limpar a mesa toda semana, limpar o teclado todo mês. Modelar
// isso como missão pendurada num despertador força um despertador por item e não
// tem noção de ciclo nem de vencimento.
//
// O paralelo com o que já existe é quase exato, e os nomes seguem esse paralelo:
//   `checklists`           está para `alarms`
//   `checklist_items`      está para `missions`
//   `checklist_cycles`     está para `alarm_occurrences`
//   `checklist_item_states` está para `mission_instances`
//
// A diferença que justifica tabelas próprias em vez de reúso: a rodada de um
// checklist é um intervalo com vencimento, não um instante de disparo, e ela
// existe mesmo em dia nenhum despertador tocar.
// ---------------------------------------------------------------------------

/**
 * Uma lista recorrente com vencimento.
 *
 * A recorrência entra **achatada em colunas**, exatamente como em [AlarmEntity], e
 * não como JSON: é o que mantém `daysMask` consultável em SQL e o que permite
 * reusar o `Recurrence` de `core` sem serializá-lo. Ver `Recurrences.kt`.
 *
 * A categoria reusa `folders`, a mesma de despertadores. Uma categoria é uma
 * categoria — dois conceitos paralelos de agrupamento seriam pior do que a
 * consequência de compartilhar um, que é: apagar a pasta apaga os checklists dela
 * junto com os despertadores.
 */
@Entity(
    tableName = "checklists",
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
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val name: String,
    val repeatKind: RepeatKind = RepeatKind.WEEKLY,
    val daysMask: Int = 0,
    val intervalWeeks: Int = 2,
    /** ISO `yyyy-MM-dd`. Semana de referência da contagem de [intervalWeeks]. */
    val anchorDate: String? = null,
    val ordinalMask: Int = 0,
    val monthDaysMask: Int = 0,
    /** Hora do vencimento dentro do dia da rodada. É o que a tela mostra em destaque. */
    val dueHour: Int = 22,
    val dueMinute: Int = 0,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long,
)

/**
 * Um item da lista, com ou sem despertador próprio.
 *
 * `alarmId` nulo é o caso comum e explicitamente suportado: nem todo item merece
 * acordar alguém. Quando existe, apagar o despertador **não** pode levar o item
 * junto — daí `SET NULL` em vez de `CASCADE`; o item continua na lista, só perde o
 * lembrete.
 */
@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AlarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["alarmId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("checklistId"), Index("alarmId")],
)
@Serializable
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    val title: String,
    val notes: String = "",
    /** `null` = item sem despertador, marcado à mão quando der. */
    val alarmId: Long? = null,
    val xpValue: Int = MissionEntity.DEFAULT_XP,
    val coinValue: Int = MissionEntity.DEFAULT_COINS,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
) {
    /** Vale o padrão nos dois? Então não há nada a dizer sobre o valor deste item. */
    val hasDefaultValue: Boolean
        get() = xpValue == MissionEntity.DEFAULT_XP && coinValue == MissionEntity.DEFAULT_COINS
}

/**
 * Uma rodada concreta de um checklist.
 *
 * O índice único em (`checklistId`, `periodStart`) é o que torna a abertura de
 * rodada idempotente — abrir a mesma rodada duas vezes é impossível, do mesmo jeito
 * que `countForOccurrence` protege a materialização de missões. Sem ele, abrir a
 * tela duas vezes no mesmo dia criaria duas rodadas e o progresso se dividiria entre
 * elas.
 */
@Entity(
    tableName = "checklist_cycles",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklistId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["checklistId", "periodStart"], unique = true),
        Index("status"),
    ],
)
@Serializable
data class ChecklistCycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    /** ISO `yyyy-MM-dd` do dia em que a rodada começou. */
    val periodStart: String,
    /** Epoch millis do vencimento: até quando dá para cumprir sem atraso. */
    val dueAt: Long,
    /** Epoch millis do início da rodada seguinte. É até aqui que o alarme fica calado. */
    val endsAt: Long,
    val status: ChecklistCycleStatus = ChecklistCycleStatus.OPEN,
    val completedAt: Long? = null,
)

/** O estado de um item numa rodada. Análogo de `mission_instances`. */
@Entity(
    tableName = "checklist_item_states",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistCycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChecklistItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["cycleId", "itemId"], unique = true),
        Index("itemId"),
    ],
)
@Serializable
data class ChecklistItemStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val itemId: Long,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val xpAwarded: Int = 0,
    val coinAwarded: Int = 0,
)

/**
 * A procedência de um arquivo de `Music/Alvorada/`.
 *
 * Tabela lateral, e de propósito: quem sabe **quais** arquivos existem continua sendo o
 * MediaStore. Um MP3 largado na pasta pelo gerenciador de arquivos nunca passa por aqui
 * e mesmo assim aparece na biblioteca — simplesmente sem origem, que é a verdade sobre
 * ele. O que só esta tabela sabe é o que o arquivo não carrega dentro de si: de qual
 * link ele veio, e de qual original ele foi recortado.
 *
 * A linhagem é o motivo principal de a tabela existir. Recortar um corte reencoda em
 * cima de um áudio já reencodado, e cada rodada dessas rebaixa a qualidade um pouco
 * mais. Guardar [parentClipId] com [trimStartMs]/[trimEndMs] deixa a tela de recorte
 * oferecer o original de volta, para o segundo corte sair da mesma fonte que o
 * primeiro em vez de sair do primeiro.
 *
 * O `SET_NULL` do FK para si mesma é deliberado: apagar o original não pode levar o
 * corte junto — o corte é um arquivo por direito próprio, que só perde a chance de ser
 * refeito da fonte.
 */
@Entity(
    tableName = "audio_clips",
    foreignKeys = [
        ForeignKey(
            entity = AudioClipEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentClipId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["mediaStoreUri"], unique = true),
        Index("parentClipId"),
    ],
)
@Serializable
data class AudioClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * A `content://` do MediaStore. É a chave de verdade desta tabela: é por ela que a
     * biblioteca casa cada arquivo listado com a origem dele. Única por índice, porque
     * dois registros para o mesmo arquivo seriam duas respostas para a mesma pergunta.
     */
    val mediaStoreUri: String,
    val displayName: String,
    val relativePath: String,
    val durationMs: Long = 0,
    /** O link que o usuário colou. Nulo quando o arquivo não veio de download. */
    val sourceUrl: String? = null,
    /** O título que o resolvedor devolveu — o nome do vídeo, não o do arquivo. */
    val sourceTitle: String? = null,
    val parentClipId: Long? = null,
    val trimStartMs: Long? = null,
    val trimEndMs: Long? = null,
    val createdAt: Long,
)
