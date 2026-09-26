package ru.heuri.sklyanka.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграции схемы.
 *
 * Правила, каждое из которых оплачено чужой потерей данных:
 * список колонок в `INSERT … SELECT` пишется явно; индексы после перестроения таблицы
 * пересоздаются вручную (DROP TABLE уносит их с собой, а Room сверит схему при открытии
 * и откажется работать); разрушающей миграции нет ни в одной сборке (МГ-2).
 *
 * На каждую миграцию пишется тест на реальной базе предыдущей версии (МГ-3).
 */
object SklyankaMigrations {

    /**
     * Версия 1 → 2: техническая таблица-маркер уходит, появляется схема данных.
     *
     * В версии 1 (скелет SKL-02) была единственная таблица `database_marker`, заведённая
     * только потому, что Room не генерирует базу без сущностей. Продуктовых данных в ней
     * нет и быть не могло, поэтому она просто удаляется — это единственная запись в истории
     * схемы, удаление которой ничего не теряет.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `database_marker`")
            SCHEMA_2.forEach(db::execSQL)
        }
    }

    /** Все миграции по порядку. Room выбирает путь сам; пропусков в цепочке быть не должно. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)

    /**
     * DDL версии 2 — дословно то, что Room ожидает увидеть при сверке схемы.
     *
     * Совпадение проверяется не глазами: тест миграции прогоняет реальную базу версии 1
     * и просит Room проверить схему, а любое расхождение в типе, порядке колонок или
     * имени индекса останавливает прогон.
     */
    private val SCHEMA_2 = listOf(
        """
        CREATE TABLE IF NOT EXISTS `profile` (
            `id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL,
            `is_self` INTEGER NOT NULL, `mirror_reminders` INTEGER NOT NULL,
            `self_slot` TEXT, PRIMARY KEY(`id`)
        )
        """,
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_profile_self_slot` ON `profile` (`self_slot`)",
        """
        CREATE TABLE IF NOT EXISTS `medication` (
            `id` TEXT NOT NULL, `profile_id` TEXT NOT NULL, `name` TEXT NOT NULL,
            `form` TEXT NOT NULL, `shape` TEXT, `dose_amount` TEXT NOT NULL,
            `dose_unit` TEXT NOT NULL, `strength` TEXT, `strength_unit` TEXT,
            `food_relation` TEXT NOT NULL, `schedule_mode` TEXT NOT NULL, `max_per_day` INTEGER,
            `times` TEXT NOT NULL, `start_date` TEXT NOT NULL, `end_date` TEXT,
            `stock_left` TEXT, `stock_threshold_days` INTEGER, `archived_at` INTEGER,
            `archive_reason` TEXT, PRIMARY KEY(`id`),
            FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`)
                ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """,
        """
        CREATE INDEX IF NOT EXISTS `index_medication_profile_id_archived_at`
            ON `medication` (`profile_id`, `archived_at`)
        """,
        """
        CREATE TABLE IF NOT EXISTS `intake` (
            `id` TEXT NOT NULL, `medication_id` TEXT NOT NULL, `scheduled_at` TEXT NOT NULL,
            `schedule_slot` TEXT, `status` TEXT NOT NULL, `acted_at` INTEGER, `source` TEXT,
            `actor_profile_id` TEXT, PRIMARY KEY(`id`),
            FOREIGN KEY(`medication_id`) REFERENCES `medication`(`id`)
                ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(`actor_profile_id`) REFERENCES `profile`(`id`)
                ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """,
        """
        CREATE UNIQUE INDEX IF NOT EXISTS `index_intake_medication_id_scheduled_at_schedule_slot`
            ON `intake` (`medication_id`, `scheduled_at`, `schedule_slot`)
        """,
        """
        CREATE INDEX IF NOT EXISTS `index_intake_scheduled_at_status`
            ON `intake` (`scheduled_at`, `status`)
        """,
        "CREATE INDEX IF NOT EXISTS `index_intake_actor_profile_id` ON `intake` (`actor_profile_id`)",
        """
        CREATE TABLE IF NOT EXISTS `op_log` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `at` INTEGER NOT NULL,
            `event` TEXT NOT NULL, `profile_id` TEXT, `medication_id` TEXT, `intake_id` TEXT,
            `value_before` TEXT, `value_after` TEXT, `source` TEXT, `detail` TEXT
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_op_log_at` ON `op_log` (`at`)",
    ).map { it.trimIndent() }
}
