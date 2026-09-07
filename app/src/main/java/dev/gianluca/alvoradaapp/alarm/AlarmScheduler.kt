package dev.gianluca.alvoradaapp.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dev.gianluca.alvoradaapp.MainActivity
import dev.gianluca.alvoradaapp.core.FireOutlook
import dev.gianluca.alvoradaapp.data.AlarmEntity
import dev.gianluca.alvoradaapp.data.AlvoradaDatabase
import dev.gianluca.alvoradaapp.data.isOneShot
import dev.gianluca.alvoradaapp.data.outlook
import java.time.ZonedDateTime

/**
 * Ponto único de agendamento do app. Nada mais fala com o `AlarmManager` direto.
 *
 * Tudo usa `setAlarmClock`, que é o único caminho isento do Doze: o sistema nunca
 * desloca esses disparos para economizar bateria e ainda mostra o ícone de despertador
 * na barra de status, o que dá uma confirmação visual de graça de que o agendamento existe.
 */
class AlarmScheduler(
    private val context: Context,
    private val db: AlvoradaDatabase,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Em API 33+ o `USE_EXACT_ALARM` é concedido na instalação para apps de despertador
     * e isto sempre retorna `true`. Entre 31 e 32 depende de o usuário ter concedido
     * o `SCHEDULE_EXACT_ALARM` nas configurações — a tela de Diagnóstico checa isso.
     */
    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms()
        else true

    // ---------------------------------------------------------------- agendamento

    /** Quando este alarme tocaria, sem agendar nada. Para a lista mostrar a contagem. */
    fun peekNextFire(alarm: AlarmEntity): Long? = peekOutlook(alarm).nextFire

    /** Previsão completa: quando toca e qual toque está sendo pulado. */
    fun peekOutlook(alarm: AlarmEntity): FireOutlook = alarm.outlook(now())

    /**
     * Agenda o próximo disparo recorrente. Retorna o instante agendado, ou `null`.
     *
     * O pulo de "só a próxima ativação" é resolvido aqui, e não no `AlarmManager`:
     * simplesmente não existe agendamento para o toque pulado — o que já está de pé
     * é o toque seguinte. Assim um reboot no meio do pulo reconstrói o mesmo estado,
     * em vez de depender de alguém lembrar de cancelar algo na hora certa.
     */
    fun scheduleNextWake(alarm: AlarmEntity): Long? {
        cancelWake(alarm.id)
        if (!alarm.enabled) return null

        val triggerAt = alarm.outlook(now()).nextFire ?: return null

        setExact(
            triggerAtMillis = triggerAt,
            operation = firePendingIntent(
                requestCode = AlarmContract.RequestCode.wake(alarm.id),
                mode = AlarmMode.WAKE,
                alarmId = alarm.id,
                occurrenceId = AlarmContract.NO_ID,
            ),
            showIntentRequestCode = AlarmContract.RequestCode.show(alarm.id),
        )
        Log.i(TAG, "Alarme ${alarm.id} (${alarm.label}) agendado para $triggerAt")
        return triggerAt
    }

    fun scheduleSnooze(alarmId: Long, occurrenceId: Long, minutes: Int): Long {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        setExact(
            triggerAtMillis = triggerAt,
            operation = firePendingIntent(
                requestCode = AlarmContract.RequestCode.snooze(occurrenceId),
                mode = AlarmMode.SNOOZE,
                alarmId = alarmId,
                occurrenceId = occurrenceId,
            ),
            showIntentRequestCode = AlarmContract.RequestCode.show(alarmId),
        )
        return triggerAt
    }

    /** A cobrança: re-desperta porque a janela de evidência estourou. */
    fun scheduleChase(alarmId: Long, occurrenceId: Long, triggerAtMillis: Long) {
        setExact(
            triggerAtMillis = triggerAtMillis,
            operation = firePendingIntent(
                requestCode = AlarmContract.RequestCode.chase(occurrenceId),
                mode = AlarmMode.CHASE,
                alarmId = alarmId,
                occurrenceId = occurrenceId,
            ),
            showIntentRequestCode = AlarmContract.RequestCode.show(alarmId),
        )
        Log.i(TAG, "Cobrança do disparo $occurrenceId agendada para $triggerAtMillis")
    }

    /** Disparo manual da tela de Diagnóstico — não cria disparo nem toca em missões. */
    fun scheduleTest(delaySeconds: Int): Long {
        val triggerAt = System.currentTimeMillis() + delaySeconds * 1_000L
        setExact(
            triggerAtMillis = triggerAt,
            operation = firePendingIntent(
                requestCode = AlarmContract.RequestCode.test(),
                mode = AlarmMode.TEST,
                alarmId = AlarmContract.NO_ID,
                occurrenceId = AlarmContract.NO_ID,
            ),
            showIntentRequestCode = AlarmContract.RequestCode.test(),
        )
        return triggerAt
    }

    // ---------------------------------------------------------------- cancelamento

    fun cancelWake(alarmId: Long) =
        cancel(AlarmContract.RequestCode.wake(alarmId), AlarmMode.WAKE, alarmId, AlarmContract.NO_ID)

    fun cancelSnooze(alarmId: Long, occurrenceId: Long) =
        cancel(AlarmContract.RequestCode.snooze(occurrenceId), AlarmMode.SNOOZE, alarmId, occurrenceId)

    fun cancelChase(alarmId: Long, occurrenceId: Long) =
        cancel(AlarmContract.RequestCode.chase(occurrenceId), AlarmMode.CHASE, alarmId, occurrenceId)

    // ---------------------------------------------------------------- reconstrução

    /**
     * Reconstrói todo o agendamento a partir do banco.
     *
     * Obrigatório após reboot, atualização do app e mudança de fuso/relógio: os
     * `PendingIntent` do `AlarmManager` vivem em memória do sistema e não sobrevivem
     * a nada disso. Alarme que some depois de reiniciar o celular é o bug clássico
     * e silencioso deste tipo de app.
     */
    suspend fun rescheduleAll() {
        val alarms = reapSpentOneShots(db.alarmDao().getEnabled())
        alarms.forEach { scheduleNextWake(it) }

        // Cobranças pendentes também precisam voltar.
        val pending = db.occurrenceDao().getWithPendingEvidence()
        for (occurrence in pending) {
            val deadline = db.missionInstanceDao()
                .getAwaitingEvidence(occurrence.id)
                .mapNotNull { it.evidenceDeadline }
                .minOrNull() ?: continue
            // Prazo já vencido durante o desligamento: cobra assim que der.
            val triggerAt = maxOf(deadline, System.currentTimeMillis() + 15_000L)
            scheduleChase(occurrence.alarmId, occurrence.id, triggerAt)
        }

        Log.i(TAG, "Reagendado: ${alarms.size} alarme(s), ${pending.size} cobrança(s)")
    }

    /**
     * Apaga os despertadores de uma vez só que já cumpriram seu papel e devolve o resto.
     *
     * A autodestruição normal acontece quando você desliga o alarme, mas nem todo
     * caminho passa por ali: o aparelho pode estar desligado na hora marcada, ou o
     * disparo pode se perder. Sem esta rede, sobraria na lista um despertador ligado
     * que nunca mais vai tocar — exatamente o entulho que o modo existe para evitar.
     *
     * Quem tem disparo em curso é poupado: um despertador único em soneca ainda tem
     * um toque a caminho, mesmo que sua data já tenha passado.
     */
    private suspend fun reapSpentOneShots(alarms: List<AlarmEntity>): List<AlarmEntity> {
        if (alarms.none { it.isOneShot }) return alarms
        val ringing = db.occurrenceDao().getActive().mapTo(mutableSetOf()) { it.alarmId }

        val (spent, alive) = alarms.partition {
            it.isOneShot && it.id !in ringing && it.outlook(now()).nextFire == null
        }
        spent.forEach {
            cancelWake(it.id)
            db.alarmDao().delete(it)
            Log.i(TAG, "Despertador único ${it.id} (${it.label}) expirou e foi removido")
        }
        return alive
    }

    // ---------------------------------------------------------------- internos

    private fun setExact(
        triggerAtMillis: Long,
        operation: PendingIntent,
        showIntentRequestCode: Int,
    ) {
        val showIntent = PendingIntent.getActivity(
            context,
            showIntentRequestCode,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (canScheduleExact()) {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                operation,
            )
        } else {
            // Degradação consciente: sem permissão de alarme exato o sistema pode
            // atrasar o disparo em alguns minutos. A tela de Diagnóstico avisa.
            Log.w(TAG, "Sem permissão de alarme exato — caindo no modo aproximado")
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                operation,
            )
        }
    }

    private fun firePendingIntent(
        requestCode: Int,
        mode: AlarmMode,
        alarmId: Long,
        occurrenceId: Long,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmContract.ACTION_FIRE
            putExtra(AlarmContract.EXTRA_MODE, mode.name)
            putExtra(AlarmContract.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmContract.EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancel(requestCode: Int, mode: AlarmMode, alarmId: Long, occurrenceId: Long) {
        val pi = firePendingIntent(requestCode, mode, alarmId, occurrenceId)
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}
