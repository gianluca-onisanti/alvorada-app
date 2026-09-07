package dev.gianluca.alvoradaapp.ui.alarms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.core.FireOutlook
import dev.gianluca.alvoradaapp.data.AlarmEntity
import dev.gianluca.alvoradaapp.data.FolderEntity
import dev.gianluca.alvoradaapp.data.FolderWithAlarms
import dev.gianluca.alvoradaapp.data.describeRepeat
import dev.gianluca.alvoradaapp.data.isOneShot
import dev.gianluca.alvoradaapp.data.outlook
import dev.gianluca.alvoradaapp.ui.components.AlvoradaTopBar
import dev.gianluca.alvoradaapp.ui.components.CategoryStripe
import dev.gianluca.alvoradaapp.ui.components.ColorDot
import dev.gianluca.alvoradaapp.ui.components.formatClock
import dev.gianluca.alvoradaapp.ui.components.formatFireClock
import dev.gianluca.alvoradaapp.ui.components.formatTimeUntil
import dev.gianluca.alvoradaapp.ui.components.parseColor
import dev.gianluca.alvoradaapp.ui.folders.FolderDialog
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun AlarmListScreen(
    onEditAlarm: (Long) -> Unit,
    onCreateAlarm: (oneShot: Boolean) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    val repository = container.alarmRepository
    val scope = rememberCoroutineScope()

    val folders by repository.observeFoldersWithAlarms().collectAsState(initial = emptyList())

    var editingFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var choosingKind by remember { mutableStateOf(false) }
    var disabling by remember { mutableStateOf<AlarmEntity?>(null) }

    Scaffold(
        // Transparente para o gradiente de `AlvoradaBackground` chegar até aqui:
        // o padrão do Scaffold é `background` opaco, que cobriria o fundo inteiro
        // e deixaria as superfícies de vidro sem nada para deixar passar.
        containerColor = Color.Transparent,
        // Obrigatório junto do container transparente: o Scaffold deriva o
        // contentColor do containerColor, e `contentColorFor(Transparent)` não
        // resolve nenhum papel do tema — o texto herdaria preto sobre o fundo
        // escuro e simplesmente desapareceria.
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            AlvoradaTopBar("Relógio", onOpenDrawer) {
                IconButton(onClick = { creatingFolder = true }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "Nova pasta")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { choosingKind = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Novo despertador")
            }
        },
    ) { padding ->
        if (folders.isEmpty()) {
            EmptyState(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            folders.forEach { group ->
                item(key = "folder-${group.folder.id}") {
                    FolderHeader(
                        group = group,
                        nextFire = group.alarms
                            .mapNotNull { repository.nextFireOf(it) }
                            .minOrNull(),
                        onToggleCollapsed = {
                            scope.launch {
                                repository.setFolderCollapsed(
                                    group.folder.id,
                                    !group.folder.collapsed,
                                )
                            }
                        },
                        onRename = { editingFolder = group.folder },
                        onDelete = { scope.launch { repository.deleteFolder(group.folder) } },
                    )
                }

                if (group.folder.collapsed) return@forEach

                if (group.alarms.isEmpty()) {
                    item(key = "empty-${group.folder.id}") {
                        Text(
                            "Nenhum despertador nesta pasta",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
                        )
                    }
                }

                items(
                    count = group.alarms.size,
                    key = { index -> "alarm-${group.alarms[index].id}" },
                ) { index ->
                    val alarm = group.alarms[index]
                    AlarmRow(
                        alarm = alarm,
                        folderColor = group.folder.colorHex,
                        outlook = repository.outlookOf(alarm),
                        onClick = { onEditAlarm(alarm.id) },
                        onToggle = { enabled ->
                            // Ligar é uma decisão só; desligar são duas — por isso a
                            // pergunta só aparece no caminho de desligar. Um despertador
                            // de uma vez só não tem "próxima": pular a única ativação
                            // que ele tem seria a mesma coisa que apagá-lo.
                            when {
                                enabled || alarm.isOneShot ->
                                    scope.launch { repository.setAlarmEnabled(alarm, enabled) }

                                else -> disabling = alarm
                            }
                        },
                        onCancelSkip = { scope.launch { repository.clearSkipNextFire(alarm) } },
                    )
                }

                item(key = "gap-${group.folder.id}") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    if (choosingKind) {
        AlarmKindDialog(
            onPick = { oneShot ->
                choosingKind = false
                onCreateAlarm(oneShot)
            },
            onDismiss = { choosingKind = false },
        )
    }

    disabling?.let { alarm ->
        DisableAlarmDialog(
            alarm = alarm,
            outlook = repository.outlookOf(alarm),
            onSkipNext = {
                scope.launch { repository.skipNextFire(alarm) }
                disabling = null
            },
            onDisable = {
                scope.launch { repository.setAlarmEnabled(alarm, false) }
                disabling = null
            },
            onDismiss = { disabling = null },
        )
    }

    if (creatingFolder) {
        FolderDialog(
            folder = null,
            onDismiss = { creatingFolder = false },
            onConfirm = { folder ->
                scope.launch { repository.saveFolder(folder) }
                creatingFolder = false
            },
        )
    }

    editingFolder?.let { folder ->
        FolderDialog(
            folder = folder,
            onDismiss = { editingFolder = null },
            onConfirm = { updated ->
                scope.launch { repository.saveFolder(updated) }
                editingFolder = null
            },
        )
    }
}

@Composable
private fun FolderHeader(
    group: FolderWithAlarms,
    nextFire: Long?,
    onToggleCollapsed: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Uma seta só, girando, em vez de dois ícones trocando: o movimento diz para onde
    // o conteúdo foi, o que dois glifos distintos não conseguem dizer.
    val chevronRotation by animateFloatAsState(
        targetValue = if (group.folder.collapsed) -90f else 0f,
        label = "chevron",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleCollapsed)
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = if (group.folder.collapsed) "Expandir pasta" else "Recolher pasta",
            modifier = Modifier
                .size(20.dp)
                .rotate(chevronRotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(6.dp))
        ColorDot(group.folder.colorHex)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = group.folder.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            // Recolhida, a pasta ainda precisa responder "quando toca o próximo?" —
            // senão recolher custaria informação, e ninguém recolheria.
            if (group.folder.collapsed && nextFire != null) {
                Text(
                    text = formatTimeUntil(nextFire),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Text(
            text = "${group.alarms.count { it.enabled }}/${group.alarms.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Ações da pasta")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Renomear") },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Excluir pasta e despertadores") },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

/**
 * Uma linha da lista.
 *
 * Sem menu de três pontos: excluir mora no editor, a um toque de distância, e o menu
 * gastava a largura de um botão inteiro para oferecer uma ação que quase nunca se usa —
 * espremendo a chave, que é a ação de todo dia.
 *
 * A cor da pasta aparece na faixa lateral e na chave. Num app em que as categorias são
 * a forma de organizar a rotina, saber a que categoria um despertador pertence não
 * deveria exigir subir os olhos até o cabeçalho do grupo.
 */
@Composable
private fun AlarmRow(
    alarm: AlarmEntity,
    folderColor: String,
    outlook: FireOutlook,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onCancelSkip: () -> Unit,
) {
    val dimmed = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = parseColor(folderColor)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Apagada quando o despertador está desligado: a faixa marca presença, e
            // um despertador desativado não está presente na rotina.
            CategoryStripe(
                colorHex = folderColor,
                modifier = Modifier.alpha(if (alarm.enabled) 1f else 0.25f),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatClock(alarm.hour, alarm.minute),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Light,
                        // Desligado fica riscado em vez de sumir: a informação continua
                        // legível, só deixa de valer.
                        textDecoration = if (alarm.enabled) null else TextDecoration.LineThrough,
                        color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else dimmed,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = alarm.label.ifBlank { "Sem nome" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else dimmed,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(alarm.describeRepeat())
                        alarm.soundLabel?.let { append("  ·  ").append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = dimmed,
                )
                outlook.nextFire?.let { next ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatTimeUntil(next),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                }

                // O pulo precisa se anunciar e se desfazer no mesmo lugar. Sem isto, a
                // chave ficaria ligada e o despertador não tocaria amanhã — a falha mais
                // assustadora que um app de despertador pode ter.
                outlook.skipped?.let { skipped ->
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.clickable(onClick = onCancelSkip),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Cancelar o pulo",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = "${formatFireClock(skipped)} pulado · toque para desfazer",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                // Pela mesma razão do bloco acima: um despertador que não toca precisa
                // dizer por quê. A diferença é que este não oferece desfazer — quem
                // calou foi o checklist, e o caminho de destravar é desmarcar o item
                // lá, não aqui.
                outlook.suppressedUntil?.let { until ->
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Checklist,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = "Checklist cumprido · volta ${formatTimeUntil(until)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = accent,
                    checkedBorderColor = accent,
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.padding(end = 16.dp),
            )
        }
    }
}

/**
 * Desligar, mas até quando?
 *
 * A chave sozinha só sabia dizer "para sempre", e era usada para as duas coisas: a
 * folga de amanhã e o despertador que não serve mais. Quem usava para a folga precisava
 * lembrar de religar — e descobria que esqueceu na manhã seguinte, dormindo demais.
 * Separar as duas intenções custa um toque a mais e devolve essa garantia.
 */
@Composable
private fun DisableAlarmDialog(
    alarm: AlarmEntity,
    outlook: FireOutlook,
    onSkipNext: () -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Quando ele voltaria, se você pular este toque. Calculado com a mesma conta que
    // o pulo de verdade usa, para a promessa da tela ser a que o agendador vai cumprir.
    val afterSkip = remember(alarm, outlook.nextFire) {
        outlook.nextFire?.let { alarm.copy(skipNextFireAt = it).outlook().nextFire }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Desligar despertador") },
        text = {
            Column {
                // Só um toque de cada vez: o que fica guardado é o instante pulado, e
                // trocá-lo por outro devolveria o primeiro à agenda em silêncio. Quem
                // já pulou e quer pular de novo tem o caminho honesto ao lado.
                if (outlook.isSkipping) {
                    Text(
                        text = outlook.skipped
                            ?.let { "O toque de ${formatFireClock(it)} já está pulado." }
                            .orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                } else if (outlook.nextFire != null) {
                    OptionRow(
                        title = "Desligar próxima ativação",
                        body = "Pula só o toque de ${formatFireClock(outlook.nextFire)}.",
                        note = afterSkip?.let { "Volta a tocar ${formatFireClock(it)}" },
                        onClick = onSkipNext,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                OptionRow(
                    title = "Desligar totalmente",
                    body = "Fica desligado até você ligar de novo.",
                    note = null,
                    onClick = onDisable,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** A escolha do "+": um despertador que fica, ou um que se apaga depois de tocar. */
@Composable
private fun AlarmKindDialog(onPick: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Que tipo de despertador?") },
        text = {
            Column {
                OptionRow(
                    icon = Icons.Filled.Repeat,
                    title = "Despertador padrão",
                    body = "Repete nos dias que você escolher e pode cobrar missões.",
                    note = null,
                    onClick = { onPick(false) },
                )
                Spacer(Modifier.height(4.dp))
                OptionRow(
                    icon = Icons.Filled.AutoDelete,
                    title = "Despertador autodestruível",
                    body = "Toca uma vez, na data que você marcar, e some depois. " +
                        "Sem missões.",
                    note = null,
                    onClick = { onPick(true) },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Uma escolha de diálogo: título, explicação e, quando cabe, a consequência em verde. */
@Composable
private fun OptionRow(
    title: String,
    body: String,
    note: String?,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp, top = 2.dp),
            )
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (note != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    note,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nenhum despertador ainda", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Toque no + para criar o primeiro. Pastas agrupam despertadores por " +
                "área da rotina — manhã, remédios, treino.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal const val NEW_ALARM_ID = -1L
