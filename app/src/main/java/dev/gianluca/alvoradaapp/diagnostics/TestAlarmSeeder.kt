package dev.gianluca.alvoradaapp.diagnostics

import android.util.Log
import dev.gianluca.alvoradaapp.alarm.AlarmScheduler
import dev.gianluca.alvoradaapp.core.DayMask
import dev.gianluca.alvoradaapp.data.AlarmEntity
import dev.gianluca.alvoradaapp.data.FolderEntity
import dev.gianluca.alvoradaapp.data.AlvoradaDatabase
import java.time.ZonedDateTime

/**
 * Cria um despertador **de verdade**, gravado no banco e agendado pelo caminho normal.
 *
 * O alarme de teste do `AlarmScheduler.scheduleTest` valida a cadeia de disparo, mas
 * não passa pelo banco — e portanto não sobrevive a um reboot, que é justamente a
 * verificação mais importante da Fase 0. Este semeador existe para permitir o teste
 * de reboot antes de a Fase 1 trazer a tela de cadastro.
 */
class TestAlarmSeeder(
    private val db: AlvoradaDatabase,
    private val scheduler: AlarmScheduler,
) {

    /** Retorna o instante agendado, ou `null` se o agendamento falhou. */
    suspend fun seed(minutesFromNow: Long): Long? {
        val target = ZonedDateTime.now().plusMinutes(minutesFromNow)

        val folderId = db.folderDao().getAll()
            .firstOrNull { it.name == FOLDER_NAME }
            ?.id
            ?: db.folderDao().upsert(FolderEntity(name = FOLDER_NAME, colorHex = "#3DDC97"))

        val alarm = AlarmEntity(
            folderId = folderId,
            label = "Despertador de teste",
            hour = target.hour,
            minute = target.minute,
            // Só o dia em que ele cai — evita que um teste esquecido toque toda semana.
            daysMask = DayMask.bitOf(target.dayOfWeek),
            snoozeMinutes = 1,
            maxSnoozes = 2,
            escalateVolume = false,
            createdAt = System.currentTimeMillis(),
        )

        val id = db.alarmDao().upsert(alarm)
        val scheduledFor = scheduler.scheduleNextWake(alarm.copy(id = id))
        Log.i(TAG, "Despertador de teste $id agendado para $scheduledFor")
        return scheduledFor
    }

    /** Remove os despertadores semeados, para o Diagnóstico não virar lixeira. */
    suspend fun clear(): Int {
        val folder = db.folderDao().getAll().firstOrNull { it.name == FOLDER_NAME } ?: return 0
        val alarms = db.alarmDao().getEnabled().filter { it.folderId == folder.id }
        alarms.forEach {
            scheduler.cancelWake(it.id)
            db.alarmDao().delete(it)
        }
        db.folderDao().delete(folder)
        return alarms.size
    }

    private companion object {
        const val TAG = "TestAlarmSeeder"
        const val FOLDER_NAME = "Diagnóstico"
    }
}
