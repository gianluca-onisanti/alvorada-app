package dev.gianluca.alvoradaapp.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
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
import dev.gianluca.alvoradaapp.ui.audio.AudioLibraryScreen
import dev.gianluca.alvoradaapp.ui.audio.AudioTrimScreen
import dev.gianluca.alvoradaapp.ui.checklists.ChecklistEditorScreen
import dev.gianluca.alvoradaapp.ui.checklists.ChecklistScreen
import dev.gianluca.alvoradaapp.ui.checklists.NEW_CHECKLIST_ID
import dev.gianluca.alvoradaapp.ui.components.AlvoradaDrawer
import dev.gianluca.alvoradaapp.ui.components.NavDestination
import dev.gianluca.alvoradaapp.ui.evidence.EvidenceCameraScreen
import dev.gianluca.alvoradaapp.ui.evidence.EvidenceScreen
import dev.gianluca.alvoradaapp.ui.gallery.GalleryScreen
import dev.gianluca.alvoradaapp.ui.panel.PanelScreen
import dev.gianluca.alvoradaapp.ui.rewards.RewardsScreen
import dev.gianluca.alvoradaapp.ui.theme.GLASS_ALPHA
import kotlinx.coroutines.launch

object Routes {
    const val PANEL = "panel"
    const val ALARMS = "alarms"
    const val CHECKLISTS = "checklists"
    const val EVIDENCE_BOARD = "evidences"
    const val GALLERY = "gallery"
    const val REWARDS = "rewards"
    const val AUDIO = "audio"
    const val SETTINGS = "settings"
    const val ALARM_EDITOR = "alarm/{id}?once={once}"
    const val EVIDENCE = "evidence/{instanceId}"
    const val CHECKLIST_EDITOR = "checklist/{id}"
    const val AUDIO_TRIM = "audio/trim?uri={uri}"

    /**
     * [once] só diz respeito a um despertador **novo**: para um já existente, quem
     * responde se ele é de uma vez só é a linha no banco, não a rota.
     */
    fun alarmEditor(id: Long, once: Boolean = false) = "alarm/$id?once=$once"
    fun evidence(instanceId: Long) = "evidence/$instanceId"
    fun checklistEditor(id: Long) = "checklist/$id"

    /**
     * A URI do MediaStore vira parâmetro de consulta, e não segmento de caminho: ela
     * contém barras, e num segmento elas partiriam a rota em pedaços.
     */
    fun audioTrim(uri: String) = "audio/trim?uri=" + Uri.encode(uri)
}

/**
 * Os destinos de primeiro nível, na ordem do menu lateral.
 *
 * A V1 tinha cinco abas numa barra e escondia Ajustes numa chave no cabeçalho do
 * Painel. Cinco era o teto do que cabe numa barra sem virar sopa de ícone, e a V2
 * acrescenta destinos — então o menu lateral passou a ser a lista completa, e
 * Ajustes virou um item nomeado como os outros, em vez de um botão que só encontra
 * quem já sabe que ele existe.
 */
private val DESTINATIONS = listOf(
    NavDestination(Routes.PANEL, "Painel", Icons.Filled.Dashboard),
    NavDestination(Routes.ALARMS, "Relógio", Icons.Filled.Alarm),
    NavDestination(Routes.CHECKLISTS, "Checklists", Icons.Filled.Checklist),
    NavDestination(Routes.EVIDENCE_BOARD, "Evidências", Icons.Filled.PhotoCamera),
    NavDestination(Routes.GALLERY, "Galeria", Icons.Filled.PhotoLibrary),
    NavDestination(Routes.REWARDS, "Prêmios", Icons.Filled.Redeem),
    NavDestination(Routes.AUDIO, "Áudios", Icons.Filled.LibraryMusic),
    NavDestination(Routes.SETTINGS, "Ajustes", Icons.Filled.Settings),
)

/**
 * Quantos dos primeiros [DESTINATIONS] também ficam na barra de baixo.
 *
 * A barra sobreviveu ao menu por causa das 6h da manhã: o que se usa todo dia tem
 * que estar a um toque, e não a dois. Painel, Relógio e Evidências são o ciclo
 * diário — o despertador cria a dívida, os checklists e a tela de evidências a
 * liquidam, o painel mostra o placar. Galeria, Prêmios e Ajustes se abrem em dias
 * raros e ficam só no menu.
 */
private const val BOTTOM_BAR_COUNT = 4

