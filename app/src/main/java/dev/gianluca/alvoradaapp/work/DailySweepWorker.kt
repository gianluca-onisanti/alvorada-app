package dev.gianluca.alvoradaapp.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.gianluca.alvoradaapp.AlvoradaApp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Varredura de virada de dia.
 *
 * Fecha as missões que ficaram abertas em dias já encerrados. Sem ela a tela de
 * Pendências viraria um cemitério de semanas passadas — e, pior, o app passaria a
 * cobrar coisas que não fazem mais sentido, que é o caminho mais curto para alguém
 * desinstalá-lo.
 *
 * Roda por volta das 03h, e não à meia-noite, para não competir com quem ainda está
 * acordado cumprindo as missões do dia.
 */
class DailySweepWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AlvoradaApp).container
        return runCatching {
            // A ordem importa: fechar as pendências vencidas primeiro, para que a
            // avaliação da sequência veja o dia já no estado final.
            val closed = container.missionRepository.sweepStale()
            container.pointsRepository.evaluateStreak()
            // As rodadas de checklist viram aqui, e **antes** do reagendamento: fechar
            // uma rodada destrava os despertadores que ela calava, e a supressão
            // precisa estar atualizada quando os alarmes forem reafirmados abaixo.
            val cycles = container.checklistRepository.sweepCycles()
            // Rede de segurança: reafirma os agendamentos caso algum tenha se perdido.
            container.alarmScheduler.rescheduleAll()
            Log.i(TAG, "Varredura diária concluída ($closed fechada[s], $cycles rodada[s])")
            Result.success()
        }.getOrElse {
            Log.e(TAG, "Varredura diária falhou", it)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DailySweepWorker"
        private const val WORK_NAME = "daily-sweep"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailySweepWorker>(Duration.ofHours(24))
                .setInitialDelay(durationUntilNextRun())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        private fun durationUntilNextRun(): Duration {
            val now = ZonedDateTime.now()
            var next = now.with(LocalTime.of(3, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }

        /** Exposto para teste manual pela tela de Diagnóstico. */
        fun todayIso(): String = LocalDate.now().toString()
    }
}
