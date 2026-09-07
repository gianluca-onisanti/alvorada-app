package dev.gianluca.alvoradaapp.ui.alarms

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.alarm.AlarmSoundPlayer
import dev.gianluca.alvoradaapp.core.DayMask
import dev.gianluca.alvoradaapp.core.NextFireCalculator
import dev.gianluca.alvoradaapp.core.Recurrence
import dev.gianluca.alvoradaapp.core.RepeatKind
import dev.gianluca.alvoradaapp.data.AlarmEntity
import dev.gianluca.alvoradaapp.data.FolderEntity
import dev.gianluca.alvoradaapp.data.MissionEntity
import dev.gianluca.alvoradaapp.ui.components.RecurrenceEditor
import dev.gianluca.alvoradaapp.ui.components.formatClock
import dev.gianluca.alvoradaapp.ui.components.formatFireMoment
import dev.gianluca.alvoradaapp.ui.missions.MissionEditorDialog
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

private val SNOOZE_OPTIONS = listOf(1, 3, 5, 10, 15, 20)

private const val DEFAULT_HOUR = 7

/**
 * Data inicial de um despertador único: hoje, se o horário padrão ainda vem; amanhã,
 * se já passou. Nascer com uma data impossível obrigaria a corrigir antes de salvar
 * um alarme que, na esmagadora maioria das vezes, é justamente para amanhã cedo.
 */
