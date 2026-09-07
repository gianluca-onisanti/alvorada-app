package dev.gianluca.alvoradaapp.diagnostics

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.ui.components.AlvoradaTopBar
import dev.gianluca.alvoradaapp.ui.settings.AboutCard
import dev.gianluca.alvoradaapp.ui.settings.BackupCard
import dev.gianluca.alvoradaapp.ui.settings.ThemeCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Tela da Fase 0. Antes de existir qualquer despertador de verdade, ela responde a
 * única pergunta que importa: **este aparelho deixa o alarme tocar?**
 *
 * O teste de 1 minuto é o instrumento — agende, bloqueie a tela, espere. Se não
 * tocar aqui, não vai tocar às 6h da manhã.
 */
@Composable
fun DiagnosticsScreen(refreshKey: Int, onOpenDrawer: () -> Unit) {
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
        // Ajustes é destino do menu agora, e não uma tela empilhada sobre o
        // Painel: o ícone é o de menu, e não a seta de voltar. Quem chegou pelo
        // aviso de pendências volta pelo botão de voltar do sistema.
        topBar = { AlvoradaTopBar("Ajustes", onOpenDrawer) },
    ) { padding ->
        DiagnosticsContent(refreshKey = refreshKey, modifier = Modifier.padding(padding))
    }
}

@Composable
private fun DiagnosticsContent(refreshKey: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val checks = remember(refreshKey) { SystemChecks(context) }
    val results = remember(refreshKey) { checks.runAll() }
    val oemWarning = remember { checks.oemWarning() }
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    val scheduler = container.alarmScheduler
    val seeder = remember { TestAlarmSeeder(container.db, scheduler) }
    val scope = rememberCoroutineScope()

    var lastScheduled by remember { mutableStateOf<Long?>(null) }
    var seededFor by remember { mutableStateOf<Long?>(null) }
    var seedMessage by remember { mutableStateOf<String?>(null) }
    var sweepMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Cada item abaixo é uma forma silenciosa de o despertador falhar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(results, key = { it.id }) { check ->
            CheckCard(check)
        }

        if (oemWarning != null) {
            item { OemCard(oemWarning) }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Teste de fogo", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Agende, bloqueie a tela e guarde o celular. A tela do alarme " +
                            "precisa acender sozinha e o som tocar mesmo no silencioso.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { lastScheduled = scheduler.scheduleTest(60) }) {
                            Text("Em 1 minuto")
                        }
                        OutlinedButton(onClick = { lastScheduled = scheduler.scheduleTest(10) }) {
                            Text("Em 10 s")
                        }
                    }
                    lastScheduled?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Agendado para ${formatTime(it)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Teste de reboot", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "O teste acima não passa pelo banco e some ao reiniciar. Este cria " +
                            "um despertador real: agende, reinicie o celular e confira se ele " +
                            "ainda toca. É a verificação que pega o pior bug possível — " +
                            "alarmes que somem em silêncio depois de um reboot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                seededFor = seeder.seed(5)
                                seedMessage = null
                            }
                        }) {
                            Text("Criar p/ daqui a 5 min")
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val removed = seeder.clear()
                                seededFor = null
                                seedMessage = "Removido(s): $removed"
                            }
                        }) {
                            Text("Limpar")
                        }
                    }
                    seededFor?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Vai tocar às ${formatTime(it)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    seedMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Varredura diária", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Normalmente roda sozinha às 3h: fecha pendências de dias já " +
                            "encerrados e avalia a sequência. Aqui dá para forçar — " +
                            "sem isso, testar streak exigiria esperar a virada do dia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = {
                        scope.launch {
                            val closed = container.missionRepository.sweepStale()
                            container.pointsRepository.evaluateStreak()
                            sweepMessage = "Fechadas: $closed · sequência reavaliada"
                        }
                    }) {
                        Text("Rodar agora")
                    }
                    sweepMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item { ThemeCard() }

        item { BackupCard() }

        item {
            AboutCard()
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CheckCard(check: DiagnosticCheck) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (check.ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = if (check.ok) "Tudo certo" else "Precisa de atenção",
                tint = if (check.ok) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(check.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    check.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!check.ok && check.fixIntent != null) {
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    check.fixIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text(check.fixLabel ?: "Corrigir")
                    }
                }
            }
        }
    }
}

@Composable
private fun OemCard(warning: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Ajuste do fabricante", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "O Android não expõe estas telas por intent — é preciso ir a pé nas " +
                    "configurações do sistema.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
