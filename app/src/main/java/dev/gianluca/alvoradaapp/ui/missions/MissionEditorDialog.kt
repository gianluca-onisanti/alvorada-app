package dev.gianluca.alvoradaapp.ui.missions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gianluca.alvoradaapp.core.DayMask
import dev.gianluca.alvoradaapp.data.MissionEntity
import dev.gianluca.alvoradaapp.ui.components.DayPicker

private val WINDOW_OPTIONS = listOf(5, 15, 30, 60, 120)
private val CHASE_INTERVAL_OPTIONS = listOf(5, 10, 15, 30)

/**
 * Escala de valor das missões.
 *
 * Múltiplos do padrão (10), e não uma régua contínua: o que importa é a relação entre
 * as missões — arrumar a cama valer menos que ir à academia —, não acertar um número
 * absoluto. Poucas opções também seguram a inflação, porque com campo livre a tentação
 * é subir todo mundo, e aí nada vale mais do que nada.
 */
private val VALUE_OPTIONS = listOf(5, 10, 20, 40)

/**
 * Um valor restaurado de backup pode não estar na escala. Sem isto nenhum chip ficaria
 * marcado, o que pareceria defeito — e salvar manteria em silêncio um valor invisível.
 */
private fun valueOptionsFor(current: Int): List<Int> =
    (VALUE_OPTIONS + current).distinct().sorted()

@Composable
fun MissionEditorDialog(
    mission: MissionEntity?,
    alarmId: Long,
    /** Dias em que esta missão pode existir — derivado da repetição do despertador. */
    selectableDays: Int,
    daysHint: String,
    onDismiss: () -> Unit,
    onConfirm: (MissionEntity) -> Unit,
) {
    var title by rememberSaveable(mission?.id) { mutableStateOf(mission?.title.orEmpty()) }
    // Ligado por padrão: a missão nasce valendo nos mesmos dias do despertador, que é
    // o que quase todo mundo quer e ninguém precisa configurar.
    var followsAlarm by rememberSaveable(mission?.id) {
        mutableStateOf(mission?.followsAlarmDays ?: true)
    }
    var daysMask by rememberSaveable(mission?.id) {
        mutableIntStateOf(mission?.daysMask ?: selectableDays)
    }
    // Seguindo o despertador, os dias deixam de ser um campo editável e passam a ser
    // um espelho — inclusive aqui, para o botão Salvar julgar o que vai ser gravado.
    val effectiveDays = if (followsAlarm) selectableDays else daysMask
    var requiresEvidence by rememberSaveable(mission?.id) {
        mutableStateOf(mission?.requiresEvidence ?: false)
    }
    var window by rememberSaveable(mission?.id) {
        mutableIntStateOf(mission?.evidenceWindowMinutes ?: 30)
    }
    var chaseInterval by rememberSaveable(mission?.id) {
        mutableIntStateOf(mission?.chaseIntervalMinutes ?: 10)
    }
    var maxChases by rememberSaveable(mission?.id) { mutableIntStateOf(mission?.maxChases ?: 3) }
    // Já nascem no padrão do schema: quem não quiser diferenciar nada é só não tocar.
    var xpValue by rememberSaveable(mission?.id) {
        mutableIntStateOf(mission?.xpValue ?: MissionEntity.DEFAULT_XP)
    }
    var coinValue by rememberSaveable(mission?.id) {
        mutableIntStateOf(mission?.coinValue ?: MissionEntity.DEFAULT_COINS)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mission == null) "Nova missão" else "Editar missão") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("O que fazer") },
                    placeholder = { Text("Tomar remédio, arrumar a cama…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { followsAlarm = !followsAlarm },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = followsAlarm, onCheckedChange = { followsAlarm = it })
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Seguir os dias do despertador", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (followsAlarm) {
                                "${DayMask.describe(selectableDays)} — muda junto quando " +
                                    "você mexer no despertador."
                            } else {
                                "A missão tem dias próprios, escolhidos abaixo."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!followsAlarm) {
                    Spacer(Modifier.height(12.dp))
                    Text("Dias", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    // Só os dias em que o despertador toca. Uma missão fora deles ficaria
                    // pendente para sempre, sem nada capaz de cumpri-la.
                    DayPicker(
                        mask = daysMask,
                        onToggle = { daysMask = DayMask.toggle(daysMask, it) },
                        selectableDays = selectableDays,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        daysHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("Quanto vale", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Os valores padrão servem para tudo. Mexa aqui só para dizer que uma " +
                        "missão pesa mais que outra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))
                ValueRow(
                    label = "XP",
                    hint = "progresso de nível, nunca perdido",
                    value = xpValue,
                    onSelect = { xpValue = it },
                )

                Spacer(Modifier.height(10.dp))
                ValueRow(
                    label = "Moedas",
                    hint = "saldo para gastar em prêmios",
                    value = coinValue,
                    onSelect = { coinValue = it },
                )

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Exige foto", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Sem foto, a missão não fecha",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = requiresEvidence, onCheckedChange = { requiresEvidence = it })
                }

                if (requiresEvidence) {
                    Spacer(Modifier.height(16.dp))
                    Text("Prazo para enviar", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WINDOW_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = window == option,
                                onClick = { window = option },
                                label = { Text(labelForMinutes(option)) },
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Intervalo entre cobranças", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CHASE_INTERVAL_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = chaseInterval == option,
                                onClick = { chaseInterval = option },
                                label = { Text("${option}m") },
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Máximo de cobranças", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { option ->
                            FilterChip(
                                selected = maxChases == option,
                                onClick = { maxChases = option },
                                label = { Text("$option") },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Depois de $maxChases cobrança(s) o app para de insistir e marca " +
                            "a missão como não cumprida. Cobrar para sempre viraria " +
                            "fonte de ansiedade, que é o oposto do objetivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        (mission ?: MissionEntity(
                            alarmId = alarmId,
                            title = "",
                            daysMask = selectableDays,
                        )).copy(
                            title = title.trim(),
                            followsAlarmDays = followsAlarm,
                            daysMask = effectiveDays,
                            requiresEvidence = requiresEvidence,
                            evidenceWindowMinutes = window,
                            chaseIntervalMinutes = chaseInterval,
                            maxChases = maxChases,
                            xpValue = xpValue,
                            coinValue = coinValue,
                        )
                    )
                },
                enabled = title.isNotBlank() && effectiveDays != DayMask.NONE,
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ValueRow(label: String, hint: String, value: Int, onSelect: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(6.dp))
            Text(
                "· $hint",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            valueOptionsFor(value).forEach { option ->
                FilterChip(
                    selected = value == option,
                    onClick = { onSelect(option) },
                    label = { Text("$option") },
                )
            }
        }
    }
}

private fun labelForMinutes(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h" else "${minutes}m"
