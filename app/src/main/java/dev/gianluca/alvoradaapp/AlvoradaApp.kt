package dev.gianluca.alvoradaapp

import android.app.Application
import android.content.Context
import dev.gianluca.alvoradaapp.alarm.AlarmScheduler
import dev.gianluca.alvoradaapp.alarm.AlarmSoundStore
import dev.gianluca.alvoradaapp.alarm.Notifications
import dev.gianluca.alvoradaapp.audio.AudioLibraryStore
import dev.gianluca.alvoradaapp.audio.AudioTrimmer
import dev.gianluca.alvoradaapp.audio.MediaResolvers
import dev.gianluca.alvoradaapp.audio.WaveformExtractor
import dev.gianluca.alvoradaapp.backup.BackupManager
import dev.gianluca.alvoradaapp.data.AlarmRepository
import dev.gianluca.alvoradaapp.data.AppPreferences
import dev.gianluca.alvoradaapp.data.MissionRepository
import dev.gianluca.alvoradaapp.data.PanelRepository
import dev.gianluca.alvoradaapp.data.PointsRepository
import dev.gianluca.alvoradaapp.data.AlvoradaDatabase
import dev.gianluca.alvoradaapp.data.ChecklistRepository
import dev.gianluca.alvoradaapp.evidence.EvidenceStore
import dev.gianluca.alvoradaapp.work.DailySweepWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Injeção de dependência manual.
 *
 * Hilt resolveria o mesmo problema com anotações e geração de código; num app deste
 * porte isso troca clareza por magia. Aqui dá para ler o grafo inteiro de uma vez.
 */
class AppContainer(context: Context) {

    val db: AlvoradaDatabase by lazy { AlvoradaDatabase.build(context) }

    val alarmScheduler: AlarmScheduler by lazy { AlarmScheduler(context, db) }

    val alarmRepository: AlarmRepository by lazy {
        AlarmRepository(context.applicationContext, db, alarmScheduler)
    }

    val soundStore: AlarmSoundStore by lazy { AlarmSoundStore(context, audioLibraryStore) }

    val evidenceStore: EvidenceStore by lazy { EvidenceStore(context) }

    val audioLibraryStore: AudioLibraryStore by lazy { AudioLibraryStore(context) }

    val mediaResolvers: MediaResolvers by lazy { MediaResolvers() }

    val audioTrimmer: AudioTrimmer by lazy { AudioTrimmer(context, audioLibraryStore) }

    val waveformExtractor: WaveformExtractor by lazy { WaveformExtractor(context) }

    val pointsRepository: PointsRepository by lazy { PointsRepository(db) }

    val missionRepository: MissionRepository by lazy {
        MissionRepository(db, alarmScheduler, evidenceStore, pointsRepository)
    }

    val checklistRepository: ChecklistRepository by lazy {
        ChecklistRepository(db, alarmRepository, pointsRepository)
    }

    val panelRepository: PanelRepository by lazy { PanelRepository(db) }

    val backupManager: BackupManager by lazy { BackupManager(context, db) }

    val preferences: AppPreferences by lazy { AppPreferences(context) }
}

class AlvoradaApp : Application() {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)

        // Rede de segurança do reagendamento. O BootReceiver cobre os casos normais,
        // mas alguns fabricantes engolem o BOOT_COMPLETED de apps que o usuário nunca
        // abriu depois de reiniciar — reagendar na abertura fecha esse buraco.
        scope.launch {
            runCatching { container.alarmScheduler.rescheduleAll() }
        }

        DailySweepWorker.schedule(this)

        // Migração única dos sons da V1 para a biblioteca pública. Roda em segundo
        // plano porque não bloqueia nada: os `file://` antigos continuam tocando até
        // serem reescritos, e quando a pasta antiga esvazia isto vira um no-op.
        scope.launch {
            runCatching {
                container.soundStore.migrateLegacySounds { from, to ->
                    container.db.alarmDao().rewriteSoundUri(from, to)
                }
            }
        }
    }
}
