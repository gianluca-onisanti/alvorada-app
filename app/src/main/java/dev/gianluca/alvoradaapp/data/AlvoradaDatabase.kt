package dev.gianluca.alvoradaapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FolderEntity::class,
        AlarmEntity::class,
        MissionEntity::class,
        OccurrenceEntity::class,
        MissionInstanceEntity::class,
        EvidencePhotoEntity::class,
        PointsLedgerEntity::class,
        RewardEntity::class,
        RewardRedemptionEntity::class,
        StreakStateEntity::class,
        ChecklistEntity::class,
        ChecklistItemEntity::class,
        ChecklistCycleEntity::class,
        ChecklistItemStateEntity::class,
        AudioClipEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AlvoradaDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun alarmDao(): AlarmDao
    abstract fun missionDao(): MissionDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun missionInstanceDao(): MissionInstanceDao
    abstract fun evidencePhotoDao(): EvidencePhotoDao
    abstract fun panelDao(): PanelDao
    abstract fun backupDao(): BackupDao
    abstract fun pointsDao(): PointsDao
    abstract fun rewardDao(): RewardDao
    abstract fun streakDao(): StreakDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun audioClipDao(): AudioClipDao

    companion object {

        /**
         * A partir da versão 3 as migrations são explícitas.
         *
         * Até a 2 o banco era descartável — não havia nada dentro que doesse perder.
         * Agora há despertadores reais configurados à mão, e apagá-los para adicionar
         * uma coluna deixou de ser um preço aceitável.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN collapsed INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Repetição para além do bitmask semanal.
         *
         * Todos os defaults reproduzem o comportamento antigo: `WEEKLY` ignora as
         * colunas novas por completo, então os despertadores já configurados
         * atravessam a migration sem mudar de horário nem de dia.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN repeatKind TEXT NOT NULL DEFAULT 'WEEKLY'")
                db.execSQL("ALTER TABLE alarms ADD COLUMN intervalWeeks INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE alarms ADD COLUMN anchorDate TEXT")
                db.execSQL("ALTER TABLE alarms ADD COLUMN ordinalMask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarms ADD COLUMN monthDaysMask INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Pulo de um toque só e missões que seguem os dias do despertador.
         *
         * O `UPDATE` no fim é o cuidado desta migration. O padrão da coluna nova é
         * "segue o despertador", que é o certo para missões novas — mas aplicá-lo a
         * quem já existe reescreveria escolhas reais: uma missão restrita a segunda e
         * quarta num despertador de Seg–Sex passaria a valer nos cinco dias na próxima
         * vez que o despertador fosse salvo. Por isso só continua seguindo quem já
         * estava exatamente igual ao dono; quem tinha dias próprios os mantém.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN skipNextFireAt INTEGER")
                db.execSQL(
                    "ALTER TABLE missions ADD COLUMN followsAlarmDays INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    """
                    UPDATE missions SET followsAlarmDays = 0
                    WHERE daysMask != (
                        SELECT CASE WHEN a.repeatKind = 'MONTHLY_DAYS' THEN 127 ELSE a.daysMask END
                        FROM alarms a WHERE a.id = missions.alarmId
                    )
                    """
                )
            }
        }

        /**
         * Checklists.
         *
         * Quatro tabelas novas e duas colunas. Nenhuma toca no que já existe: os
         * despertadores atravessam a migration sem mudar de horário, de dia ou de
         * comportamento, porque as colunas novas nascem nulas e nulo significa
         * exatamente o que significava antes de elas existirem — nenhuma supressão
         * em vigor, nenhum crédito de checklist no extrato.
         *
         * O DDL abaixo é transcrito de `app/schemas/.../6.json`, e não escrito à
         * mão: o Room confere a identidade do schema ao abrir o banco e recusa
         * divergências de um detalhe — a ordem de uma coluna, um `ON DELETE`, um
         * índice que falta.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN suppressedUntil INTEGER")
                db.execSQL("ALTER TABLE points_ledger ADD COLUMN checklistItemStateId INTEGER")
                db.execSQL("ALTER TABLE points_ledger ADD COLUMN checklistCycleId INTEGER")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checklists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `folderId` INTEGER NOT NULL, `name` TEXT NOT NULL, `repeatKind` TEXT NOT NULL, `daysMask` INTEGER NOT NULL, `intervalWeeks` INTEGER NOT NULL, `anchorDate` TEXT, `ordinalMask` INTEGER NOT NULL, `monthDaysMask` INTEGER NOT NULL, `dueHour` INTEGER NOT NULL, `dueMinute` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checklists_folderId` ON `checklists` (`folderId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checklist_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `checklistId` INTEGER NOT NULL, `title` TEXT NOT NULL, `notes` TEXT NOT NULL, `alarmId` INTEGER, `xpValue` INTEGER NOT NULL, `coinValue` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `archived` INTEGER NOT NULL, FOREIGN KEY(`checklistId`) REFERENCES `checklists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`alarmId`) REFERENCES `alarms`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checklist_items_checklistId` ON `checklist_items` (`checklistId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checklist_items_alarmId` ON `checklist_items` (`alarmId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checklist_cycles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `checklistId` INTEGER NOT NULL, `periodStart` TEXT NOT NULL, `dueAt` INTEGER NOT NULL, `endsAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `completedAt` INTEGER, FOREIGN KEY(`checklistId`) REFERENCES `checklists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_checklist_cycles_checklistId_periodStart` ON `checklist_cycles` (`checklistId`, `periodStart`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checklist_cycles_status` ON `checklist_cycles` (`status`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checklist_item_states` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cycleId` INTEGER NOT NULL, `itemId` INTEGER NOT NULL, `done` INTEGER NOT NULL, `doneAt` INTEGER, `xpAwarded` INTEGER NOT NULL, `coinAwarded` INTEGER NOT NULL, FOREIGN KEY(`cycleId`) REFERENCES `checklist_cycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`itemId`) REFERENCES `checklist_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_checklist_item_states_cycleId_itemId` ON `checklist_item_states` (`cycleId`, `itemId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checklist_item_states_itemId` ON `checklist_item_states` (`itemId`)"
                )
            }
        }

        /**
         * Origem e linhagem dos áudios.
         *
         * Uma tabela só, e nenhuma coluna nova em `alarms`. De onde um som veio e de
         * onde ele foi recortado é informação do **arquivo**, não do despertador — o
         * mesmo arquivo pode estar em três despertadores, e a resposta é a mesma nos
         * três. `AlarmEntity` continua guardando só `soundUri`/`soundIsSystem`/
         * `soundLabel`, e nenhum caminho de gravação do editor muda por causa disto.
         *
         * Nasce vazia por design, e não por omissão: quem já tem arquivos na pasta
         * segue com eles funcionando, apenas sem origem registrada — que é a verdade
         * sobre eles, já que ninguém guardou de onde vieram na hora em que vieram.
         *
         * DDL transcrito de `app/schemas/.../7.json`, pelo mesmo motivo da 5→6: o Room
         * confere a identidade do schema ao abrir o banco e recusa divergência de um
         * detalhe.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audio_clips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mediaStoreUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `relativePath` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `sourceUrl` TEXT, `sourceTitle` TEXT, `parentClipId` INTEGER, `trimStartMs` INTEGER, `trimEndMs` INTEGER, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`parentClipId`) REFERENCES `audio_clips`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_audio_clips_mediaStoreUri` ON `audio_clips` (`mediaStoreUri`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_audio_clips_parentClipId` ON `audio_clips` (`parentClipId`)"
                )
            }
        }

        fun build(context: Context): AlvoradaDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AlvoradaDatabase::class.java,
                "wake.db",
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                // Só para downgrade — avançar sem migration passa a ser erro, e não
                // uma perda silenciosa de dados.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
