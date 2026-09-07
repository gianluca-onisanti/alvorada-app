package dev.gianluca.alvoradaapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.gianluca.alvoradaapp.alarm.AlarmActivity
import dev.gianluca.alvoradaapp.alarm.AlarmService
import dev.gianluca.alvoradaapp.data.ThemeChoice
import dev.gianluca.alvoradaapp.ui.AlvoradaNavigation
import dev.gianluca.alvoradaapp.ui.components.AlvoradaBackground
import dev.gianluca.alvoradaapp.ui.theme.AlvoradaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * Incrementado a cada `onResume` para reexecutar os checks do Diagnóstico.
     * O usuário sai daqui para uma tela de configuração do sistema e volta — sem isto,
     * a permissão recém-concedida continuaria aparecendo como pendente.
     */
    private var refreshKey by mutableIntStateOf(0)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshKey++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Com `targetSdk 36` o Android 15+ desenha atrás das barras do sistema de
        // qualquer forma. Declarar aqui é o que traz o `SystemBarStyle.auto` de
        // brinde: sem ele ninguém controla o contraste dos ícones, e no tema claro
        // sobre barra transparente eles ficam branco no branco.
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        followRingingAlarm()

        setContent {
            val preferences = remember { (application as AlvoradaApp).container.preferences }
            val theme by preferences.themeChoice.collectAsState(initial = ThemeChoice.SYSTEM)

            AlvoradaTheme(
                darkTheme = when (theme) {
                    ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                    ThemeChoice.LIGHT -> false
                    ThemeChoice.DARK -> true
                }
            ) {
                AlvoradaBackground {
                    AlvoradaNavigation(diagnosticsRefreshKey = refreshKey)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
    }

    /**
     * Enquanto esta tela estiver visível, um alarme tocando traz a tela do alarme por
     * cima — abrindo o app no meio do toque ou ao alarme disparar com o app já aberto.
     *
     * O app não pode ter um estado em que o aparelho toca e não existe onde desligar.
     * O `fullScreenIntent` cobre o aparelho bloqueado, mas com o app em uso ele vira
     * só um heads-up, que se dispensa com um gesto e some — e aí sobrava um alarme
     * tocando sem nenhuma tela para pará-lo.
     *
     * `repeatOnLifecycle(STARTED)` importa: sem ele, a coleta continuaria em segundo
     * plano e o app tentaria abrir a tela do alarme estando invisível.
     */
    private fun followRingingAlarm() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AlarmService.state.collect { ringing ->
                    if (ringing == null) return@collect
                    startActivity(
                        Intent(this@MainActivity, AlarmActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
