package dev.gianluca.alvoradaapp.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.data.GalleryGroup
import dev.gianluca.alvoradaapp.data.GalleryGrouping
import dev.gianluca.alvoradaapp.data.GalleryPhoto
import dev.gianluca.alvoradaapp.data.PanelRepository
import dev.gianluca.alvoradaapp.ui.components.ColorDot
import dev.gianluca.alvoradaapp.ui.components.EmptyState
import dev.gianluca.alvoradaapp.ui.components.parseColor
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * As fotos tiradas dentro do app.
 *
 * Cada foto carrega de volta o contexto que a produziu — missão, categoria e horário.
 * Uma foto de caixa de remédio sem essa moldura seria indistinguível de lixo visual
 * três semanas depois; com ela, a galeria vira um registro de que a rotina aconteceu.
 *
 * O agrupamento por dia responde "o que eu fiz naquele dia?", mas há outras duas
 * perguntas que ele não responde: "como está o histórico desta categoria?" e "esse
 * despertador vem sendo cumprido?". Daí o seletor — e daí ele voltar sempre para o
 * dia ao reabrir a tela, que é a pergunta mais frequente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }

    var grouping by remember { mutableStateOf(GalleryGrouping.DAY) }
    val photos by remember { container.panelRepository.observeGalleryPhotos() }
        .collectAsState(initial = emptyList())
    // Reagrupar não recomeça a coleta: a troca é instantânea e a tela não pisca vazia.
    val groups = remember(photos, grouping) { PanelRepository.group(photos, grouping) }

    var viewing by remember { mutableStateOf<GalleryPhoto?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Galeria") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            GroupingPicker(selected = grouping, onSelect = { grouping = it })

            if (groups.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.PhotoLibrary,
                    title = "Nenhuma foto ainda",
                    body = "As evidências que você enviar aparecem aqui, agrupadas por dia.",
                )
                return@Column
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                groups.forEach { group ->
                    item(key = "header-${group.key}", span = { GridItemSpan(maxLineSpan) }) {
                        GroupHeader(group = group, grouping = grouping)
                    }
                    items(group.photos, key = { it.id }) { photo ->
                        Thumbnail(photo = photo, onClick = { viewing = photo })
                    }
                }
            }
        }
    }

    viewing?.let { photo ->
        PhotoViewer(photo = photo, onDismiss = { viewing = null })
    }
}

@Composable
private fun GroupingPicker(
    selected: GalleryGrouping,
    onSelect: (GalleryGrouping) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Agrupar por",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        GalleryGrouping.entries.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(option.label) },
            )
        }
    }
}

@Composable
private fun GroupHeader(group: GalleryGroup, grouping: GalleryGrouping) {
    Row(
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        group.colorHex?.let {
            ColorDot(it)
            Spacer(Modifier.size(8.dp))
        }
        Column {
            Text(
                text = if (grouping == GalleryGrouping.DAY) {
                    formatDayLabel(group.label).replaceFirstChar { it.uppercase() }
                } else {
                    group.label
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${group.photos.size} foto(s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Thumbnail(photo: GalleryPhoto, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = File(photo.filePath),
            contentDescription = photo.missionTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Faixa inferior: a cor da categoria identifica a origem sem ocupar espaço.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(parseColor(photo.folderColor))
        )
    }
}

@Composable
private fun PhotoViewer(photo: GalleryPhoto, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            verticalArrangement = Arrangement.Center,
        ) {
            AsyncImage(
                model = File(photo.filePath),
                contentDescription = photo.missionTitle,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(parseColor(photo.folderColor))
                )
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        photo.missionTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        buildString {
                            append(photo.folderName)
                            append("  ·  ")
                            append(
                                "%s - %02d:%02d".format(
                                    photo.alarmLabel.ifBlank { "Sem nome" },
                                    photo.alarmHour,
                                    photo.alarmMinute,
                                )
                            )
                            append("  ·  ")
                            append(formatClockOf(photo.takenAt))
                        },
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val DAY_LABEL = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR)
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

private fun formatDayLabel(isoDate: String): String = runCatching {
    val date = LocalDate.parse(isoDate)
    when (date) {
        LocalDate.now() -> "Hoje"
        LocalDate.now().minusDays(1) -> "Ontem"
        else -> date.format(DAY_LABEL)
    }
}.getOrDefault(isoDate)

private fun formatClockOf(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(CLOCK)
