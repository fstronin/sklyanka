package ru.stohastika.sklyanka.data.room

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

    /**
     * Версия 2 → 3: у лекарства появляется признак дублирования напоминаний на часы.
     *
     * ЧАС-15 требует тумблера на каждое лекарство, а в перечне полей модели данных такого
     * поля не было (ОВ-58) — тумблеру карточки лекарства (SKL-10) хранить настройку негде.
     *
     * Один `ALTER TABLE ADD COLUMN` без перестроения таблицы: строки не переписываются,
     * индексы и внешние ключи остаются на месте, ни одна запись приёма не затрагивается
     * (МГ-2). `NOT NULL DEFAULT 1` заполняет уже заведённые курсы: дублирование включено,
     * а действующим ограничителем остаётся общее состояние связи с часами в настройках
     * (ЧАС-15). Значение по умолчанию согласуется с сущностью — иначе Room отвергнет
     * схему при открытии базы.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `medication` ADD COLUMN `mirror_to_watch` INTEGER NOT NULL DEFAULT 1")
        }
    }

    /**
     * Версия 3 → 4: появляется журнал срабатываний напоминаний (SKL-13, НФТ-50).
     *
     * Существующие таблицы не трогаются вовсе: добавление таблицы не переносит данные
     * и не перестраивает индексы, поэтому история приёмов физически не может пострадать.
     * Журнал сам по себе восстановим — но едет он в одной базе с историей, и разрушающей
     * миграции здесь нет ни при каких условиях (МГ-2).
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SCHEMA_4.forEach(db::execSQL)
        }
    }

    /**
     * Версия 4 → 5: у лекарства появляется размер упаковки (SKL-34, ОВ-45).
     *
     * Один `ALTER TABLE ADD COLUMN` без перестроения таблицы: строки не переписываются,
     * индексы и внешние ключи остаются на месте, ни одна запись приёма не затрагивается
     * (МГ-2). Старые строки получают `NULL` — «размер не задан»: знаменатель не выдуман
     * из остатка, иначе полоса заполнения врала бы убедительнее текста.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `medication` ADD COLUMN `package_size` TEXT")
        }
    }

    /**
     * Версия 5 → 6: появляется таблица отложенных срабатываний (SKL-35, ОВ-75).
     *
     * Отдельная таблица, а не колонки `intake`: откладывание — состояние доставки,
     * а не медицинская запись. Существующие таблицы не трогаются вовсе, поэтому история
     * приёмов физически не может пострадать (МГ-2). Внешний ключ с `ON DELETE CASCADE`
     * уносит запись вместе с приёмом: пересоздание будущих приёмов при правке расписания
     * и замена данных при переносе оставляют её без предмета.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `deferred_reminder` (
                    `intake_id` TEXT NOT NULL, `fire_at` INTEGER NOT NULL, `attempt` INTEGER NOT NULL,
                    PRIMARY KEY(`intake_id`),
                    FOREIGN KEY(`intake_id`) REFERENCES `intake`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
        }
    }

    /** Все миграции по порядку. Room выбирает путь сам; пропусков в цепочке быть не должно. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
    )

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

    /**
     * DDL версии 4 — журнал срабатываний напоминаний.
     *
     * Порядок и типы колонок дословно те, что ожидает Room: расхождение остановит
     * инструментальный тест миграции на сверке схемы, а не человека на ревью (МГ-3).
     */
    private val SCHEMA_4 = listOf(
        """
        CREATE TABLE IF NOT EXISTS `reminder_log` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `occurred_at` INTEGER NOT NULL,
            `event` TEXT NOT NULL, `intake_id` TEXT, `medication_id` TEXT, `profile_id` TEXT,
            `scheduled_at` INTEGER, `fired_at` INTEGER, `deviation_seconds` INTEGER,
            `channel` TEXT, `delivered` INTEGER, `reason` TEXT, `alarms_cancelled` INTEGER,
            `alarms_scheduled` INTEGER, `attempt` INTEGER, `snooze_minutes` INTEGER,
            `watch_result` TEXT
        )
        """,
        "CREATE INDEX IF NOT EXISTS `index_reminder_log_occurred_at` ON `reminder_log` (`occurred_at`)",
        "CREATE INDEX IF NOT EXISTS `index_reminder_log_intake_id` ON `reminder_log` (`intake_id`)",
    ).map { it.trimIndent() }
}
