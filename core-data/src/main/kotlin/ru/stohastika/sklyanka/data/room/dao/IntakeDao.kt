package ru.stohastika.sklyanka.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.stohastika.sklyanka.data.room.entity.IntakeEntity

/**
 * Приёмы.
 *
 * Сутки и периоды выбираются диапазоном по текстовому локальному времени —
 * без арифметики «минус 24 часа»: в сутках с переводом времени их 23 или 25 (НФТ-37).
 * Границы приходят из домена уже в формате хранения.
 *
 * Экранные выборки выпадают из удалённого лекарства (ПР-14). Исключения сознательные
 * и названы у своих запросов: проход по просроченным обязан довести его приёмы до
 * `MISSED`, а сравнение плана с фактом — увидеть будущие `PENDING`, чтобы снять их.
 */
@Dao
// Один интерфейс на таблицу приёмов, а не два: выборки, отметка, снятие, автоперевод
// и удаление спрашивают одну и ту же таблицу, и разделение по числу методов завело бы
// второе место, где перечислены её колонки.
@Suppress("TooManyFunctions")
interface IntakeDao {

    @Query(
        """
        SELECT i.* FROM intake i
        JOIN medication m ON m.id = i.medication_id
        WHERE m.profile_id = :profileId
          AND m.deleted_at IS NULL
          AND i.revoked_at IS NULL
          AND i.scheduled_at >= :dayStart AND i.scheduled_at < :nextDayStart
        ORDER BY i.scheduled_at
        """,
    )
    fun observeDay(profileId: String, dayStart: String, nextDayStart: String): Flow<List<IntakeEntity>>

    @Query(
        """
        SELECT i.* FROM intake i
        JOIN medication m ON m.id = i.medication_id
        WHERE m.profile_id = :profileId
          AND m.deleted_at IS NULL
          AND i.revoked_at IS NULL
          AND i.scheduled_at >= :from AND i.scheduled_at < :to
        ORDER BY i.scheduled_at
        """,
    )
    fun observePeriod(profileId: String, from: String, to: String): Flow<List<IntakeEntity>>

    /**
     * История по лекарству за период.
     *
     * Фильтр по профилю здесь не избыточен, хотя лекарство однозначно задаёт профиль:
     * идентификатор лекарства приходит из навигации, то есть из внешнего мира, и после
     * переноса данных теоретически может указывать в чужой профиль.
     */
    @Query(
        """
        SELECT i.* FROM intake i
        JOIN medication m ON m.id = i.medication_id
        WHERE i.medication_id = :medicationId AND m.profile_id = :profileId
          AND m.deleted_at IS NULL
          AND i.revoked_at IS NULL
          AND i.scheduled_at >= :from AND i.scheduled_at < :to
        ORDER BY i.scheduled_at
        """,
    )
    fun observeByMedication(
        profileId: String,
        medicationId: String,
        from: String,
        to: String,
    ): Flow<List<IntakeEntity>>

    /**
     * Есть ли у лекарства отметка `TAKEN` — граница мягкого удаления (ИНВ-22).
     *
     * `EXISTS` останавливается на первой найденной строке и идёт по индексу
     * `(medication_id, scheduled_at, schedule_slot)`: спрашивается это на каждой сборке
     * карточки, пока действие «Удалить» доступно.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM intake i
            JOIN medication m ON m.id = i.medication_id
            WHERE i.medication_id = :medicationId AND m.profile_id = :profileId
              AND i.status = 'TAKEN'
              AND i.revoked_at IS NULL
        )
        """,
    )
    fun observeHasTaken(profileId: String, medicationId: String): Flow<Boolean>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM intake i
            JOIN medication m ON m.id = i.medication_id
            WHERE i.medication_id = :medicationId AND m.profile_id = :profileId
              AND i.status = 'TAKEN'
              AND i.revoked_at IS NULL
        )
        """,
    )
    suspend fun hasTaken(profileId: String, medicationId: String): Boolean

    @Query("SELECT * FROM intake WHERE id = :intakeId")
    suspend fun getById(intakeId: String): IntakeEntity?

    /**
     * Приёмы по расписанию одного лекарства внутри окна генерации (SKL-06).
     *
     * Запрос идёт по индексу (medication_id, scheduled_at, schedule_slot) — тому самому,
     * который держит уникальность приёма по расписанию (INV-13).
     *
     * Условие по слоту отсеивает приёмы по потребности: слот у них NULL, плану они не
     * подчиняются и удалению при перепланировании не подлежат (INV-14). Сравнение идёт
     * с NULL, а не со значением слота, потому что значение — деталь хранения, а смысл
     * условия — «запись порождена расписанием».
     */
    @Query(
        """
        SELECT i.* FROM intake i
        JOIN medication m ON m.id = i.medication_id
        WHERE i.medication_id = :medicationId AND m.profile_id = :profileId
          AND i.schedule_slot IS NOT NULL
          AND i.scheduled_at >= :from AND i.scheduled_at < :to
        ORDER BY i.scheduled_at
        """,
    )
    suspend fun selectScheduledBetween(
        profileId: String,
        medicationId: String,
        from: String,
        to: String,
    ): List<IntakeEntity>

