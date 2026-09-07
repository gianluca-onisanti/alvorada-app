package dev.gianluca.alvoradaapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Recebe o disparo do `AlarmManager` e passa a bola adiante, sem fazer nada pesado.
 *
 * Um `BroadcastReceiver` tem poucos segundos de vida e pode ser morto a qualquer
 * momento depois do `onReceive` — nenhum acesso a banco ou reprodução de áudio pode
 * acontecer aqui. Todo o trabalho fica no serviço em foreground, que o sistema
 * mantém vivo.
 *
 * Subir um serviço em foreground a partir do background normalmente é proibido a
 * partir do Android 12, mas receber um alarme exato coloca o app numa allowlist
 * temporária que autoriza exatamente isto.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val mode = intent.getStringExtra(AlarmContract.EXTRA_MODE) ?: AlarmMode.WAKE.name
        val alarmId = intent.getLongExtra(AlarmContract.EXTRA_ALARM_ID, AlarmContract.NO_ID)
        val occurrenceId = intent.getLongExtra(AlarmContract.EXTRA_OCCURRENCE_ID, AlarmContract.NO_ID)

        Log.i(TAG, "Disparo recebido: mode=$mode alarmId=$alarmId occurrenceId=$occurrenceId")

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmContract.ACTION_FIRE
            putExtra(AlarmContract.EXTRA_MODE, mode)
            putExtra(AlarmContract.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmContract.EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private companion object {
        const val TAG = "AlarmReceiver"
    }
}