private fun defaultOnceDate(now: ZonedDateTime = ZonedDateTime.now()): LocalDate =
    if (now.hour < DEFAULT_HOUR) now.toLocalDate() else now.toLocalDate().plusDays(1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    alarmId: Long,
    onDone: () -> Unit,
    /** Só para um despertador novo — o existente diz por si o que é. */
    startAsOneShot: Boolean = false,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    val repository = container.alarmRepository
    val soundStore = container.soundStore
    val scope = rememberCoroutineScope()

    val missionRepository = container.missionRepository
    val folders by repository.observeFolders().collectAsState(initial = emptyList())

    val missions by remember(alarmId) {
        if (alarmId == NEW_ALARM_ID) flowOf(emptyList())
        else missionRepository.observeMissions(alarmId)
    }.collectAsState(initial = emptyList())

    var editingMission by remember { mutableStateOf<MissionEntity?>(null) }
    var creatingMission by remember { mutableStateOf(false) }

    // `loaded` também é saveable: sem ele, uma rotação de tela recarregaria os valores
    // do banco por cima do que já estava sendo editado.
    var loaded by rememberSaveable { mutableStateOf(false) }
    var hour by rememberSaveable { mutableIntStateOf(DEFAULT_HOUR) }
    var minute by rememberSaveable { mutableIntStateOf(0) }
    // A recorrência é guardada achatada, e não como objeto: `rememberSaveable` só sabe
    // preservar o que cabe num Bundle, e seis primitivos sobrevivem a uma rotação de
    // tela sem precisar de um Saver escrito à mão.
    var daysMask by rememberSaveable { mutableIntStateOf(DayMask.WEEKDAYS) }
    var repeatKind by rememberSaveable {
        mutableStateOf(if (startAsOneShot) RepeatKind.ONCE else RepeatKind.WEEKLY)
    }
    var intervalWeeks by rememberSaveable { mutableIntStateOf(2) }
    var anchorDate by rememberSaveable {
        mutableStateOf(if (startAsOneShot) defaultOnceDate().toString() else null)
    }
    var ordinalMask by rememberSaveable { mutableIntStateOf(0) }
    var monthDaysMask by rememberSaveable { mutableIntStateOf(0) }
    var label by rememberSaveable { mutableStateOf("") }
    var folderId by rememberSaveable { mutableStateOf(0L) }
    var soundUri by rememberSaveable { mutableStateOf<String?>(null) }
    var soundIsSystem by rememberSaveable { mutableStateOf(true) }
    var soundLabel by rememberSaveable { mutableStateOf("Padrão do sistema") }
    var volume by rememberSaveable { mutableIntStateOf(100) }
    var escalate by rememberSaveable { mutableStateOf(true) }
    var vibrate by rememberSaveable { mutableStateOf(true) }
    var snoozeMinutes by rememberSaveable { mutableIntStateOf(5) }
    var maxSnoozes by rememberSaveable { mutableIntStateOf(3) }
    var createdAt by rememberSaveable { mutableStateOf(0L) }
    // Carregado e devolvido intacto: editar o nome de um despertador não deveria
    // desfazer o pulo que você marcou ontem à noite. Se a edição mudar o horário ou a
    // repetição, o próprio agendador deixa de reconhecer o instante e o pulo caduca.
    var skipNextFireAt by rememberSaveable { mutableStateOf<Long?>(null) }

    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(alarmId) {
        if (loaded) return@LaunchedEffect
        val existing = if (alarmId != NEW_ALARM_ID) repository.getAlarm(alarmId) else null
        if (existing != null) {
            hour = existing.hour
            minute = existing.minute
            daysMask = existing.daysMask
            repeatKind = existing.repeatKind
            intervalWeeks = existing.intervalWeeks
            anchorDate = existing.anchorDate
            ordinalMask = existing.ordinalMask
            monthDaysMask = existing.monthDaysMask
            label = existing.label
            folderId = existing.folderId
            soundUri = existing.soundUri
            soundIsSystem = existing.soundIsSystem
            soundLabel = existing.soundLabel ?: "Padrão do sistema"
            volume = existing.volumePercent
            escalate = existing.escalateVolume
            vibrate = existing.vibrate
            snoozeMinutes = existing.snoozeMinutes
            maxSnoozes = existing.maxSnoozes
            skipNextFireAt = existing.skipNextFireAt
            createdAt = existing.createdAt
        } else {
            folderId = repository.ensureDefaultFolder().id
            createdAt = System.currentTimeMillis()
        }
        loaded = true
    }

    val recurrence = Recurrence(
        kind = repeatKind,
        daysMask = daysMask,
        intervalWeeks = intervalWeeks,
        anchorDate = anchorDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        ordinalMask = ordinalMask,
        monthDaysMask = monthDaysMask,
    )
    val isOneShot = recurrence.isOneShot
    val applyRecurrence: (Recurrence) -> Unit = {
        repeatKind = it.kind
        daysMask = it.daysMask
        intervalWeeks = it.intervalWeeks
        anchorDate = it.anchorDate?.toString()
        ordinalMask = it.ordinalMask
        monthDaysMask = it.monthDaysMask
    }

    // Serve à seção de repetição e ao botão Salvar: um despertador sem próximo disparo
    // é um despertador que nunca toca, e salvá-lo seria guardar uma promessa vazia.
    val nextFire = remember(recurrence, hour, minute) {
        NextFireCalculator.nextFireMillis(hour, minute, recurrence, ZonedDateTime.now())
    }

    // Pré-escuta: mesmo caminho de áudio do alarme de verdade (canal de alarme), para
    // você ouvir exatamente como vai soar às 6h. Só que sem repetir.
    val preview = remember { AlarmSoundPlayer(context) }
    var previewing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            preview.stop()
        }
    }

    val systemPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked = result.data?.pickedRingtoneUri()
        val chosen = soundStore.fromSystemPicker(picked)
        soundUri = chosen.uri
        soundIsSystem = chosen.isSystem
        soundLabel = chosen.label
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            soundStore.importUserSound(uri)?.let { chosen ->
                soundUri = chosen.uri
                soundIsSystem = chosen.isSystem
                soundLabel = chosen.label
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isOneShot -> "Autodestruível"
                            alarmId == NEW_ALARM_ID -> "Novo despertador"
                            else -> "Editar"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (alarmId != NEW_ALARM_ID) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Excluir")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ---------------------------------------------------------- hora
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true }
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = formatClock(hour, minute),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Light,
                    )
                    Text(
                        "Toque para alterar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------------------------------------------------------- repetição
            Section(if (isOneShot) "Quando" else "Repetição") {
                RecurrenceEditor(recurrence = recurrence, onChange = applyRecurrence)

                if (isOneShot) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Toca uma única vez. Depois que você desligar — ou depois de " +
                            "acabarem as sonecas — ele se apaga sozinho, sem deixar " +
                            "rastro na lista.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Recorrência de calendário é fácil de configurar errado e difícil de
                // conferir de cabeça. Mostrar o próximo disparo real fecha esse buraco
                // agora, em vez de daqui a três semanas quando ele não tocar.
                if (nextFire != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Próximo disparo: ${formatFireMoment(nextFire)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else if (recurrence.canFire) {
                    // Configuração completa e mesmo assim sem futuro: só acontece com
                    // data única no passado. Sem este aviso, o botão Salvar ficaria
                    // desabilitado sem nenhuma pista do motivo.
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Essa data e hora já passaram — escolha outro dia ou " +
                            "adiante o horário.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            // ---------------------------------------------------------- identificação
            Section("Identificação") {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nome") },
                    placeholder = { Text("Acordar, remédio, treino…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                FolderSelector(
                    folders = folders,
                    selectedId = folderId,
                    onSelect = { folderId = it },
                )
            }

            // ---------------------------------------------------------- som
            Section("Som") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(soundLabel, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        if (previewing) {
                            preview.stop()
                            previewing = false
                        } else {
                            preview.start(
                                soundUri = soundUri?.let(Uri::parse),
                                volumePercent = volume,
                                escalate = false,
                                vibrate = false,
                                loop = false,
                            )
                            previewing = true
                        }
                    }) {
                        Icon(
                            imageVector = if (previewing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (previewing) "Parar" else "Ouvir",
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            systemPicker.launch(ringtonePickerIntent(soundUri, soundStore.defaultAlarmUri()))
                        },
                        label = { Text("Som do sistema") },
                    )
                    AssistChip(
                        onClick = { filePicker.launch(arrayOf("audio/*")) },
                        label = { Text("Arquivo próprio") },
                    )
                }
                Spacer(Modifier.height(16.dp))

                Text("Volume: $volume%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { volume = it.toInt() },
                    valueRange = 10f..100f,
                )
                Text(
                    "Percentual do volume de alarme do aparelho — o app não consegue " +
                        "passar do que o sistema permite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))
                ToggleRow("Volume crescente", escalate) { escalate = it }
                ToggleRow("Vibrar", vibrate) { vibrate = it }
            }

            // ---------------------------------------------------------- soneca
            Section("Soneca") {
                Text("Duração", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SNOOZE_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = snoozeMinutes == option,
                            onClick = { snoozeMinutes = option },
                            label = { Text("${option}m") },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Máximo de sonecas: $maxSnoozes", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = maxSnoozes.toFloat(),
                    onValueChange = { maxSnoozes = it.toInt() },
                    valueRange = 0f..10f,
                    steps = 9,
                )
                Text(
                    text = if (maxSnoozes == 0) {
                        "Sem soneca: só resta desligar."
                    } else {
                        "Depois de $maxSnoozes soneca(s) o botão some. Soneca sem teto é " +
                            "o que transforma despertador em ruído de fundo."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------------------------------------------------------- missões
            Section("Missões") {
                if (isOneShot) {
                    Text(
                        "Um despertador autodestruível não tem missões: ele desaparece " +
                            "logo depois de tocar e levaria junto qualquer cobrança em " +
                            "aberto. Para rotina que precisa ser cobrada, use um " +
                            "despertador padrão.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (alarmId == NEW_ALARM_ID) {
                    Text(
                        "Salve o despertador primeiro — missões precisam de um dono " +
                            "para herdar os dias em que podem existir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (missions.isEmpty()) {
                        Text(
                            "Nenhuma missão. Sem elas o despertador só toca — com elas, " +
                                "ele cobra.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    missions.forEach { mission ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable { editingMission = mission }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (mission.requiresEvidence) {
                                    Icon(
                                        Icons.Filled.PhotoCamera,
                                        contentDescription = "Exige foto",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(mission.title)
                                    Text(
                                        buildString {
                                            append(DayMask.describe(mission.daysMask))
                                            if (mission.requiresEvidence) {
                                                append("  ·  foto em ${mission.evidenceWindowMinutes}min")
                                                append("  ·  até ${mission.maxChases} cobranças")
                                            }
                                            // Só quando foge do padrão: repetir "10 XP ·
                                            // 10 moedas" em toda linha esconderia
                                            // justamente as missões que você diferenciou.
                                            if (!mission.hasDefaultValue) {
                                                append("  ·  ${mission.xpValue} XP")
                                                append("  ·  ${mission.coinValue} moedas")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch { missionRepository.deleteMission(mission) }
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Excluir missão")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AssistChip(
                        onClick = { creatingMission = true },
                        label = { Text("Adicionar missão") },
                    )
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        repository.saveAlarm(
                            AlarmEntity(
                                id = if (alarmId == NEW_ALARM_ID) 0L else alarmId,
                                folderId = folderId,
                                label = label.trim(),
                                hour = hour,
                                minute = minute,
                                daysMask = daysMask,
                                repeatKind = repeatKind,
                                intervalWeeks = intervalWeeks,
                                anchorDate = anchorDate,
                                ordinalMask = ordinalMask,
                                monthDaysMask = monthDaysMask,
                                soundUri = soundUri,
                                soundIsSystem = soundIsSystem,
                                soundLabel = soundLabel,
                                volumePercent = volume,
                                escalateVolume = escalate,
                                vibrate = vibrate,
                                snoozeMinutes = snoozeMinutes,
                                maxSnoozes = maxSnoozes,
                                enabled = true,
                                skipNextFireAt = skipNextFireAt,
                                createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
                            )
                        )
                        onDone()
                    }
                },
                enabled = loaded && folderId != 0L && nextFire != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("Salvar") }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Horário") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    hour = pickerState.hour
                    minute = pickerState.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            },
        )
    }

    if (creatingMission || editingMission != null) {
        MissionEditorDialog(
            mission = editingMission,
            alarmId = alarmId,
            selectableDays = recurrence.missionDayMask,
            daysHint = if (recurrence.kind == RepeatKind.MONTHLY_DAYS) {
                "Este despertador toca por dia do calendário, então a missão vale " +
                    "sempre que ele tocar."
            } else {
                "Limitado aos dias do despertador (${DayMask.describe(recurrence.daysMask)})."
            },
            onDismiss = { creatingMission = false; editingMission = null },
            onConfirm = { mission ->
                scope.launch { missionRepository.saveMission(mission) }
                creatingMission = false
                editingMission = null
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir despertador?") },
            text = { Text("O agendamento é cancelado imediatamente.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.getAlarm(alarmId)?.let { repository.deleteAlarm(it) }
                        onDone()
                    }
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FolderSelector(
    folders: List<FolderEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = folders.firstOrNull { it.id == selectedId }

    Column {
        Text("Pasta", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        AssistChip(
            onClick = { open = true },
            label = { Text(selected?.name ?: "Escolher…") },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(folder.name) },
                    onClick = { onSelect(folder.id); open = false },
                )
            }
        }
    }
}

/** Abre o seletor de toques do sistema já posicionado na escolha atual. */
private fun ringtonePickerIntent(currentUri: String?, defaultUri: Uri?): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Som do despertador")
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri)
        putExtra(
            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
            currentUri?.let(Uri::parse) ?: defaultUri,
        )
    }

private fun Intent.pickedRingtoneUri(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }
