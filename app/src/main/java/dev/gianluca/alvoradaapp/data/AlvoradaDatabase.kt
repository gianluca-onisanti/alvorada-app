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
    ],
    version = 5,
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

        fun build(context: Context): AlvoradaDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AlvoradaDatabase::class.java,
                "wake.db",
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                // Só para downgrade — avançar sem migration passa a ser erro, e não
                // uma perda silenciosa de dados.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
