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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.data.ChecklistRun
import dev.gianluca.alvoradaapp.data.describeRepeat
import dev.gianluca.alvoradaapp.ui.components.AlvoradaTopBar
import dev.gianluca.alvoradaapp.ui.components.CategoryStripe
import dev.gianluca.alvoradaapp.ui.components.EmptyState
import dev.gianluca.alvoradaapp.ui.components.GlassCard
import dev.gianluca.alvoradaapp.ui.components.SectionTitle
import dev.gianluca.alvoradaapp.ui.components.formatFireClock
import dev.gianluca.alvoradaapp.ui.components.formatTimeUntil
import kotlinx.coroutines.launch

/**
 * As listas recorrentes e o que falta em cada uma nesta rodada.
 *
 * A tela é sobre o **agora**: só a rodada corrente de cada checklist aparece, com o
 * vencimento em destaque. Histórico de rodadas passadas não tem lugar aqui — quem
 * abre esta tela quer saber o que falta hoje, e uma lista de rodadas cumpridas
 * empurraria isso para baixo.
 */
@Composable
fun ChecklistScreen(
    onEditChecklist: (Long) -> Unit,
    onCreateChecklist: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    val repository = container.checklistRepository
    val scope = rememberCoroutineScope()

    // Abrir a tela abre as rodadas que ainda não existem. A varredura das 03h faz o
    // mesmo, mas o celular pode ter passado a madrugada desligado.
    LaunchedEffect(Unit) { repository.ensureAllCycles() }

    val runs by repository.observeRuns().collectAsState(initial = emptyList())

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = { AlvoradaTopBar("Checklists", onOpenDrawer) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateChecklist) {
                Icon(Icons.Filled.Add, contentDescription = "Novo checklist")
            }
        },
    ) { padding ->
        if (runs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Checklist,
                title = "Nenhum checklist ainda",
                body = "Listas que se repetem — a limpeza de sábado, a manutenção do mês. " +
                    "Marque o item e o despertador dele se cala até a próxima rodada.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        val byFolder = runs.groupBy { it.folder?.name ?: "Sem categoria" }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            byFolder.forEach { (folderName, group) ->
                item(key = "header-$folderName") {
                    SectionTitle(folderName, trailing = "${group.size}")
                }
                items(group, key = { it.checklist.id }) { run ->
                    ChecklistCard(
                        run = run,
                        onToggle = { itemId, done ->
                            scope.launch { repository.toggleItem(run.cycle.id, itemId, done) }
                        },
                        onEdit = { onEditChecklist(run.checklist.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecklistCard(
    run: ChecklistRun,
    onToggle: (Long, Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val overdue = run.isOverdue()
    val container = if (run.isComplete) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), container = container) {
        // `heightIn` e não `fillMaxHeight`: é o conteúdo que define a altura, e a
        // faixa da categoria estica para acompanhar. Mesmo padrão de EvidenceScreen.
        Row(Modifier.heightIn(min = 76.dp)) {
            run.folder?.let { CategoryStripe(it.colorHex) }
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                        Text(run.checklist.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            run.checklist.describeRepeat(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${run.done}/${run.total}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (run.total == 0) 0f else run.done.toFloat() / run.total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )

                Spacer(Modifier.height(8.dp))
                // O vencimento em destaque é o ponto da tela: um checklist sem prazo
                // visível vira uma lista de desejos.
                Text(
                    text = when {
                        run.isComplete -> "Cumprido · volta ${formatTimeUntil(run.cycle.endsAt)}"
                        overdue -> "Venceu ${formatFireClock(run.cycle.dueAt)}"
                        else -> "Vence ${formatFireClock(run.cycle.dueAt)}  ·  " +
                            formatTimeUntil(run.cycle.dueAt)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        run.isComplete -> MaterialTheme.colorScheme.secondary
                        // Vencido é cinza, nunca vermelho: informação, não repreensão.
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                Spacer(Modifier.height(6.dp))
                run.items.forEach { itemRun ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(itemRun.item.id, !itemRun.done) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = itemRun.done,
                            onCheckedChange = { onToggle(itemRun.item.id, it) },
                        )
                        Text(
                            text = itemRun.item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (itemRun.done) TextDecoration.LineThrough else null,
                            color = if (itemRun.done) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (itemRun.hasAlarm) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.NotificationsActive,
                                contentDescription = "Tem despertador",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
