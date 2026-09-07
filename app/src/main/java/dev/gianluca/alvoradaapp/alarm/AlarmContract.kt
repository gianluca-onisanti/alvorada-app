package dev.gianluca.alvoradaapp.alarm

/** Por que o aparelho está tocando agora. */
enum class AlarmMode {
    /** Disparo normal do despertador, no horário agendado. */
    WAKE,

    /** Retorno depois de uma soneca. */
    SNOOZE,

    /** A janela de evidência estourou e existe missão sem foto. */
    CHASE,

    /** Disparo manual da tela de Diagnóstico. Não toca em nenhuma missão. */
    TEST,
}

object AlarmContract {

    const val ACTION_FIRE = "dev.gianluca.alvoradaapp.action.FIRE"
    const val ACTION_STOP = "dev.gianluca.alvoradaapp.action.STOP"
    const val ACTION_SNOOZE = "dev.gianluca.alvoradaapp.action.SNOOZE"
    const val ACTION_DISMISS = "dev.gianluca.alvoradaapp.action.DISMISS"

    const val EXTRA_ALARM_ID = "alarmId"
    const val EXTRA_OCCURRENCE_ID = "occurrenceId"
    const val EXTRA_MODE = "mode"

    /** Missões já marcadas na própria tela do alarme, no momento de dispensar. */
    const val EXTRA_COMPLETED_MISSIONS = "completedMissions"

    const val NO_ID = -1L

    /**
     * Request codes determinísticos para os `PendingIntent`.
     *
     * Precisam ser estáveis entre execuções: é assim que se cancela um agendamento
     * feito antes de um reboot ou de uma atualização do app. `id * 10 + tipo` mantém
     * espaços separados por tipo — um alarme e uma cobrança nunca se sobrescrevem,
     * mesmo quando os ids coincidem em tabelas diferentes.
     */
    object RequestCode {
        private const val TYPE_WAKE = 1
        private const val TYPE_SNOOZE = 2
        private const val TYPE_CHASE = 3
        private const val TYPE_SHOW = 4
        private const val TYPE_TEST = 5

        fun wake(alarmId: Long): Int = (alarmId * 10 + TYPE_WAKE).toInt()
        fun snooze(occurrenceId: Long): Int = (occurrenceId * 10 + TYPE_SNOOZE).toInt()
        fun chase(occurrenceId: Long): Int = (occurrenceId * 10 + TYPE_CHASE).toInt()
        fun show(alarmId: Long): Int = (alarmId * 10 + TYPE_SHOW).toInt()
        fun test(): Int = TYPE_TEST

        /**
         * Botões da notificação. Fora do espaço `id * 10 + tipo` de propósito: um
         * alarme de id 900 produziria 9001, e um código de ação fixo escolhido "num
         * número alto qualquer" colidiria com ele em silêncio — a soneca do alarme
         * passaria a disparar a ação da notificação.
         */
        fun notificationSnooze(): Int = Int.MAX_VALUE - 1
        fun notificationDismiss(): Int = Int.MAX_VALUE - 2
    }

    /** Id de notificação do serviço em foreground. Único: só toca um alarme por vez. */
    const val FOREGROUND_NOTIFICATION_ID = 1001
}
