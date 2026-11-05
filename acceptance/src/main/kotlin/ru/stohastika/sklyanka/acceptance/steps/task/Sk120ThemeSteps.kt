package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.AppearancePreferences
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import ru.stohastika.sklyanka.domain.appearance.ObserveAccentThemeUseCase
import ru.stohastika.sklyanka.domain.appearance.SetAccentThemeUseCase

/**
 * Шаги приёмки SKL-120 без экрана — выбор акцентной темы и его сохранение (Р-31, §5.17).
 *
 * Мир подставляет **носитель** выбора (постоянные настройки в памяти), а правила исполняют
 * настоящие сценарии домена: выбор проходит через `SetAccentThemeUseCase`, а чтение —
 * через `ObserveAccentThemeUseCase`. Пересказа правил здесь нет: иначе сценарий проверял бы
 * не продукт, а себя.
 *
 * **Границ доступа здесь нет.** Продукт бесплатный (Р-86, ADR-0016): доступ в сборке есть
 * всегда, поэтому все четыре темы и оба варианта иконки выбираются без оплаты. Сценарий
 * проверяет предметное поведение — выбор применяется сразу и переживает перезапуск, —
 * а не платную границу, которой в поставляемой сборке не существует.
 *
 * **Чего эти шаги не проверяют.** Вёрстку блока и диалогов — её держит стенд `feature-settings`;
 * переключение алиасов, имя в лончере, ярлык и знак уведомления — стенд `app`; краски токенов
 * и контрасты — стенд `core-ui`; роли плиты — стенд `feature-widgets`; светлый документ —
 * стенд `feature-history`.
 */
class Sk120ThemeSteps(private val world: AcceptanceWorld) {

    /** Постоянные настройки оформления сценария: носитель выбора, а не правило (Р-23). */
    private val appearance = InMemoryAppearance()

    private val observeTheme = ObserveAccentThemeUseCase(appearance)
    private val setTheme = SetAccentThemeUseCase(appearance)

    // --- Предыстория ---------------------------------------------------------------

    /**
     * Среда интерфейса следует системной настройке (Р-23).
     *
     * Флаг живёт в состоянии системы сценария: тёмную тему человек выбирает в настройках
     * телефона, мимо приложения, — и стенды читают её оттуда же.
     */
    @Допустим("^в системе включена тёмная тема$")
    fun theSystemIsInTheDarkTheme() {
        world.system.darkTheme = true
    }

    /** Тема, заведённая раньше: сценарий, начинающийся с выбора, не проходит через диалог. */
    @Допустим("^выбрана базовая тема «Олива»$")
    fun theBaseThemeIsChosen() {
        runBlocking { appearance.setAccentTheme(AccentTheme.DEFAULT) }
    }

    /** Тема, заведённая до сценария: записывается в носитель, а не через гейт. */
    @Допустим("^выбрана тема «([^»]+)»$")
    fun thatThemeIsChosen(name: String) {
        runBlocking { appearance.setAccentTheme(themeNamed(name)) }
    }

    // --- Действия -----------------------------------------------------------------

    /** Выбор темы в диалоге: правило исполняет домен, а не шаг (Р-22, §5.17). */
    @Когда("^человек выбирает тему «([^»]+)»$")
    fun thePersonChoosesTheTheme(name: String) {
        runBlocking { setTheme(themeNamed(name)) }
    }

    // --- Проверки: выбор и его сохранение -------------------------------------------

    /** Выбор применяется сразу: приложение несёт акцент темы, а не прежний (§5.17). */
    @Тогда("^приложение несёт акцент «([^»]+)» сразу$")
    fun theApplicationCarriesThatAccentAtOnce(name: String) {
        assertAccent(name)
    }

    @Тогда("^приложение несёт акцент «([^»]+)»$")
    fun theApplicationCarriesThatAccent(name: String) {
        assertAccent(name)
    }

    /** Запись переживает перезапуск: читает её **новый** шов над тем же носителем. */
    @Тогда("^после перезапуска тема остаётся «([^»]+)»$")
    fun theThemeSurvivesARestart(name: String) {
        val afterRestart = ObserveAccentThemeUseCase(appearance)
        assertEquals(themeNamed(name), runBlocking { afterRestart.current() }, "the choice must survive a restart")
    }

    /** Выбор записан — тот, который человек нажал. */
    @Тогда("^выбор записан «([^»]+)»$")
    fun theChoiceIsStoredAs(name: String) {
        assertEquals(themeNamed(name), runBlocking { observeTheme.current() }, "the choice must be stored")
    }

    // --- Носитель выбора -------------------------------------------------------------

    private fun assertAccent(name: String) {
        assertEquals(
            themeNamed(name),
            runBlocking { observeTheme.current() },
            "the application must carry the accent of «$name» (Р-31)",
        )
    }

    /**
     * Имя темы словами сценария.
     *
     * Имена — из каталога строк (§10), и сценарий называет их теми же словами: ключ хранения
     * (`olive`) латинский и человеку не показывается. Падеж не делает имя другим: «Аметиста»
     * и «Аметист» — одна тема (тем же приёмом сравниваются имена профилей в SKL-121).
     */
    private fun themeNamed(name: String): AccentTheme {
        val stem = stem(name)
        val matches = THEMES.filterKeys { stem(it) == stem }.values.toList()
        return requireNotNull(matches.singleOrNull()) {
            "unknown accent theme: $name (known: ${THEMES.keys})"
        }
    }

    /**
     * Основа имени темы: падежные окончания отбрасываются так же, как у имён профилей,
     * и к тому же набору добавлено «м» — творительный падеж («Аметистом») им не покрывался.
     */
    private fun stem(name: String): String = name.trim().trimEnd { it in CASE_ENDINGS }

    /** Постоянные настройки оформления в памяти: подменяется носитель, а не правило (ADR-0007). */
    private class InMemoryAppearance : AppearancePreferences {

        private val theme = MutableStateFlow(AccentTheme.DEFAULT)

        private val variant = MutableStateFlow(IconVariant.DEFAULT)

        override val accentTheme: Flow<AccentTheme> = theme

        override val iconVariant: Flow<IconVariant> = variant

        override suspend fun setAccentTheme(theme: AccentTheme) {
            this.theme.value = theme
        }

        override suspend fun setIconVariant(variant: IconVariant) {
            this.variant.value = variant
        }
    }

    private companion object {

        /** Имена тем §2.5 так, как их называет сценарий. */
        val THEMES: Map<String, AccentTheme> = mapOf(
            "Олива" to AccentTheme.OLIVE,
            "Индиго" to AccentTheme.INDIGO,
            "Хвоя" to AccentTheme.PINE,
            "Аметист" to AccentTheme.AMETHYST,
        )

        /** Падежные окончания имён тем: основа сравнивается без них (как у имён профилей). */
        val CASE_ENDINGS: CharArray = "аяуюыиеоёймАЯУЮЫИЕОЁЙМ".toCharArray()
    }
}
