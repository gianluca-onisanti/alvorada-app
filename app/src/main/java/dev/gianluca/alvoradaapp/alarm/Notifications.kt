package dev.gianluca.alvoradaapp.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import dev.gianluca.alvoradaapp.R

object Notifications {

    const val CHANNEL_ALARM = "alarm_v1"

    /**
     * O canal é criado com som nulo de propósito: quem toca é o `MediaPlayer` do
     * `AlarmSoundPlayer`, com `USAGE_ALARM`. Deixar o canal tocar também resultaria
     * em dois áudios sobrepostos, e o som do canal não fura o modo silencioso.
     *
     * Importância máxima porque é o que habilita o full-screen intent.
     */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Notificação do serviço em foreground. O `fullScreenIntent` é o caminho sancionado
     * para abrir a tela do alarme por cima do lock screen — com a tela apagada ou
     * bloqueada o sistema lança a Activity direto; com o aparelho em uso ele mostra
     * um heads-up, que é o comportamento desejável para não sequestrar a tela à toa.
     *
     * Os botões de soneca e desligar não são conveniência: são a **única saída** quando
     * a tela do alarme é fechada com o som ainda tocando. Sem eles, o único jeito de
     * calar o aparelho era esperar o silenciamento automático — nem desativar nem
     * excluir o despertador ajudava, porque o serviço já tinha o som na mão e não
     * depende mais do banco.
     */
    fun buildAlarmNotification(
        context: Context,
        title: String,
        body: String,
        mode: AlarmMode,
        alarmId: Long,
        occurrenceId: Long,
        canSnooze: Boolean = false,
        snoozeMinutes: Int = 0,
    ): Notification {
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            AlarmContract.RequestCode.show(if (alarmId >= 0) alarmId else 0L),
            Intent(context, AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(AlarmContract.EXTRA_MODE, mode.name)
                putExtra(AlarmContract.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmContract.EXTRA_OCCURRENCE_ID, occurrenceId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = Notification.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (canSnooze) {
            builder.addAction(
                action(
                    context = context,
                    icon = R.drawable.ic_snooze,
                    title = "Soneca $snoozeMinutes min",
                    serviceAction = AlarmContract.ACTION_SNOOZE,
                    requestCode = AlarmContract.RequestCode.notificationSnooze(),
                )
            )
        }

        builder.addAction(
            action(
                context = context,
                icon = R.drawable.ic_alarm_off,
                title = "Desligar",
                serviceAction = AlarmContract.ACTION_DISMISS,
                requestCode = AlarmContract.RequestCode.notificationDismiss(),
            )
        )

        return builder.build()
    }

    /**
     * Vai direto ao serviço em vez de passar por um receiver: quando o botão aparece,
     * o `AlarmService` está necessariamente vivo e em foreground — a notificação é
     * dele —, então `startService` é permitido e o caminho tem uma peça a menos para
     * falhar no momento em que menos se pode falhar.
     */
    private fun action(
        context: Context,
        icon: Int,
        title: String,
        serviceAction: String,
        requestCode: Int,
    ): Notification.Action {
        val pending = PendingIntent.getService(
            context,
            requestCode,
            Intent(context, AlarmService::class.java).setAction(serviceAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(
            Icon.createWithResource(context, icon),
            title,
            pending,
        ).build()
    }
}
