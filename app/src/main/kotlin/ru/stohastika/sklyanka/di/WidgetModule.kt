package ru.stohastika.sklyanka.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.feature.widgets.open.WidgetOpenTarget
import ru.stohastika.sklyanka.widget.RepositoryWidgetSources
import ru.stohastika.sklyanka.widget.WidgetOpenTargetImpl
import ru.stohastika.sklyanka.widget.WidgetSources
import javax.inject.Singleton

/**
 * Связки виджетов на стороне приложения (SKL-118, `TDD.md §2`, §12).
 *
 * Единственная связка здесь — куда ведёт нажатие на плиту: маршруты знает только `app`
 * (ADR-0007), и модуль виджетов зовёт порт, не зная ни имён активностей, ни состава
 * навигации. Остальные зависимости виджетов даёт их собственный модуль и граф приложения.
 *
 * Слот витрины «Про» (`ProShowcaseTarget`)
 * связывается вариантами: в `free` — no-op, в `pro` — настоящая шторка (SKL-153).
 */
@Module
@InstallIn(SingletonComponent::class)
interface WidgetModule {

    @Binds
    @Singleton
    fun openTarget(implementation: WidgetOpenTargetImpl): WidgetOpenTarget

    /** Источники изменений для наблюдателя плит: активный профиль, лекарства, приёмы дня. */
    @Binds
    @Singleton
    fun widgetSources(implementation: RepositoryWidgetSources): WidgetSources
}