    /**
     * Снятие ожидающихся приёмов, переставших соответствовать расписанию.
     *
     * Условие `status = 'PENDING'` стоит внутри самого DELETE: между чтением окна и записью
     * помещается отметка с часов, и без этого условия перепланирование стёрло бы её вместе
     * с записью. Отметка неприкосновенна при любой правке расписания (ПР-11).
     *
     * Список идентификаторов приходит из окна генерации и потому короток — десятки записей,
     * а не тысячи: предела SQLite на число параметров запроса он не достигает.
     */
    @Query("DELETE FROM intake WHERE id IN (:intakeIds) AND status = 'PENDING'")
    suspend fun deletePending(intakeIds: List<String>): Int

    /**
     * Новый снимок дозы у ещё не отмеченного приёма (ПР-19).
     *
     * Правка ступеней меняет дозу приёма, но не момент: запись остаётся той же, вместе
     * с будильником и адресом карточки (НАП-9), а меняется только снимок. Условие
     * `status = 'PENDING'` стоит внутри самого UPDATE — отметка неприкосновенна
     * (ПР-11): отмеченный приём списан по своей дозе, и переписывать её задним числом
     * значило бы разойтись с остатком. `null` в `dose_amount` осмыслен: доза — у лекарства.
     */
    @Query("UPDATE intake SET dose_amount = :doseAmount WHERE id = :intakeId AND status = 'PENDING'")
    suspend fun refreshPendingDose(intakeId: String, doseAmount: String?): Int

    /**
     * Вставка пачкой с пропуском конфликтов.
     *
     * Повторная генерация того же приёма — ожидаемый ход событий, а не ошибка:
     * уникальность держит индекс по слоту, а число реально вставленных строк
     * возвращается наружу и уходит в журнал.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(intakes: List<IntakeEntity>): List<Long>

    /**
     * Ядро идемпотентности (ПР-2, ПР-3, INV-19).
     *
     * Условие `status = 'PENDING'` стоит внутри самого UPDATE: сравнение и запись —
     * одна атомарная операция. Вторая отметка получает ноль изменённых строк и не делает
     * ничего — ни записи, ни списания остатка. Чтение «а отмечен ли уже?» перед записью
     * не годится: между чтением и записью помещается отметка с часов.
     */
    @Query(
        """
        UPDATE intake
        SET status = :status, acted_at = :actedAt, source = :source,
            actor_profile_id = :actorProfileId
        WHERE id = :intakeId AND status = 'PENDING'
        """,
    )
    suspend fun markIfPending(
        intakeId: String,
        status: String,
        actedAt: Long,
        source: String,
        actorProfileId: String?,
    ): Int

    /**
     * Замещение отметки более ранним действием (ПР-3, ЧАС-22).
     *
     * Условие `acted_at > :actedAt` стоит внутри самого UPDATE по той же причине, что
     * и условие статуса у [markIfPending]: проверка «а не старше ли записанная отметка?»
     * отдельным чтением оставляет между чтением и записью место для третьего события.
     * Отметка, не более ранняя записанной, получает ноль изменённых строк и не делает
     * ничего — второй записи не появляется ни в одном исходе (ПР-2, НФТ-10).
     */
    @Query(
        """
        UPDATE intake
        SET status = :status, acted_at = :actedAt, source = :source,
            actor_profile_id = :actorProfileId
        WHERE id = :intakeId AND acted_at IS NOT NULL AND acted_at > :actedAt
        """,
    )
    suspend fun supersedeIfLater(
        intakeId: String,
        status: String,
        actedAt: Long,
        source: String,
        actorProfileId: String?,
    ): Int

