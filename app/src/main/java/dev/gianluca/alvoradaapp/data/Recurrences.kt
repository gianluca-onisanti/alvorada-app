package dev.gianluca.alvoradaapp.data

import dev.gianluca.alvoradaapp.core.FireOutlook
import dev.gianluca.alvoradaapp.core.NextFireCalculator
import dev.gianluca.alvoradaapp.core.Recurrence
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Ponte entre a recorrência (tipo puro, testável, em `core`) e as colunas achatadas
 * da tabela de alarmes.
 *
 * A recorrência não vira uma coluna JSON de propósito: `daysMask` continua sendo um
 * inteiro consultável em SQL, que é o que permite ao painel e às missões filtrarem
 * por dia sem carregar todos os alarmes para a memória.
 */
fun AlarmEntity.recurrence(): Recurrence = Recurrence(
    kind = repeatKind,
    daysMask = daysMask,
    intervalWeeks = intervalWeeks,
    anchorDate = anchorDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    ordinalMask = ordinalMask,
    monthDaysMask = monthDaysMask,
)

fun AlarmEntity.withRecurrence(recurrence: Recurrence): AlarmEntity = copy(
    repeatKind = recurrence.kind,
    daysMask = recurrence.daysMask,
    intervalWeeks = recurrence.intervalWeeks,
    anchorDate = recurrence.anchorDate?.toString(),
    ordinalMask = recurrence.ordinalMask,
    monthDaysMask = recurrence.monthDaysMask,
)

/** Em que dias da semana as missões deste despertador podem existir. */
fun AlarmEntity.missionDayMask(): Int = recurrence().missionDayMask

/** Toca uma vez e se apaga. Ver `RepeatKind.ONCE`. */
val AlarmEntity.isOneShot: Boolean get() = recurrence().isOneShot

/**
 * Próximo toque deste despertador, já com o pulo aplicado.
 *
 * Um despertador desligado não tem previsão nenhuma — nem para mostrar na lista, nem
 * para agendar. É o mesmo `null` nos dois casos de propósito: quem pergunta "quando
 * toca?" não deveria precisar checar `enabled` antes.
 */
fun AlarmEntity.outlook(now: ZonedDateTime = ZonedDateTime.now()): FireOutlook =
    if (!enabled) FireOutlook.NONE
    else NextFireCalculator.outlook(hour, minute, recurrence(), now, skipNextFireAt)

/** Rótulo curto de repetição, para a lista e para o editor. */
fun AlarmEntity.describeRepeat(): String = recurrence().describe()
