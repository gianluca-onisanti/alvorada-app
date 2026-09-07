package dev.gianluca.alvoradaapp.alarm

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.ui.evidence.EvidenceCameraScreen
import dev.gianluca.alvoradaapp.ui.theme.AlvoradaAlarmTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * A tela que aparece quando o alarme toca.
 *
 * Roda numa tarefa própria (`taskAffinity` no manifest) e em instância única, para
 * que uma cobrança que chega enquanto a tela já está aberta não empilhe uma segunda.
 *
 * Tem três fases. A do meio é a que dá sentido ao app: depois de o som parar, a tela
 * **não some** — ela mostra o que ficou devendo foto e quanto tempo resta. Dispensar
 * o alarme e cumprir a missão passam a ser dois atos distintos.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        // Botão voltar não dispensa alarme — sair exige uma decisão explícita.
        onBackPressedDispatcher.addCallback(this) { /* intencionalmente vazio */ }

        setContent {
            AlvoradaAlarmTheme {
                val ringing by AlarmService.state.collectAsState()
                val pending by AlarmService.pendingEvidence.collectAsState()
                var cameraFor by remember { mutableStateOf<Long?>(null) }

                LaunchedEffect(ringing, pending, cameraFor) {
                    if (ringing == null && pending == null && cameraFor == null) finish()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when {
                        cameraFor != null -> EvidenceCameraScreen(
                            instanceId = cameraFor!!,
                            onFinished = { cameraFor = null },
                            onCancel = { cameraFor = null },
                        )

                        ringing != null -> RingingScreen(
                            state = ringing!!,
                            onSnooze = { AlarmService.snooze(this@AlarmActivity) },
                            onDismiss = { done, doneChecklist ->
                                AlarmService.dismiss(this@AlarmActivity, done, doneChecklist)
                            },
                            onPhoto = { cameraFor = it },
                        )

                        pending != null -> PendingEvidenceScreen(
                            pending = pending!!,
                            onPhoto = { cameraFor = it },
                            onLater = {
                                AlarmService.clearPendingEvidence()
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

// ---------------------------------------------------------------- fase 1: tocando

@Composable
private fun RingingScreen(
    state: RingingState,
    onSnooze: () -> Unit,
    /** (missões marcadas, itens de checklist marcados) — conjuntos separados. */
    onDismiss: (Set<Long>, Set<Long>) -> Unit,
    onPhoto: (Long) -> Unit,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }

    // Missões sem foto podem ser marcadas aqui mesmo — "tomei o remédio" enquanto
    // você está de pé na frente do celular, sem depender de voltar ao app depois.
    val checked: SnapshotStateList<Long> = remember(state.occurrenceId) {
        emptyList<Long>().toMutableStateList()
    }

    // Lista própria pela mesma razão do extra próprio no Intent: os ids das duas
    // tabelas colidem, e um conjunto só marcaria o item errado.
    val checkedChecklist: SnapshotStateList<Long> = remember(state.occurrenceId) {
        emptyList<Long>().toMutableStateList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (state.mode == AlarmMode.CHASE) Icons.Filled.PhotoCamera
                else Icons.Filled.Alarm,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (state.mode == AlarmMode.CHASE) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = now.format(TIME_FORMAT),
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = state.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.mode == AlarmMode.CHASE) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Faltou a evidência",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // ------------------------------------------------------------ missões
        if (state.mode == AlarmMode.CHASE) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.chaseRuns, key = { it.instance.id }) { run ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(run.mission.title, modifier = Modifier.weight(1f))
                            Button(onClick = { onPhoto(run.instance.id) }) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("Foto")
                            }
                        }
                    }
                }
            }
        } else if (state.missions.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.missions, key = { it.id }) { mission ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (mission.requiresEvidence) {
                            Icon(
                                Icons.Filled.PhotoCamera,
                                contentDescription = "Exige foto",
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(20.dp),
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                        } else {
                            Checkbox(
                                checked = mission.id in checked,
                                onCheckedChange = {
                                    if (it) checked.add(mission.id) else checked.remove(mission.id)
                                },
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(mission.title, style = MaterialTheme.typography.bodyLarge)
                            if (mission.requiresEvidence) {
                                Text(
                                    "foto em até ${mission.evidenceWindowMinutes} min",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        // Itens de checklist deste despertador que ainda faltam nesta rodada.
        // Marcar aqui cala o despertador até a rodada virar — é o mesmo gesto que a
        // tela de Checklists oferece, no momento em que ele importa.
        if (state.checklistItems.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Checklist",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            state.checklistItems.forEach { run ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = run.item.id in checkedChecklist,
                        onCheckedChange = {
                            if (it) checkedChecklist.add(run.item.id)
                            else checkedChecklist.remove(run.item.id)
                        },
                    )
                    Text(
                        run.item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ------------------------------------------------------------ ações
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.canSnooze) {
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("Soneca de ${state.snoozeMinutes} min  ·  restam ${state.snoozesLeft}")
                }
                Spacer(Modifier.height(10.dp))
            } else if (state.mode == AlarmMode.WAKE || state.mode == AlarmMode.SNOOZE) {
                Text(
                    "Sem soneca — teto atingido",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
            }

            Button(
                onClick = { onDismiss(checked.toSet(), checkedChecklist.toSet()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Desligar", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------- fase 2: janela de evidência

/**
 * O som parou, mas o compromisso não. Mostra o que falta e o prazo real de cada item —
 * se o tempo acabar, o despertador volta.
 */
@Composable
private fun PendingEvidenceScreen(
    pending: PendingEvidence,
    onPhoto: (Long) -> Unit,
    onLater: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember {
        (context.applicationContext as AlvoradaApp).container.missionRepository
    }
    // Observado, não fotografado: assim que a última foto entra, a lista esvazia e a
    // tela se fecha sozinha.
    val runs by repository.observeAwaitingRuns(pending.occurrenceId)
        .collectAsState(initial = emptyList())

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    LaunchedEffect(runs) {
        if (runs.isEmpty()) onLater()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Spacer(Modifier.height(24.dp))
            Text(
                "Falta a evidência",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Se a foto não chegar no prazo, o despertador volta a tocar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(runs, key = { it.instance.id }) { run ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(run.mission.title, style = MaterialTheme.typography.bodyLarge)
                                run.instance.evidenceDeadline?.let {
                                    Text(
                                        text = remainingLabel(it, now),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (it - now < 5 * 60_000L) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                            Button(onClick = { onPhoto(run.instance.id) }) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("Foto")
                            }
                        }
                    }
                }
            }
        }

        TextButton(
            onClick = onLater,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Fazer depois") }
    }
}

private fun remainingLabel(deadline: Long, now: Long): String {
    val remaining = deadline - now
    if (remaining <= 0) return "prazo vencido — cobrança a caminho"
    val minutes = remaining / 60_000
    val seconds = (remaining % 60_000) / 1000
    return if (minutes >= 1) "restam ${minutes}min" else "restam ${seconds}s"
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
