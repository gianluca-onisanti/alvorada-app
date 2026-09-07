package dev.gianluca.alvoradaapp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.gianluca.alvoradaapp.diagnostics.DiagnosticsScreen
import dev.gianluca.alvoradaapp.ui.alarms.AlarmEditorScreen
import dev.gianluca.alvoradaapp.ui.alarms.AlarmListScreen
import dev.gianluca.alvoradaapp.ui.alarms.NEW_ALARM_ID
import dev.gianluca.alvoradaapp.ui.evidence.EvidenceCameraScreen
import dev.gianluca.alvoradaapp.ui.evidence.EvidenceScreen
import dev.gianluca.alvoradaapp.ui.gallery.GalleryScreen
import dev.gianluca.alvoradaapp.ui.panel.PanelScreen
import dev.gianluca.alvoradaapp.ui.rewards.RewardsScreen

object Routes {
    const val PANEL = "panel"
    const val ALARMS = "alarms"
    const val EVIDENCE_BOARD = "evidences"
    const val GALLERY = "gallery"
    const val REWARDS = "rewards"
    const val DIAGNOSTICS = "diagnostics"
    const val ALARM_EDITOR = "alarm/{id}?once={once}"
    const val EVIDENCE = "evidence/{instanceId}"

    /**
     * [once] só diz respeito a um despertador **novo**: para um já existente, quem
     * responde se ele é de uma vez só é a linha no banco, não a rota.
     */
    fun alarmEditor(id: Long, once: Boolean = false) = "alarm/$id?once=$once"
    fun evidence(instanceId: Long) = "evidence/$instanceId"
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

/**
 * Cinco abas, todas de uso diário.
 *
 * Ajustes saiu daqui e virou um botão no cabeçalho do Painel: é uma tela de
 * configuração e diagnóstico, aberta em dias raros, e uma aba permanente sugeria que
 * fosse parte da rotina. Evidências entra ao lado do Relógio porque é a continuação
 * direta dele — o despertador cria a dívida, esta tela a liquida.
 */
private val TABS = listOf(
    Tab(Routes.PANEL, "Painel") { Icon(Icons.Filled.Dashboard, contentDescription = null) },
    Tab(Routes.ALARMS, "Relógio") { Icon(Icons.Filled.Alarm, contentDescription = null) },
    Tab(Routes.EVIDENCE_BOARD, "Evidências") {
        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
    },
    Tab(Routes.GALLERY, "Galeria") { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
    Tab(Routes.REWARDS, "Prêmios") { Icon(Icons.Filled.Redeem, contentDescription = null) },
)

@Composable
fun AlvoradaNavigation(diagnosticsRefreshKey: Int) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            // O editor ocupa a tela inteira: navegar de aba no meio de uma edição
            // não salva seria uma forma silenciosa de perder trabalho.
            if (currentRoute in TABS.map { it.route }) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.PANEL) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = tab.icon,
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.PANEL,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(Routes.PANEL) {
                PanelScreen(
                    onOpenCamera = { id -> navController.navigate(Routes.evidence(id)) },
                    onOpenSettings = { navController.navigate(Routes.DIAGNOSTICS) },
                    setupRefreshKey = diagnosticsRefreshKey,
                )
            }

            composable(Routes.EVIDENCE_BOARD) {
                EvidenceScreen(
                    onOpenCamera = { id -> navController.navigate(Routes.evidence(id)) },
                )
            }

            composable(Routes.GALLERY) { GalleryScreen() }

            composable(Routes.REWARDS) { RewardsScreen() }

            composable(Routes.ALARMS) {
                AlarmListScreen(
                    onEditAlarm = { id -> navController.navigate(Routes.alarmEditor(id)) },
                    onCreateAlarm = { once ->
                        navController.navigate(Routes.alarmEditor(NEW_ALARM_ID, once))
                    },
                )
            }

            composable(
                route = Routes.ALARM_EDITOR,
                arguments = listOf(
                    // Tipo texto de propósito: o id de um despertador novo é -1, e o
                    // NavType.LongType não aceita negativo num segmento de caminho.
                    navArgument("id") { type = NavType.StringType },
                    navArgument("once") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: NEW_ALARM_ID
                AlarmEditorScreen(
                    alarmId = id,
                    startAsOneShot = entry.arguments?.getBoolean("once") == true,
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.EVIDENCE,
                arguments = listOf(navArgument("instanceId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("instanceId") ?: return@composable
                EvidenceCameraScreen(
                    instanceId = id,
                    onFinished = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }

            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(
                    refreshKey = diagnosticsRefreshKey,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
