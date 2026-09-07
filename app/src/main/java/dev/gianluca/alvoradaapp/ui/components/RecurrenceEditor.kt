package dev.gianluca.alvoradaapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gianluca.alvoradaapp.core.DayMask
import dev.gianluca.alvoradaapp.core.MonthDayMask
import dev.gianluca.alvoradaapp.core.OrdinalMask
import dev.gianluca.alvoradaapp.core.RepeatKind
import dev.gianluca.alvoradaapp.core.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val KINDS = listOf(
    RepeatKind.WEEKLY to "Semanal",
    RepeatKind.WEEK_INTERVAL to "A cada N semanas",
    RepeatKind.MONTHLY_ORDINAL to "Semana do mês",
    RepeatKind.MONTHLY_DAYS to "Dias do mês",
)

private val INTERVAL_OPTIONS = listOf(2, 3, 4, 6, 8)
private val ANCHOR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
private val ONCE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", Locale.forLanguageTag("pt-BR"))

/**
 * Editor de repetição.
 *
 * Os quatro modos são exclusivos, e não flags que se combinam: "toda sexta" e "última
 * sexta do mês" são intenções diferentes, e permitir marcar as duas ao mesmo tempo só
 * produziria configurações que ninguém consegue prever. O seletor semanal continua
 * sendo o primeiro e o padrão — os outros três existem para os casos que ele não cobre.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurrenceEditor(
    recurrence: Recurrence,
    onChange: (Recurrence) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    Column(modifier.fillMaxWidth()) {
        // O despertador de uma vez só não troca de modo: ele nasce autodestruível na
        // escolha do "+" e morre no primeiro toque. Oferecer os quatro modos aqui
        // proporia uma conversão que o resto do app não sustenta — a começar pelas
        // missões, que ele não pode ter.
        if (!recurrence.isOneShot) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KINDS.forEach { (kind, label) ->
                    FilterChip(
                        selected = recurrence.kind == kind,
                        onClick = { onChange(recurrence.switchedTo(kind, today)) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        when (recurrence.kind) {
            RepeatKind.WEEKLY -> WeeklyControls(recurrence, onChange)
            RepeatKind.WEEK_INTERVAL -> IntervalControls(recurrence, onChange, today)
            RepeatKind.MONTHLY_ORDINAL -> OrdinalControls(recurrence, onChange)
            RepeatKind.MONTHLY_DAYS -> MonthDayControls(recurrence, onChange)
            RepeatKind.ONCE -> OnceControls(recurrence, onChange, today)
        }

        if (!recurrence.canFire) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = missingPieceHint(recurrence.kind),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

// ------------------------------------------------------------------------ semanal

@Composable
private fun WeeklyControls(recurrence: Recurrence, onChange: (Recurrence) -> Unit) {
    DayPicker(
        mask = recurrence.daysMask,
        onToggle = { onChange(recurrence.copy(daysMask = DayMask.toggle(recurrence.daysMask, it))) },
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { onChange(recurrence.copy(daysMask = DayMask.EVERY_DAY)) },
            label = { Text("Todo dia") },
        )
        AssistChip(
            onClick = { onChange(recurrence.copy(daysMask = DayMask.WEEKDAYS)) },
            label = { Text("Seg–Sex") },
        )
        AssistChip(
            onClick = { onChange(recurrence.copy(daysMask = DayMask.WEEKEND)) },
            label = { Text("Fim de semana") },
        )
    }
}

// ------------------------------------------------------------------- a cada N semanas

@Composable
private fun IntervalControls(
    recurrence: Recurrence,
    onChange: (Recurrence) -> Unit,
    today: LocalDate,
) {
    DayPicker(
        mask = recurrence.daysMask,
        onToggle = { onChange(recurrence.copy(daysMask = DayMask.toggle(recurrence.daysMask, it))) },
    )

    Spacer(Modifier.height(14.dp))
    Text("Intervalo", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        INTERVAL_OPTIONS.forEach { weeks ->
            FilterChip(
                selected = recurrence.intervalWeeks == weeks,
                onClick = { onChange(recurrence.copy(intervalWeeks = weeks)) },
                label = { Text("${weeks}s") },
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    // Sem âncora, "a cada duas semanas" não quer dizer nada — duas semanas a partir
    // de quando? Estes dois botões são o controle de fase inteiro: começar agora ou
    // pular uma semana.
    val anchor = recurrence.anchorDate ?: today
    Text(
        "Contando a partir da semana de ${anchor.format(ANCHOR_FORMAT)}.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { onChange(recurrence.copy(anchorDate = today)) }) {
            Text("Esta semana")
        }
        TextButton(onClick = { onChange(recurrence.copy(anchorDate = today.plusWeeks(1))) }) {
            Text("Próxima semana")
        }
    }
}

// -------------------------------------------------------------------- semana do mês

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrdinalControls(recurrence: Recurrence, onChange: (Recurrence) -> Unit) {
    DayPicker(
        mask = recurrence.daysMask,
        onToggle = { onChange(recurrence.copy(daysMask = DayMask.toggle(recurrence.daysMask, it))) },
    )

    Spacer(Modifier.height(14.dp))
    Text("Qual ocorrência do mês", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..4).forEach { index ->
            FilterChip(
                selected = OrdinalMask.contains(recurrence.ordinalMask, index),
                onClick = {
                    onChange(
                        recurrence.copy(
                            ordinalMask = OrdinalMask.toggle(recurrence.ordinalMask, index),
                        )
                    )
                },
                label = { Text(OrdinalMask.label(index)) },
            )
        }
        FilterChip(
            selected = OrdinalMask.hasLast(recurrence.ordinalMask),
            onClick = {
                onChange(recurrence.copy(ordinalMask = OrdinalMask.toggleLast(recurrence.ordinalMask)))
            },
            label = { Text("Última") },
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "\"Última\" não é o mesmo que \"5ª\": meses com quatro sextas não têm uma " +
            "quinta sexta, e quem pede a última quer que ela aconteça todo mês.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// -------------------------------------------------------------------- dias do mês

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthDayControls(recurrence: Recurrence, onChange: (Recurrence) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (1..31).forEach { day ->
            FilterChip(
                selected = MonthDayMask.contains(recurrence.monthDaysMask, day),
                onClick = {
                    onChange(
                        recurrence.copy(
                            monthDaysMask = MonthDayMask.toggle(recurrence.monthDaysMask, day),
                        )
                    )
                },
                label = { Text("$day") },
            )
        }
        FilterChip(
            selected = MonthDayMask.hasLastDay(recurrence.monthDaysMask),
            onClick = {
                onChange(
                    recurrence.copy(
                        monthDaysMask = MonthDayMask.toggleLastDay(recurrence.monthDaysMask),
                    )
                )
            },
            label = { Text("Último dia") },
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "O dia 31 simplesmente não existe em fevereiro — nesses meses o despertador " +
            "pula. Para o fechamento do mês, use \"último dia\".",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Neste modo as missões valem sempre que o despertador toca: o dia da semana " +
            "muda a cada mês, então não dá para restringi-las por ele.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// --------------------------------------------------------------------- data única

/**
 * Uma data e mais nada.
 *
 * "Hoje" e "Amanhã" ficam à mão porque são o caso real deste modo — um lembrete para
 * daqui a algumas horas, não um compromisso de outubro. O calendário completo continua
 * a um toque, para as exceções.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnceControls(
    recurrence: Recurrence,
    onChange: (Recurrence) -> Unit,
    today: LocalDate,
) {
    var picking by remember { mutableStateOf(false) }
    val date = recurrence.anchorDate ?: today

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = date.format(ONCE_FORMAT).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        AssistChip(onClick = { picking = true }, label = { Text("Calendário") })
    }

    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { onChange(recurrence.copy(anchorDate = today)) }) { Text("Hoje") }
        TextButton(onClick = { onChange(recurrence.copy(anchorDate = today.plusDays(1))) }) {
            Text("Amanhã")
        }
    }

    if (!picking) return

    // O `DatePicker` trabalha em millis UTC de meia-noite, e não no fuso do aparelho.
    // Converter pelo fuso local deslocaria a data escolhida em um dia para quem está
    // a oeste de Greenwich — que é o caso do Brasil inteiro.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !utcTimeMillis.toUtcDate().isBefore(today)

                override fun isSelectableYear(year: Int): Boolean = year >= today.year
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = { picking = false },
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let {
                    onChange(recurrence.copy(anchorDate = it.toUtcDate()))
                }
                picking = false
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
    ) { DatePicker(state = pickerState) }
}

private fun Long.toUtcDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

// ------------------------------------------------------------------------ internos

/**
 * Troca de modo preservando o que ainda faz sentido e preenchendo o que o novo modo
 * exige. Cair num estado que nunca dispara logo depois de tocar num chip seria uma
 * armadilha silenciosa.
 */
