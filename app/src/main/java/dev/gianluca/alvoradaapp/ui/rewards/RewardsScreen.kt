package dev.gianluca.alvoradaapp.ui.rewards

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gianluca.alvoradaapp.AlvoradaApp
import dev.gianluca.alvoradaapp.core.Leveling
import dev.gianluca.alvoradaapp.core.StreakSnapshot
import dev.gianluca.alvoradaapp.data.ProgressSummary
import dev.gianluca.alvoradaapp.data.RedeemResult
import dev.gianluca.alvoradaapp.data.RewardEntity
import kotlinx.coroutines.launch

/**
 * A loja. As recompensas são cadastradas por você — o app não tem opinião sobre o
 * que te motiva, só sobre o preço estar visível antes de você gastar.
 *
 * Moedas são a única coisa gastável do sistema. O XP e o nível ficam intocados por
 * compras: gastar não desfaz o que você fez para ganhar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AlvoradaApp).container }
    val points = container.pointsRepository
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val progress by points.observeProgress().collectAsState(
        initial = ProgressSummary(0, Leveling.progressOf(0), 0, StreakSnapshot(0, 0, 2))
    )
    val rewards by points.observeRewards().collectAsState(initial = emptyList())

    var editing by remember { mutableStateOf<RewardEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Recompensas") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nova recompensa")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { BalanceCard(progress) }

            if (rewards.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Nenhuma recompensa", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Cadastre coisas que você realmente quer — pedir sushi, " +
                                    "uma tarde de jogo, um livro. Moeda sem nada para " +
                                    "comprar deixa de significar alguma coisa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items(rewards, key = { it.id }) { reward ->
                RewardRow(
                    reward = reward,
                    balance = progress.coins,
                    onEdit = { editing = reward },
                    onDelete = { scope.launch { points.deleteReward(reward) } },
                    onRedeem = {
                        scope.launch {
                            when (val result = points.redeem(reward.id)) {
                                is RedeemResult.Ok ->
                                    snackbar.showSnackbar(
                                        "${reward.name} resgatado — restam ${result.remaining} moedas"
                                    )

                                is RedeemResult.NotEnough ->
                                    snackbar.showSnackbar("Faltam ${result.missing} moedas")

                                RedeemResult.Unknown ->
                                    snackbar.showSnackbar("Recompensa não encontrada")
                            }
                        }
                    },
                )
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (creating || editing != null) {
        RewardDialog(
            reward = editing,
            onDismiss = { creating = false; editing = null },
            onConfirm = { reward ->
                scope.launch { points.saveReward(reward) }
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun BalanceCard(progress: ProgressSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${progress.coins}",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    " moedas",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Nível ${progress.level.level}  ·  ${progress.level.xpIntoLevel}/" +
                    "${progress.level.xpNeededForNext} XP",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.level.ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "O nível vem do XP acumulado e nunca cai — nem por compra, nem por " +
                    "dia perdido.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RewardRow(
    reward: RewardEntity,
    balance: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRedeem: () -> Unit,
) {
    val affordable = balance >= reward.costCoins

    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(reward.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (affordable) "${reward.costCoins} moedas"
                    else "${reward.costCoins} moedas · faltam ${reward.costCoins - balance}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (affordable) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Excluir recompensa")
            }
            TextButton(onClick = onEdit) { Text("Editar") }
            Button(onClick = onRedeem, enabled = affordable) { Text("Resgatar") }
        }
    }
}

@Composable
private fun RewardDialog(
    reward: RewardEntity?,
    onDismiss: () -> Unit,
    onConfirm: (RewardEntity) -> Unit,
) {
    var name by rememberSaveable(reward?.id) { mutableStateOf(reward?.name.orEmpty()) }
    var cost by rememberSaveable(reward?.id) {
        mutableStateOf(reward?.costCoins?.toString() ?: "100")
    }

    val costValue = cost.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (reward == null) "Nova recompensa" else "Editar recompensa") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("O que você ganha") },
                    placeholder = { Text("Pedir sushi, tarde de jogo…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it.filter(Char::isDigit).take(6) },
                    label = { Text("Custo em moedas") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        (reward ?: RewardEntity(name = "", costCoins = 0)).copy(
                            name = name.trim(),
                            costCoins = costValue ?: 0,
                        )
                    )
                },
                enabled = name.isNotBlank() && (costValue ?: 0) > 0,
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