/** Rotas com chrome: barra superior com menu e barra de baixo. */
private val CHROME_ROUTES: Set<String> = DESTINATIONS.map { it.route }.toSet()

@Composable
fun AlvoradaNavigation(diagnosticsRefreshKey: Int) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val hasChrome = currentRoute in CHROME_ROUTES

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    val goTo: (String) -> Unit = { route ->
        scope.launch { drawerState.close() }
        if (currentRoute != route) navController.navigateTopLevel(route)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // O mesmo portão da barra de baixo. Sem ele, um arrasto de borda abriria o
        // menu no meio de uma edição de despertador ou por cima do visor da câmera.
        gesturesEnabled = hasChrome,
        drawerContent = {
            AlvoradaDrawer(
                destinations = DESTINATIONS,
                currentRoute = currentRoute,
                separatorAfter = BOTTOM_BAR_COUNT - 1,
                onNavigate = goTo,
            )
        },
    ) {
        Scaffold(
            // Transparente para o gradiente de `AlvoradaBackground` aparecer: é ele
            // que dá às superfícies translúcidas algo para deixar passar.
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            bottomBar = {
                // O editor ocupa a tela inteira: navegar de aba no meio de uma edição
                // não salva seria uma forma silenciosa de perder trabalho.
                if (hasChrome) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                            .copy(alpha = GLASS_ALPHA),
                    ) {
                        DESTINATIONS.take(BOTTOM_BAR_COUNT).forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = { goTo(destination.route) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(destination.label) },
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
                        onOpenSettings = { navController.navigateTopLevel(Routes.SETTINGS) },
                        onOpenDrawer = openDrawer,
                        setupRefreshKey = diagnosticsRefreshKey,
                    )
                }

                composable(Routes.CHECKLISTS) {
                    ChecklistScreen(
                        onEditChecklist = { id ->
                            navController.navigate(Routes.checklistEditor(id))
                        },
                        onCreateChecklist = {
                            navController.navigate(Routes.checklistEditor(NEW_CHECKLIST_ID))
                        },
                        onOpenDrawer = openDrawer,
                    )
                }

                composable(Routes.EVIDENCE_BOARD) {
                    EvidenceScreen(
                        onOpenCamera = { id -> navController.navigate(Routes.evidence(id)) },
                        onOpenDrawer = openDrawer,
                    )
                }

                composable(Routes.GALLERY) { GalleryScreen(onOpenDrawer = openDrawer) }

                composable(Routes.REWARDS) { RewardsScreen(onOpenDrawer = openDrawer) }

                composable(Routes.ALARMS) {
                    AlarmListScreen(
                        onEditAlarm = { id -> navController.navigate(Routes.alarmEditor(id)) },
                        onCreateAlarm = { once ->
                            navController.navigate(Routes.alarmEditor(NEW_ALARM_ID, once))
                        },
                        onOpenDrawer = openDrawer,
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

                composable(
                    route = Routes.CHECKLIST_EDITOR,
                    // Texto pelo mesmo motivo do editor de despertador: o id de um
                    // checklist novo é -1, e `NavType.LongType` não aceita negativo
                    // num segmento de caminho.
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    val id = entry.arguments?.getString("id")?.toLongOrNull()
                        ?: NEW_CHECKLIST_ID
                    ChecklistEditorScreen(
                        checklistId = id,
                        onDone = { navController.popBackStack() },
                    )
                }

                composable(Routes.AUDIO) {
                    AudioLibraryScreen(
                        onTrim = { uri -> navController.navigate(Routes.audioTrim(uri)) },
                        onOpenDrawer = openDrawer,
                    )
                }

                composable(
                    route = Routes.AUDIO_TRIM,
                    arguments = listOf(navArgument("uri") { type = NavType.StringType }),
                ) { entry ->
                    val uri = entry.arguments?.getString("uri") ?: return@composable
                    AudioTrimScreen(
                        sourceUri = uri,
                        onDone = { navController.popBackStack() },
                    )
                }

                composable(Routes.SETTINGS) {
                    DiagnosticsScreen(
                        refreshKey = diagnosticsRefreshKey,
                        onOpenDrawer = openDrawer,
                    )
                }
            }
        }
    }
}

/**
 * Navega para um destino de primeiro nível preservando o que a tela tinha na tela.
 *
 * `saveState`/`restoreState` são novos na V2 e existem porque o menu deixou a troca
 * de destino mais frequente: sem eles cada volta ao Painel remontava a tela do zero
 * e perdia a posição da rolagem, que numa galeria de meses de foto é perda real.
 */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.PANEL) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
