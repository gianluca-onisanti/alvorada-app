package dev.gianluca.alvoradaapp.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.core.Leveling
import dev.gianluca.alvoradaapp.diagnostics.SystemChecks
import dev.gianluca.alvoradaapp.ui.onboarding.SetupCard
import dev.gianluca.alvoradaapp.ui.onboarding.WelcomeDialog
import dev.gianluca.alvoradaapp.core.StreakSnapshot
import dev.gianluca.alvoradaapp.data.DayMissionRow
import dev.gianluca.alvoradaapp.data.DaySummary
import dev.gianluca.alvoradaapp.data.DayTally
import dev.gianluca.alvoradaapp.data.FolderProgress
import dev.gianluca.alvoradaapp.data.MissionStatus
import dev.gianluca.alvoradaapp.data.PanelRepository
import dev.gianluca.alvoradaapp.data.ProgressSummary
import dev.gianluca.alvoradaapp.ui.components.CategoryStripe
import dev.gianluca.alvoradaapp.ui.components.SectionTitle
import dev.gianluca.alvoradaapp.ui.components.parseColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * O panorama do dia: quais categorias existem e o que foi cumprido em cada uma.
 *
 * É também a tela onde as pendências vivem — a lista separada da Fase 2 foi absorvida
 * aqui, porque "o que falta" e "como foi o dia" são a mesma pergunta feita de dois
 * ângulos, e separá-las obrigava a olhar em dois lugares para saber onde você está.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelScreen(
    onOpenCamera: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    setupRefreshKey: Int = 0,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    val panel = container.panelRepository
    val missions = container.missionRepository
    val scope = rememberCoroutineScope()

    // Recontados a cada volta para o app: o usuário sai daqui para uma tela do sistema
    // e retorna, e o aviso precisa refletir o que ele acabou de conceder.
    val pendingSetup = remember(setupRefreshKey) {
        SystemChecks(context).runAll().count { !it.ok }
    }

    // `null` enquanto o DataStore não respondeu — sem isso as boas-vindas piscariam na
    // tela de quem já as dispensou, no intervalo entre compor e ler o disco.
    val welcomeSeen by container.preferences.welcomeSeen.collectAsState(initial = null)
    if (welcomeSeen == false) {
        WelcomeDialog(
            onConfigure = {
                scope.launch { container.preferences.markWelcomeSeen() }
                onOpenSettings()
            },
            onDismiss = { scope.launch { container.preferences.markWelcomeSeen() } },
        )
    }

    val today = remember { PanelRepository.today() }
    val summary by panel.observeDay(today).collectAsState(
        initial = DaySummary(today, emptyList())
    )
    val open by panel.observeOpen().collectAsState(initial = emptyList())
    val tallies by panel.observeRecentTallies(7).collectAsState(initial = emptyList())
    val progress by container.pointsRepository.observeProgress().collectAsState(
        initial = ProgressSummary(0, Leveling.progressOf(0), 0, StreakSnapshot(0, 0, 2))
    )
    val xpToday by container.pointsRepository.observeXpToday().collectAsState(initial = 0)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Painel") },
                actions = {
                    // Ajustes vive aqui, e não numa aba: é tela de configuração e
                    // diagnóstico, aberta em dias raros. Uma aba permanente sugeriria
                    // que fizesse parte da rotina diária, e ocuparia o lugar de algo
                    // que faz.
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Build, contentDescription = "Ajustes")
                    }
                },
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
            if (pendingSetup > 0) {
                item { SetupCard(pendingSetup, onOpenSettings) }
            }

            item { DayHeader(today, summary.completed, summary.total, summary.ratio) }

            item { ProgressCard(progress, xpToday) }

            if (tallies.isNotEmpty()) {
                item { WeekStrip(tallies) }
            }

            if (open.isNotEmpty()) {
                item {
                    SectionTitle(
                        text = "Precisa de você",
                        trailing = "${open.size}",
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(open, key = { "open-${it.instanceId}" }) { row ->
                    OpenRow(
                        row = row,
                        now = now,
                        onPhoto = { onOpenCamera(row.instanceId) },
                        onComplete = { scope.launch { missions.complete(row.instanceId) } },
                    )
                }
            }

            if (summary.isEmpty) {
                item { EmptyDay() }
            } else {
                item {
                    SectionTitle(
                        text = "Categorias de hoje",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(summary.folders, key = { it.folderId }) { folder ->
                    FolderCard(folder)
                }
            }

            // Fase 4: XP do dia, nível e streak entram logo abaixo do cabeçalho.
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DayHeader(date: String, completed: Int, total: Int, ratio: Float) {
    Column {
        Text(
            text = formatFullDate(date).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$completed",
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = " de $total missões",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
    }
}

/**
 * Nível, moedas e sequência.
 *
 * A sequência mostra os escudos restantes junto do número — saber que existe uma
 * rede é o que impede um dia ruim de virar "já era, desisti". Um escudo invisível
 * não protege ninguém do desânimo, só do contador.
 */
@Composable
private fun ProgressCard(progress: ProgressSummary, xpToday: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Nível ${progress.level.level}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${progress.level.xpIntoLevel}/${progress.level.xpNeededForNext} XP" +
                            if (xpToday > 0) "  ·  +$xpToday hoje" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${progress.coins}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("moedas", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress.level.ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = when (progress.streak.current) {
                        0 -> "Sem sequência ativa"
                        1 -> "1 dia seguido"
                        else -> "${progress.streak.current} dias seguidos"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${progress.streak.shields} escudo(s)",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (progress.streak.longest > progress.streak.current) {
                Text(
                    "Recorde: ${progress.streak.longest} dias",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * Sete quadrados, um por dia. A opacidade é a fração cumprida.
 *
 * Sem números e sem vermelho de propósito: a leitura útil é a textura da semana, não
 * a nota de cada dia. Um dia fraco no meio de seis bons some no conjunto, que é
 * exatamente o efeito desejado.
 */
@Composable
private fun WeekStrip(tallies: List<DayTally>) {
    val accent = MaterialTheme.colorScheme.secondary
    val empty = MaterialTheme.colorScheme.surfaceVariant

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Últimos 7 dias", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tallies.forEach { tally ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (tally.total == 0) empty
                                    else accent.copy(alpha = 0.25f + 0.75f * tally.ratio)
                                )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = weekdayInitial(tally.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenRow(
    row: DayMissionRow,
    now: Long,
    onPhoto: () -> Unit,
    onComplete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryStripe(row.folderColor)
            if (!row.requiresEvidence) {
                Checkbox(checked = false, onCheckedChange = { onComplete() })
            } else {
                Spacer(Modifier.size(14.dp))
            }
            Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
                Text(row.missionTitle, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = buildString {
                        append(row.folderName)
                        if (row.date != PanelRepository.today()) append("  ·  ${row.date}")
                        if (row.chaseCount > 0) append("  ·  ${row.chaseCount}ª cobrança")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                row.evidenceDeadline?.let { deadline ->
                    val overdue = deadline <= now
                    Text(
                        text = if (overdue) {
                            "prazo vencido — até ${row.maxChases} cobranças"
                        } else {
                            "restam ${(deadline - now) / 60_000}min para a foto"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            if (row.requiresEvidence) {
                FilledTonalButton(
                    onClick = onPhoto,
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Foto")
                }
            } else {
                Spacer(Modifier.size(6.dp))
            }
        }
    }
}

@Composable
private fun FolderCard(folder: FolderProgress) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(parseColor(folder.colorHex))
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${folder.completed}/${folder.total}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { folder.ratio },
                color = parseColor(folder.colorHex),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(12.dp))
            folder.rows.forEach { row -> MissionLine(row) }
        }
    }
}

@Composable
private fun MissionLine(row: DayMissionRow) {
    val (icon, tint, note) = when (row.status) {
        MissionStatus.COMPLETED -> Triple(
            Icons.Filled.CheckCircle,
            MaterialTheme.colorScheme.secondary,
            if (row.wasLate) "cumprida com atraso" else null,
        )

        MissionStatus.AWAITING_EVIDENCE -> Triple(
            Icons.Filled.PhotoCamera,
            MaterialTheme.colorScheme.tertiary,
            "esperando foto",
        )

        MissionStatus.PENDING -> Triple(
            Icons.Filled.RadioButtonUnchecked,
            MaterialTheme.colorScheme.onSurfaceVariant,
            null,
        )

        // Cinza, nunca vermelho: não cumprida é informação, não repreensão.
        MissionStatus.FAILED -> Triple(
            Icons.Filled.RemoveCircleOutline,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "não cumprida",
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Text(
            row.missionTitle,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        note?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyDay() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Nada registrado hoje ainda", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "As missões do dia aparecem aqui depois que o despertador é dispensado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val FULL_DATE = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR)

private fun formatFullDate(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(FULL_DATE) }.getOrDefault(isoDate)

private fun weekdayInitial(isoDate: String): String =
    runCatching {
        LocalDate.parse(isoDate).dayOfWeek
            .getDisplayName(java.time.format.TextStyle.NARROW, PT_BR)
            .uppercase()
    }.getOrDefault("·")
