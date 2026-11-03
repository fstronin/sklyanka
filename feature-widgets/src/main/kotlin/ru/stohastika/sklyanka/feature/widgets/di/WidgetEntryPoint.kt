package ru.stohastika.sklyanka.feature.widgets.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecorder
import ru.stohastika.sklyanka.domain.appearance.ObserveAccentThemeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.widget.ReadWidgetSnapshotsUseCase
import ru.stohastika.sklyanka.feature.widgets.bindings.WidgetBindingStore
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetRefresher
import ru.stohastika.sklyanka.feature.widgets.open.WidgetOpenTarget

/**
 * Доступ виджетов к графу зависимостей (SKL-118, `TDD.md §2`).
 *
 * Явная точка входа, а не `@AndroidEntryPoint`: `GlanceAppWidget` — не компонент Android
 * с внедрением полей, и виджет обязан пережить отсутствие графа: в процессе без приложения
 * продукта (инструментальный прогон тестовой сборки) система всё равно доставляет обновление
 * плиты, и падение на `EntryPointAccessors` уронило бы процесс (SKL-97).
 *
 * Список зависимостей — это ещё и бюджет: у обновления плиты он ограничен платформой,
 * поэтому здесь ровно то, без чего плита не рисуется и не отмечает.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {

    /** Снимок дня, недели и одного лекарства — то, что рисует плита. */
    fun snapshots(): ReadWidgetSnapshotsUseCase

    /** Отметка из виджета — тот же сценарий, что у кнопки в приложении (ПР-22). */
    fun markIntake(): MarkIntakeUseCase

    /**
     * Запись события нажатия плиты (SKL-165).
     *
     * Тот же доменный шов, что у экранов: гейт согласия и очередь живут в нём, а плита
     * сообщает только, какое действие человека случилось.
     */
    fun analytics(): AnalyticsRecorder

    /** Перевод просроченных приёмов в «пропущено без отметки» перед отрисовкой (ПР-1). */
    fun sweepMissed(): SweepMissedIntakesUseCase

    /** Момент времени домена: свежесть снимка проверяется им, а не системными часами (НФТ-48). */
    fun time(): TimeSource

    /** Куда ведёт нажатие на плиту (реализация — в `app`, ADR-0007). */
    fun openTarget(): WidgetOpenTarget

    /** Привязка экземпляра плиты к лекарству у «по лекарству» (Р-34). */
    fun bindings(): WidgetBindingStore

    /**
     * Выбранная акцентная тема (§2.5, §5.11, SKL-120).
     *
     * Плита читает те же роли, что экраны, и акцент владельца у неё — акцент темы,
     * а в тёмной среде — её тёмное значение (Р-23, §9). В чужом профиле акцент — цвет
     * профиля, и он сильнее темы (ПРФ-12), поэтому тему там не спрашивают.
     *
     * Шов тот же, каким тему читает приложение ([ru.stohastika.sklyanka.domain.appearance
     * .ObserveAccentThemeUseCase]): второй путь чтения разошёлся бы с первым, и плита
     * показала бы не ту краску, что экран.
     */
    fun accentTheme(): ObserveAccentThemeUseCase

    /** Перерисовка плит по событию (ПР-1, П-7): одна точка у фоновых просителей. */
    fun refresher(): WidgetRefresher

    companion object {

        fun of(context: Context): WidgetEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    }
}
