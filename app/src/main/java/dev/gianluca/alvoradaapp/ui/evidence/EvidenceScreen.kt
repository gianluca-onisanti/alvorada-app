package dev.gianluca.alvoradaapp.ui.evidence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.data.EvidenceBoard
import dev.gianluca.alvoradaapp.data.EvidenceTask
import dev.gianluca.alvoradaapp.data.PanelRepository
import dev.gianluca.alvoradaapp.ui.components.CategoryStripe
import dev.gianluca.alvoradaapp.ui.components.ColorDot
import dev.gianluca.alvoradaapp.ui.components.EmptyState
import dev.gianluca.alvoradaapp.ui.components.SectionTitle
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Onde as fotos devidas viram fotos entregues.
 *
 * O Painel mostra o dia inteiro; esta tela mostra só a dívida fotográfica, que é o
 * único tipo de pendência que o app cobra de volta despertando. Ter um lugar próprio
 * para ela significa poder abrir o app já sabendo o que resolver, sem varrer o dia
 * atrás do que falta.
 *
 * A segunda metade — o que já foi entregue hoje — não é enfeite: uma tela que só mostra
 * débito transforma o app numa lista de cobranças. O que foi feito precisa ocupar
 * espaço na mesma tela em que o que falta aparece.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceScreen(onOpenCamera: (Long) -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    val today = remember { PanelRepository.today() }

    val board by container.panelRepository.observeEvidenceBoard(today)
        .collectAsState(initial = EvidenceBoard(emptyList(), emptyList()))

    // Os prazos são a informação que muda sozinha nesta tela — sem o tique, "restam
    // 3min" ficaria congelado enquanto o prazo escorre.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Evidências") }) },
    ) { padding ->
        if (board.isEmpty) {
            EmptyState(
                icon = Icons.Filled.PhotoCamera,
                title = "Nada esperando foto",
                body = "Quando um despertador for dispensado com missões que exigem " +
                    "evidência, elas aparecem aqui até a foto chegar.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (board.pending.isNotEmpty()) {
                item {
                    SectionTitle(
                        text = "Faltando",
                        trailing = "${board.pending.size}",
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                items(board.pending, key = { "pending-${it.row.instanceId}" }) { task ->
                    PendingCard(
                        task = task,
                        now = now,
                        onPhoto = { onOpenCamera(task.row.instanceId) },
                    )
                }
            }

            if (board.done.isNotEmpty()) {
                item {
                    SectionTitle(
                        text = "Entregues hoje",
                        trailing = "${board.done.size}",
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = if (board.pending.isEmpty()) 0.dp else 12.dp),
                    )
                }
                items(board.done, key = { "done-${it.row.instanceId}" }) { task ->
                    DoneCard(task)
                }
            }

            if (board.pending.isEmpty()) {
                item { AllClearCard() }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ----------------------------------------------------------------------- pendente

@Composable
private fun PendingCard(task: EvidenceTask, now: Long, onPhoto: () -> Unit) {
    val row = task.row
    val overdue = row.evidenceDeadline?.let { it <= now } == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 76.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryStripe(row.folderColor)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    row.missionTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(row.folderName)
                        if (row.date != PanelRepository.today()) {
                            append("  ·  ").append(formatShortDate(row.date))
                        }
                        if (row.chaseCount > 0) append("  ·  ${row.chaseCount}ª cobrança")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                row.evidenceDeadline?.let { deadline ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (overdue) {
                            "prazo vencido — até ${row.maxChases} cobranças"
                        } else {
                            "restam ${remainingLabel(deadline - now)} para a foto"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (overdue) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.secondary,
                    )
                }
                // Uma foto já enviada numa missão que pede duas continua pendente. Sem
                // dizer isso, "Faltando" parece um erro do app.
                if (task.hasPhotos) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${task.photos.size} foto(s) — falta concluir",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalButton(
                onClick = onPhoto,
                modifier = Modifier.padding(end = 12.dp),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(if (task.hasPhotos) "Abrir" else "Foto")
            }
        }
    }
}

// ----------------------------------------------------------------------- entregue

@Composable
private fun DoneCard(task: EvidenceTask) {
    val row = task.row

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.missionTitle, style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(row.folderColor, size = 8)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = buildString {
                                append(row.folderName)
                                row.completedAt?.let { append("  ·  ").append(formatClock(it)) }
                                if (row.wasLate) append("  ·  com atraso")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (task.hasPhotos) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(task.photos, key = { it.id }) { photo ->
                        Box(
                            Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(10.dp))
                        ) {
                            AsyncImage(
                                model = File(photo.filePath),
                                contentDescription = row.missionTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllClearCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    "Nenhuma foto pendente",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Nada vai despertar para cobrar você.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- formatos

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

private fun formatClock(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(CLOCK)

private fun formatShortDate(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(SHORT_DATE) }.getOrDefault(isoDate)

private fun remainingLabel(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes >= 1) "${minutes}min" else "${millis / 1000}s"
}
