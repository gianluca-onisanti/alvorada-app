package dev.gianluca.alvoradaapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.gianluca.alvoradaapp.AlvoradaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reconstrói todo o agendamento depois dos eventos que apagam os `PendingIntent`
 * registrados no `AlarmManager`.
 *
 * Os agendamentos vivem em memória do sistema e evaporam em: reboot, atualização
 * do app, mudança de fuso e ajuste manual do relógio. Sem este receiver, reiniciar
 * o celular deixaria todos os despertadores silenciosamente mortos — o modo de
 * falha mais perigoso deste app, porque não dá nenhum sinal até a hora errada.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Reagendando após $action")

        val pending = goAsync()
        val container = (context.applicationContext as AlvoradaApp).container

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                container.alarmScheduler.rescheduleAll()
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao reagendar após $action", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