private fun Recurrence.switchedTo(kind: RepeatKind, today: LocalDate): Recurrence = when (kind) {
    RepeatKind.WEEKLY -> copy(kind = kind)

    RepeatKind.WEEK_INTERVAL -> copy(
        kind = kind,
        anchorDate = anchorDate ?: today,
        intervalWeeks = if (intervalWeeks < 2) 2 else intervalWeeks,
    )

    RepeatKind.MONTHLY_ORDINAL -> copy(
        kind = kind,
        ordinalMask = if (ordinalMask == OrdinalMask.NONE) OrdinalMask.bitOf(0) else ordinalMask,
    )

    RepeatKind.MONTHLY_DAYS -> copy(kind = kind)

    RepeatKind.ONCE -> copy(kind = kind, anchorDate = anchorDate ?: today)
}

private fun missingPieceHint(kind: RepeatKind): String = when (kind) {
    RepeatKind.WEEKLY, RepeatKind.WEEK_INTERVAL ->
        "Sem nenhum dia marcado o despertador nunca toca."

    RepeatKind.MONTHLY_ORDINAL ->
        "Marque o dia da semana e ao menos uma ocorrência do mês."

    RepeatKind.MONTHLY_DAYS ->
        "Marque ao menos um dia do mês."

    RepeatKind.ONCE ->
        "Escolha a data em que ele deve tocar."
}
