package dev.gianluca.alvoradaapp.ui.checklists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.core.DayMask
import dev.gianluca.alvoradaapp.core.Recurrence
import dev.gianluca.alvoradaapp.data.AlarmEntity
import dev.gianluca.alvoradaapp.data.ChecklistEntity
import dev.gianluca.alvoradaapp.data.ChecklistItemEntity
import dev.gianluca.alvoradaapp.data.FolderEntity
import dev.gianluca.alvoradaapp.data.isOneShot
import dev.gianluca.alvoradaapp.data.recurrence
import dev.gianluca.alvoradaapp.data.withRecurrence
import dev.gianluca.alvoradaapp.ui.components.ColorDot
import dev.gianluca.alvoradaapp.ui.components.RecurrenceEditor
import dev.gianluca.alvoradaapp.ui.components.SectionTitle
import dev.gianluca.alvoradaapp.ui.components.formatClock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

const val NEW_CHECKLIST_ID = -1L

/**
 * Criação e edição de um checklist.
 *
 * A frequência reusa o `RecurrenceEditor` do despertador sem uma linha de adaptação:
 * os quatro modos que ele já oferece — semanal, a cada N semanas, semana do mês, dias
 * do mês — são exatamente diário, semanal, quinzenal e mensal. Inventar um seletor de
 * frequência próprio daria menos poder e mais código.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistEditorScreen(checklistId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    val repository = container.checklistRepository
    val alarmRepository = container.alarmRepository
    val scope = rememberCoroutineScope()

    val isNew = checklistId == NEW_CHECKLIST_ID
    var loaded by remember { mutableStateOf(isNew) }
    var savedId by remember { mutableStateOf(if (isNew) 0L else checklistId) }

    var name by rememberSaveable { mutableStateOf("") }
    var folderId by rememberSaveable { mutableStateOf(0L) }
    var recurrence by remember { mutableStateOf(Recurrence.weekly(DayMask.EVERY_DAY)) }
    var editingItem by remember { mutableStateOf<ChecklistItemEntity?>(null) }
    var addingItem by remember { mutableStateOf(false) }

    val folders by alarmRepository.observeFolders().collectAsState(initial = emptyList())
    val foldersWithAlarms by alarmRepository.observeFoldersWithAlarms()
        .collectAsState(initial = emptyList())

    // Autodestruíveis não podem ser vinculados: eles se apagam no primeiro toque e
    // levariam junto um vínculo que a rodada seguinte ainda esperaria encontrar.
    val linkableAlarms: List<AlarmEntity> = foldersWithAlarms
        .flatMap { it.alarms }
        .filterNot { it.isOneShot }

    // O fluxo troca conforme o checklist ganha id, mas `collectAsState` precisa ser
    // chamado sempre: uma chamada composable condicional muda a estrutura da
    // composição entre recomposições, que é exatamente o que o Compose proíbe.
    val itemsFlow = remember(savedId) {
        if (savedId == 0L) flowOf(emptyList()) else repository.observeItems(savedId)
    }
    val itemList by itemsFlow.collectAsState(initial = emptyList())

    val timeState = rememberTimePickerState(initialHour = 22, initialMinute = 0, is24Hour = true)

    LaunchedEffect(checklistId) {
        if (isNew) {
            folderId = alarmRepository.ensureDefaultFolder().id
            loaded = true
            return@LaunchedEffect
        }
        val existing = repository.getChecklist(checklistId) ?: return@LaunchedEffect
        name = existing.name
        folderId = existing.folderId
        recurrence = existing.recurrence()
        timeState.hour = existing.dueHour
        timeState.minute = existing.dueMinute
        loaded = true
    }

    if (!loaded) return

    fun persist(onSaved: (Long) -> Unit = {}) {
        scope.launch {
            val base = if (savedId == 0L) {
                ChecklistEntity(
                    folderId = folderId,
                    name = name.trim(),
                    dueHour = timeState.hour,
                    dueMinute = timeState.minute,
                    createdAt = System.currentTimeMillis(),
                )
            } else {
                repository.getChecklist(savedId)!!.copy(
                    folderId = folderId,
                    name = name.trim(),
                    dueHour = timeState.hour,
                    dueMinute = timeState.minute,
                )
            }
            val id = repository.saveChecklist(base.withRecurrence(recurrence))
            savedId = id
            onSaved(id)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Novo checklist" else "Editar checklist") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    TextButton(
                        enabled = name.isNotBlank() && recurrence.canFire,
                        onClick = { persist { onDone() } },
                    ) { Text("Salvar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do checklist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                SectionTitle("Categoria")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    folders.forEach { folder ->
                        FolderChip(folder, folderId == folder.id) { folderId = folder.id }
                    }
                }
            }

            item {
                SectionTitle("Com que frequência")
                RecurrenceEditor(recurrence = recurrence, onChange = { recurrence = it })
            }

            item {
                SectionTitle("Vence às")
                Text(
                    "O horário em que a rodada fecha. Depois dele o checklist aparece " +
                        "como vencido, mas ainda dá para marcar até a rodada virar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TimePicker(state = timeState)
            }

            item {
                SectionTitle("Itens", trailing = "${itemList.size}")
                if (savedId == 0L) {
                    Text(
                        "Salve o checklist primeiro — itens precisam de um dono.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(itemList, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { editingItem = item }
                        ) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge)
                            val subtitle = buildString {
                                val alarm = linkableAlarms.firstOrNull { it.id == item.alarmId }
                                if (alarm != null) {
                                    append(formatClock(alarm.hour, alarm.minute))
                                    append(" · ")
                                    append(alarm.label)
                                } else {
                                    append("Sem despertador")
                                }
                                if (!item.hasDefaultValue) {
                                    append("  ·  ${item.xpValue} XP · ${item.coinValue} moedas")
                                }
                            }
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (item.alarmId != null) {
                            Icon(
                                Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { scope.launch { repository.deleteItem(item) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Excluir item")
                        }
                    }
                }
            }

            item {
                AssistChip(
                    enabled = savedId != 0L,
                    onClick = { addingItem = true },
                    label = { Text("Adicionar item") },
                )
                Spacer(Modifier.height(60.dp))
            }
        }
    }

    if (addingItem || editingItem != null) {
        ChecklistItemDialog(
            item = editingItem,
            checklistId = savedId,
            alarms = linkableAlarms,
            onDismiss = { addingItem = false; editingItem = null },
            onConfirm = { updated ->
                scope.launch { repository.saveItem(updated) }
                addingItem = false
                editingItem = null
            },
        )
    }
}

@Composable
private fun FolderChip(folder: FolderEntity, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = { ColorDot(folder.colorHex) },
        label = { Text(folder.name) },
    )
}
