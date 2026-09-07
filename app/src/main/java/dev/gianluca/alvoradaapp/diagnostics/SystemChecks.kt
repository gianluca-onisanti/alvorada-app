package dev.gianluca.alvoradaapp.diagnostics

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dev.gianluca.alvoradaapp.alarm.Notifications

data class DiagnosticCheck(
    val id: String,
    val title: String,
    val explanation: String,
    val ok: Boolean,
    val fixLabel: String? = null,
    val fixIntent: Intent? = null,
)

/**
 * Verificação ativa das permissões de que o despertador depende.
 *
 * Todas falham em silêncio: nada avisa que o alarme não vai tocar, você só descobre
 * na manhã em que não tocar. Esta tela existe para transformar cada uma numa
 * pergunta respondível antes de você confiar no app.
 */
class SystemChecks(private val context: Context) {

    fun runAll(): List<DiagnosticCheck> = listOf(
        exactAlarm(),
        notificationsEnabled(),
        alarmChannelEnabled(),
        fullScreenIntent(),
        batteryOptimization(),
    )

    private fun exactAlarm(): DiagnosticCheck {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else true

        return DiagnosticCheck(
            id = "exact_alarm",
            title = "Alarme exato",
            explanation = "Sem isto o sistema pode atrasar o disparo em minutos para " +
                "economizar bateria. É o que garante que 7h00 signifique 7h00.",
            ok = ok,
            fixLabel = "Conceder".takeIf { !ok },
            fixIntent = if (!ok && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, appUri())
            } else null,
        )
    }

    private fun notificationsEnabled(): DiagnosticCheck {
        val ok = NotificationManagerCompat.from(context).areNotificationsEnabled()
        return DiagnosticCheck(
            id = "notifications",
            title = "Notificações",
            explanation = "O alarme toca dentro de um serviço em foreground, que exige " +
                "notificação. Bloqueadas, o alarme não consegue nem começar.",
            ok = ok,
            fixLabel = "Abrir configurações".takeIf { !ok },
            fixIntent = if (!ok) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else null,
        )
    }

    private fun alarmChannelEnabled(): DiagnosticCheck {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(Notifications.CHANNEL_ALARM)
        val ok = channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE

        return DiagnosticCheck(
            id = "alarm_channel",
            title = "Canal “Despertadores”",
            explanation = "Se este canal específico for silenciado ou rebaixado, a tela " +
                "do alarme deixa de abrir sozinha, mesmo com as notificações liberadas.",
            ok = ok,
            fixLabel = "Abrir canal".takeIf { !ok },
            fixIntent = if (!ok) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, Notifications.CHANNEL_ALARM)
            } else null,
        )
    }

    private fun fullScreenIntent(): DiagnosticCheck {
        val ok = if (Build.VERSION.SDK_INT >= 34) {
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else true

        return DiagnosticCheck(
            id = "full_screen_intent",
            title = "Tela cheia sobre o bloqueio",
            explanation = "É o que faz a tela do alarme aparecer por cima do lock screen " +
                "em vez de virar um aviso discreto no topo.",
            ok = ok,
            fixLabel = "Conceder".takeIf { !ok },
            fixIntent = if (!ok && Build.VERSION.SDK_INT >= 34) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, appUri())
            } else null,
        )
    }

    private fun batteryOptimization(): DiagnosticCheck {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ok = pm.isIgnoringBatteryOptimizations(context.packageName)

        return DiagnosticCheck(
            id = "battery",
            title = "Otimização de bateria",
            explanation = "Com a otimização ativa o sistema pode matar o app entre um " +
                "alarme e outro. É a causa número um de despertador que não toca.",
            ok = ok,
            fixLabel = "Desativar".takeIf { !ok },
            fixIntent = if (!ok) {
                @Suppress("BatteryLife")
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appUri())
            } else null,
        )
    }

    private fun appUri(): Uri = Uri.parse("package:${context.packageName}")

    /**
     * Alguns fabricantes mantêm um matador de processos próprio, acima das
     * configurações padrão do Android — liberar tudo acima ainda não basta neles.
     */
    fun oemWarning(): String? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            "xiaomi" in manufacturer || "redmi" in manufacturer || "poco" in manufacturer ->
                "Xiaomi: ative “Autostart” para o Alvorada e trave o app na tela de recentes " +
                    "(ícone de cadeado). Sem isso o MIUI mata o app dormindo."

            "samsung" in manufacturer ->
                "Samsung: em Bateria → Limites de uso em segundo plano, garanta que o Alvorada " +
                    "não esteja em “Apps em suspensão” nem em “Apps em suspensão profunda”."

            "huawei" in manufacturer || "honor" in manufacturer ->
                "Huawei/Honor: em Bateria → Inicialização de app, mude o Alvorada para " +
                    "gerenciamento manual com as três opções ligadas."

            "oppo" in manufacturer || "realme" in manufacturer || "oneplus" in manufacturer ->
                "Oppo/Realme/OnePlus: permita execução em segundo plano e desative a " +
                    "otimização agressiva de bateria para o Alvorada."

            "motorola" in manufacturer || "lenovo" in manufacturer ->
                "Motorola: desative o Moto “otimizador de bateria” para o Alvorada."

            "vivo" in manufacturer ->
                "Vivo: ative “Alta atividade em segundo plano” para o Alvorada."

            else -> null
        }
    }
}
