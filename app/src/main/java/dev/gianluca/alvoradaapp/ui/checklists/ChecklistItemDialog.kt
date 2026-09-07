package dev.gianluca.alvoradaapp.ui.checklists

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gianluca.alvoradaapp.data.AlarmEntity
import dev.gianluca.alvoradaapp.data.ChecklistItemEntity
import dev.gianluca.alvoradaapp.data.MissionEntity
import dev.gianluca.alvoradaapp.ui.components.formatClock

private val VALUE_OPTIONS = listOf(5, 10, 20, 40)

/** Um valor restaurado de backup pode não estar na escala. Ver `MissionEditorDialog`. */
private fun valueOptionsFor(current: Int): List<Int> =
    (VALUE_OPTIONS + current).distinct().sorted()

/**
 * Editor de um item de checklist.
 *
 * Quase o mesmo formulário de `MissionEditorDialog`, sem o bloco de evidência: um
 * item de checklist não exige foto nem gera cobrança. Cobrança é dívida de missão de
 * despertador — aqui o prazo é o da rodada, e não cumprir custa apenas não ter
 * rendido os pontos.
 *
 * A escolha de despertador é a peça nova, e a opção "nenhum" é o padrão de propósito:
 * a maioria dos itens de uma lista de manutenção não merece acordar ninguém.
 */
@Composable
fun ChecklistItemDialog(
    item: ChecklistItemEntity?,
    checklistId: Long,
    /** Despertadores disponíveis para vincular. Autodestruíveis não entram. */
    alarms: List<AlarmEntity>,
    onDismiss: () -> Unit,
    onConfirm: (ChecklistItemEntity) -> Unit,
) {
    var title by rememberSaveable(item?.id) { mutableStateOf(item?.title.orEmpty()) }
    var notes by rememberSaveable(item?.id) { mutableStateOf(item?.notes.orEmpty()) }
    var alarmId by rememberSaveable(item?.id) { mutableStateOf(item?.alarmId) }
    var xp by rememberSaveable(item?.id) {
        mutableStateOf(item?.xpValue ?: MissionEntity.DEFAULT_XP)
    }
    var coins by rememberSaveable(item?.id) {
        mutableStateOf(item?.coinValue ?: MissionEntity.DEFAULT_COINS)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Novo item" else "Editar item") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("O que fazer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                Text("Despertador", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Marcar este item cala o despertador dele até a próxima rodada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = alarmId == null,
                        onClick = { alarmId = null },
                        label = { Text("Nenhum — marco à mão") },
                    )
                    alarms.forEach { alarm ->
                        FilterChip(
                            selected = alarmId == alarm.id,
                            onClick = { alarmId = alarm.id },
                            label = {
                                Text("${formatClock(alarm.hour, alarm.minute)} · ${alarm.label}")
                            },
                        )
                    }
                }
                if (alarms.isEmpty()) {
                    Text(
                        "Nenhum despertador cadastrado ainda. Crie um no Relógio para " +
                            "poder vincular.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("Quanto vale", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                ValueRow("XP", "progresso de nível", xp) { xp = it }
                Spacer(Modifier.height(8.dp))
                ValueRow("Moedas", "saldo para prêmios", coins) { coins = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onConfirm(
                        item?.copy(
                            title = title.trim(),
                            notes = notes.trim(),
                            alarmId = alarmId,
                            xpValue = xp,
                            coinValue = coins,
                        ) ?: ChecklistItemEntity(
                            checklistId = checklistId,
                            title = title.trim(),
                            notes = notes.trim(),
                            alarmId = alarmId,
                            xpValue = xp,
                            coinValue = coins,
                        )
                    )
                },
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
        Spacer(Modifier.height(4.dp))
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