    /**
     * Отмена состоявшейся отметки (SKL-30).
     *
     * Условие `status IN ('TAKEN', 'SKIPPED')` стоит в самом UPDATE по той же причине,
     * что у отметки и замещения: между чтением и записью помещается отметка с часов.
     * `MISSED` в условие не входит намеренно: «пропущено без отметки» — подпись
     * приложения, и отмена не имеет права воскресить её задним числом. Ноль изменённых
     * строк — не молчание: он попадает в журнал служебным кодом.
     */
    @Query(
        """
        UPDATE intake
        SET status = 'PENDING', acted_at = NULL, source = NULL, actor_profile_id = NULL
        WHERE id = :intakeId AND status IN ('TAKEN', 'SKIPPED')
        """,
    )
    suspend fun unmark(intakeId: String): Int

    /**
     * Снятие записи «по потребности» (Р-46, SKL-127): мягкий признак, а не удаление.
     *
     * Условный запрос, как [markIfPending] и [unmark]: повторное снятие меняет ноль строк
     * и не даёт вернуть дозу второй раз. Условие по статусу отсекает запись, которую
     * успели перевести в «пропущено»: снимается только состоявшаяся отметка «принято».
     */
    @Query(
        """
        UPDATE intake
        SET revoked_at = :at
        WHERE id = :intakeId AND revoked_at IS NULL AND status = 'TAKEN'
        """,
    )
    suspend fun revoke(intakeId: String, at: Long): Int

    /** Просроченные ожидающие приёмы для перевода в MISSED (ПР-1); граница окна — параметр домена (ОВ-4). */
    @Query("SELECT * FROM intake WHERE status = 'PENDING' AND scheduled_at < :before")
    suspend fun selectExpiredPending(before: String): List<IntakeEntity>

    /**
     * Пакетный перевод в MISSED по истечении окна (ПР-1).
     *
     * Условие `status = 'PENDING'` делает проход идемпотентным на уровне запроса:
     * повторный проход по уже переведённым записям не переписывает момент перевода,
     * даже если домен ошибся в выборке (НАП-34).
     */
    @Query(
        """
        UPDATE intake
        SET status = 'MISSED', acted_at = :actedAt, source = 'AUTO', actor_profile_id = NULL
        WHERE id IN (:intakeIds) AND status = 'PENDING'
        """,
    )
    suspend fun markMissedIfPending(intakeIds: List<String>, actedAt: Long): Int

    /**
     * Сколько приёмов лекарства пришлось на одни местные сутки — счётчик суточного
     * лимита приёмов по потребности (ИНВ-17).
     *
     * Границы суток приходят из домена уже текстом: арифметики над временем в SQL нет,
     * потому что в сутках с переводом времени их 23 или 25 (НФТ-37).
     */
    @Query(
        """
        SELECT COUNT(*) FROM intake i
        JOIN medication m ON m.id = i.medication_id
        WHERE i.medication_id = :medicationId AND m.profile_id = :profileId
          AND m.deleted_at IS NULL
          AND i.revoked_at IS NULL
          AND i.scheduled_at >= :dayStart AND i.scheduled_at < :nextDayStart
        """,
    )
    suspend fun countOnDay(profileId: String, medicationId: String, dayStart: String, nextDayStart: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM intake i
        JOIN medication m ON m.id = i.medication_id
        WHERE m.profile_id = :profileId
        """,
    )
    suspend fun countInProfile(profileId: String): Int

    /**
     * Число записей истории профиля потоком — числа потерь на экране удаления (SKL-36).
     *
     * То же соединение, что у [countInProfile] и у истории: отдельного учёта записей
     * приложение не ведёт. Поток, потому что экран подтверждения остаётся открытым
     * и во время выбора места выгрузки, и число обязано не разойтись со стираемым.
     */
    @Query(
        """
        SELECT COUNT(*) FROM intake i
        JOIN medication m ON m.id = i.medication_id
        WHERE m.profile_id = :profileId
        """,
    )
    fun observeCountInProfile(profileId: String): Flow<Int>

    /**
     * Удаление всех приёмов профиля вместе с отложенными срабатываниями (SKL-36, ОВ-7).
     *
     * Профиля в `intake` нет — отбор идёт через лекарства (ПР-10). Порядок транзакции
     * задаёт вызывающий: этот запрос идёт первым, иначе лекарство не удалить (RESTRICT).
     * Записи `deferred_reminder` уходят каскадом: откладывать больше нечего.
     */
    @Query("DELETE FROM intake WHERE medication_id IN (SELECT id FROM medication WHERE profile_id = :profileId)")
    suspend fun deleteInProfile(profileId: String): Int

    /** Только для режима «заменить всё» при переносе данных (ПЕР-16). */
    @Query("DELETE FROM intake")
    suspend fun deleteAll(): Int
}
