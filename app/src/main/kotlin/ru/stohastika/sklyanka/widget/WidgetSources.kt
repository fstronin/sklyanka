package ru.stohastika.sklyanka.widget

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import ru.stohastika.sklyanka.domain.appearance.AppearancePreferences
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.time.TimeSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Изменения данных, за которыми следит плита (SKL-118, `TDD.md §7`).
 *
 * Порт, а не три репозитория в наблюдателе: правило «на изменение — перерисовка» тогда
 * проверяется без базы, а сам наблюдатель не знает, из каких таблиц приходят данные.
 * Состав изменений — активный профиль, его лекарства и приёмы сегодняшних суток: ими
 * закрываются смена профиля, правка и архивация курса, перенос данных, отметка и её отмена,
 * полуночный проход.
 */
interface WidgetSources {

    /** Поток изменений: любое значение — повод перерисовать плиты. */
    fun changes(): Flow<Unit>
}

/** Изменения данных приложения: тот же состав, что читает снимок плиты. */
@Singleton
class RepositoryWidgetSources @Inject constructor(
    private val active: ActiveProfileRepository,
    private val medications: MedicationRepository,
    private val intakes: IntakeRepository,
    private val time: TimeSource,
    /**
     * Оформление: плита читает акцентную тему в момент отрисовки (§5.11, SKL-120).
     *
     * Без этого потока выбор новой темы не просил бы перерисовку: приложение перекрасилось
     * бы, а плита несла бы прежний акцент до следующего события данных — то есть до
     * ближайшей отметки или правки лекарства.
     */
    private val appearance: AppearancePreferences,
) : WidgetSources {

    /**
     * Значения потока не сравниваются: любое изменение состава — повод перерисовать.
     *
     * Различать «то же самое» здесь нечего: списки приходят из хранилища новыми объектами,
     * и сравнение содержимого стоило бы дороже, чем лишний запуск сессии.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun changes(): Flow<Unit> = active.activeProfileId
        .flatMapLatest { profileId ->
            combine(
                medications.observeActive(profileId),
                intakes.observeDay(profileId, time.today()),
                appearance.accentTheme,
            ) { scheduled, today, _ -> scheduled to today }
        }
        .map { Unit }
}
