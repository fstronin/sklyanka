package ru.stohastika.sklyanka.domain.appearance

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase

/**
 * Действующая акцентная тема (§2.5, Р-31).
 *
 * Читается и потоком — экран обязан перекраситься сразу, — и разово, на старте: тема
 * применяется **до первой композиции**, иначе первый кадр мелькнул бы «Оливой» и человек
 * с «Аметистом» увидел бы чужой цвет на своём экране (`TDD.md` §5).
 *
 * Доступ «Про» на действующую тему не влияет: выбранное остаётся применённым и после
 * отмены подписки (Р-22, В-120-3). Поэтому этот шов о подписке не спрашивает вовсе —
 * снимается выбор нового, а не показ заведённого.
 */
class ObserveAccentThemeUseCase(private val preferences: AppearancePreferences) {

    operator fun invoke(): Flow<AccentTheme> = preferences.accentTheme

    /** Разовое чтение для старта приложения: тема становится известна до первого кадра. */
    suspend fun current(): AccentTheme = preferences.accentTheme.first()
}

/**
 * Действующий вариант иконки (§2.4, Р-31).
 *
 * Тем же швом, что и тема: переключение лончера — следствие выбора, а не отдельное
 * действие человека, и второй путь чтения разошёлся бы с записанным выбором.
 */
class ObserveIconVariantUseCase(private val preferences: AppearancePreferences) {

    operator fun invoke(): Flow<IconVariant> = preferences.iconVariant

    suspend fun current(): IconVariant = preferences.iconVariant.first()
}

/**
 * Выбор акцентной темы (§5.17, Р-22).
 *
 * Платный выбор проходит только с доступом; без него открывается витрина, и запись
 * не меняется — «применение выбранной темы при этом не меняется, выбор ждёт доступа»
 * (§5.17). Базовая «Олива» проходит всегда: она тема базы (Р-20), а не покупка.
 *
 * Доступ читается из кэша последнего ответа магазина ([ObserveProStatusUseCase]) и не
 * ходит в сеть: выбор оформления не имеет права оказаться между человеком и приложением
 * из-за недоступного магазина (П-6, Р-68 п. 7).
 */
class SetAccentThemeUseCase(
    private val preferences: AppearancePreferences,
    private val proStatus: ObserveProStatusUseCase,
) {

    suspend operator fun invoke(theme: AccentTheme): AppearanceOutcome {
        val outcome = AppearanceGate.choose(isBase = theme.isBase, included = proStatus().included)
        if (outcome == AppearanceOutcome.APPLIED) preferences.setAccentTheme(theme)
        return outcome
    }
}

/**
 * Выбор варианта иконки (§5.17, Р-22) — тем же правилом, что и тема.
 *
 * Тем же гейтом, а не вторым: оба выбора — возможности «Про» (Р-31), и разойтись в том,
 * где проходит платное, им нельзя.
 */
class SetIconVariantUseCase(
    private val preferences: AppearancePreferences,
    private val proStatus: ObserveProStatusUseCase,
) {

    suspend operator fun invoke(variant: IconVariant): AppearanceOutcome {
        val outcome = AppearanceGate.choose(isBase = variant.isBase, included = proStatus().included)
        if (outcome == AppearanceOutcome.APPLIED) preferences.setIconVariant(variant)
        return outcome
    }
}
