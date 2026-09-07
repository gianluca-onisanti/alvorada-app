package dev.gianluca.alvoradaapp.data

import android.content.Context
import dev.gianluca.alvoradaapp.alarm.AlarmScheduler
import dev.gianluca.alvoradaapp.alarm.AlarmService
import dev.gianluca.alvoradaapp.core.FireOutlook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Uma pasta com os despertadores que moram nela. */
data class FolderWithAlarms(
    val folder: FolderEntity,
    val alarms: List<AlarmEntity>,
)

/**
 * Camada única entre a UI e o par (banco, agendador).
 *
 * Existe porque toda escrita em `alarms` tem um efeito colateral obrigatório no
 * `AlarmManager`: salvar sem reagendar deixa o banco dizendo uma coisa e o sistema
 * fazendo outra. Concentrar isso aqui torna esse par indivisível — nenhuma tela
 * consegue gravar um alarme e esquecer de agendá-lo.
 */
class AlarmRepository(
    private val context: Context,
    private val db: AlvoradaDatabase,
    private val scheduler: AlarmScheduler,
) {

    // ------------------------------------------------------------------ leitura

    fun observeFoldersWithAlarms(): Flow<List<FolderWithAlarms>> =
        combine(db.folderDao().observeAll(), db.alarmDao().observeAll()) { folders, alarms ->
            val byFolder = alarms.groupBy { it.folderId }
            folders.map { FolderWithAlarms(it, byFolder[it.id].orEmpty()) }
        }

    fun observeFolders(): Flow<List<FolderEntity>> = db.folderDao().observeAll()

    suspend fun getAlarm(id: Long): AlarmEntity? = db.alarmDao().getById(id)

    // ------------------------------------------------------------------ escrita

    /** Grava e reagenda. Retorna o id (novo ou existente). */
    suspend fun saveAlarm(alarm: AlarmEntity): Long {
        val id = db.alarmDao().upsert(alarm).let { if (alarm.id == 0L) it else alarm.id }
        val saved = alarm.copy(id = id)

        if (saved.isOneShot) {
            // Um despertador de uma vez só não sustenta missões: ele some depois do
            // primeiro toque e levaria junto qualquer pendência aberta. A UI já não
            // oferece o botão; aqui a regra vira invariante do banco.
            db.missionDao().deleteByAlarm(id)
        } else {
            // Quem escolheu seguir o despertador recebe os dias novos…
            db.missionDao().syncFollowersTo(id, saved.missionDayMask())
            // …e quem tem dias próprios não pode sobrar em dias órfãos.
            // `missionDayMask` e não `daysMask`: num despertador por dia do calendário o
            // dia da semana é imprevisível, e podar por ele apagaria todas as missões.
            db.missionDao().pruneDaysTo(id, saved.missionDayMask())
        }

        scheduler.scheduleNextWake(saved)
        return id
    }

    suspend fun setAlarmEnabled(alarm: AlarmEntity, enabled: Boolean) {
        db.alarmDao().setEnabled(alarm.id, enabled)
        // Ligar ou desligar de vez encerra qualquer pulo pendente: os dois gestos
        // dizem o que fazer com o próximo toque, e o pulo diria outra coisa.
        db.alarmDao().setSkipNextFireAt(alarm.id, null)

        if (enabled) {
            scheduler.scheduleNextWake(alarm.copy(enabled = true, skipNextFireAt = null))
        } else {
            scheduler.cancelWake(alarm.id)
            // Desligar a chave enquanto ele toca precisa calar o som agora. Cancelar
            // o agendamento futuro sem isso resolveria amanhã um problema que é agora.
            AlarmService.stopIfRinging(context, alarm.id)
        }
    }

    /**
     * Pula só o próximo toque. Retorna o instante pulado, ou `null` se não havia toque.
     *
     * O que fica gravado é o instante, não um sinalizador: quando ele deixa de coincidir
     * com o próximo disparo — porque a hora passou ou porque o despertador foi editado —
     * o pulo simplesmente para de valer, sem precisar ser desfeito por ninguém.
     */
    suspend fun skipNextFire(alarm: AlarmEntity): Long? {
        val target = scheduler.peekOutlook(alarm).takeIf { !it.isSkipping }?.nextFire ?: return null
        db.alarmDao().setSkipNextFireAt(alarm.id, target)
        scheduler.scheduleNextWake(alarm.copy(skipNextFireAt = target))
        return target
    }

    suspend fun clearSkipNextFire(alarm: AlarmEntity) {
        db.alarmDao().setSkipNextFireAt(alarm.id, null)
        scheduler.scheduleNextWake(alarm.copy(skipNextFireAt = null))
    }

    /**
     * Autodestruição do despertador de uma vez só.
     *
     * Chamado pelo serviço quando o toque termina — dispensado ou silenciado sozinho.
     * Não passa por [deleteAlarm] de propósito: aquele caminho manda calar o alarme que
     * está tocando, e aqui quem está encerrando o toque é justamente quem chama.
     */
    suspend fun consumeOneShot(alarmId: Long, occurrenceId: Long) {
        val alarm = db.alarmDao().getById(alarmId) ?: return
        if (!alarm.isOneShot) return
        scheduler.cancelWake(alarmId)
        scheduler.cancelSnooze(alarmId, occurrenceId)
        scheduler.cancelChase(alarmId, occurrenceId)
        db.alarmDao().delete(alarm)
    }

    suspend fun deleteAlarm(alarm: AlarmEntity) {
        scheduler.cancelWake(alarm.id)
        AlarmService.stopIfRinging(context, alarm.id)
        db.alarmDao().delete(alarm)
    }

    suspend fun saveFolder(folder: FolderEntity): Long = db.folderDao().upsert(folder)

    suspend fun setFolderCollapsed(folderId: Long, collapsed: Boolean) =
        db.folderDao().setCollapsed(folderId, collapsed)

    /**
     * O `ForeignKey.CASCADE` apaga os alarmes da pasta no banco, mas o `AlarmManager`
     * não sabe disso — os `PendingIntent` continuariam de pé e tocariam um alarme que
     * não existe mais. Por isso o cancelamento vem antes, um a um.
     */
    suspend fun deleteFolder(folder: FolderEntity) {
        val orphans = db.alarmDao().getEnabled().filter { it.folderId == folder.id }
        orphans.forEach {
            scheduler.cancelWake(it.id)
            AlarmService.stopIfRinging(context, it.id)
        }
        db.folderDao().delete(folder)
    }

    /** Garante que exista ao menos uma pasta, para o primeiro despertador ter onde nascer. */
    suspend fun ensureDefaultFolder(): FolderEntity {
        db.folderDao().getAll().firstOrNull()?.let { return it }
        val folder = FolderEntity(name = "Rotina", colorHex = "#7C5CFF")
        return folder.copy(id = db.folderDao().upsert(folder))
    }

    /** Próximo disparo de cada alarme, para a lista mostrar "daqui a 7h12". */
    fun nextFireOf(alarm: AlarmEntity): Long? = scheduler.peekNextFire(alarm)

    /** Previsão completa — inclui o toque pulado, que a lista precisa nomear. */
    fun outlookOf(alarm: AlarmEntity): FireOutlook = scheduler.peekOutlook(alarm)
}
