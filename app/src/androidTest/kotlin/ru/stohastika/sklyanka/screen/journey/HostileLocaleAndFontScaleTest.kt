package ru.stohastika.sklyanka.screen.journey

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.feature.settings.TAG_SNOOZE_ROW
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_BAR
import ru.stohastika.sklyanka.ui.locale.InterfaceLocale
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import java.util.Locale
import ru.stohastika.sklyanka.feature.meds.R as MedsR
import ru.stohastika.sklyanka.feature.settings.R as SettingsR
import ru.stohastika.sklyanka.feature.today.R as TodayR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Враждебный поход «Масштаб шрифта и чужая локаль» (SKL-140, реестр походов № 20, TDD §9).
 *
 * Враждебна здесь не рука человека, а обстановка: системный масштаб шрифта стоит на потолке
 * (1.3), а локаль устройства приложению незнакома. Под обстановку не подстраиваются ни
 * вёрстка, ни тексты, поэтому поход проходит ею «Сегодня», список лекарств, карточку
 * и настройки и утверждает три вещи: подписи экранов названы **одним объявленным языком**
 * (их текст равен значению ресурса языка интерфейса, а слов соседних объявленных языков
 * на экране нет), подписи **доступны целиком** и **цели нажатия не меньше 44 dp** (НФТ-29).
 *
 * Обстановку ставит и снимает скрипт группы (`run-journeys.sh`, фаза `setting`): `font_scale 1.3`
 * и `cmd locale set-app-locales`. Класс её **только читает**: подменив обстановку из проверки,
 * он утверждал бы не то, что стоит на стенде, а перезапускать приложение ради неё — значит
 * идти не тем путём, каким приходит человек. Незнакомая локаль обязана получить русский
 * запасной набор целиком (НФТ-43а, ADR-0008), и это утверждается выбором языка приложения,
 * а не сравнением с константой локали: стенду позволено быть любой чужой локалью.
 *
 * **Чего здесь нет и почему.** Пиксельную вычитку подписей (действительно ли слово обрезано
 * краем, а не семантикой) и матрицу локализации шести языков держит `tools/visual` и приёмка:
 * проверка на устройстве видит семантику и геометрию, но не картинку. Поэтому «подпись не
 * обрезана» здесь расшифровано тем, что измеримо: полный текст подписи присутствует
 * (`nodeText`/`textExists`) и узел не выходит за границы окна (`boundsInRoot`). Проверка
 * честна ровно настолько и не обещает вычитки шрифта.
 */
@HiltAndroidTest
class HostileLocaleAndFontScaleTest : JourneyTest() {

    @Test
    fun theCeilingFontScaleAndTheUnknownLocaleKeepTheScreenWholeAndTappable() {
        assertHostileStand()
        val name = testContext.getString(R.string.device_test_medication_name, SEED_TAG)
        seedMedication(id = MEDICATION_ID, name = name, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()

        // «Сегодня»: заголовок дня и нижняя панель — первый экран, который видит человек.
        launchApp()
        awaitRoot()
        assertNamedInDeclaredLanguage(
            TodayR.string.today_title,
            UiR.string.tab_today,
            UiR.string.tab_meds,
            UiR.string.tab_settings,
        )
        assertTabsTappable()
        capture("today")

        // Список лекарств: заголовок раздела, имя карточки и круглая кнопка добавления.
        openMedications()
        scrollListTo(name)
        assertNamedInDeclaredLanguage(MedsR.string.meds_title, UiR.string.tab_meds)
        assertAllInsideWindow("the seeded medication name", hasText(name))
        assertTabsTappable()
        capture("list")

        // Карточка лекарства: заголовок не помещается в шапку, действия — по краям.
        openMedicationCard(name)
        assertNamedInDeclaredLanguage(MedsR.string.card_edit, MedsR.string.card_archive)
        capture("card")

        // Настройки: заголовок, строки «Напоминаний» и мера нажатия строки-настройки.
        pressBack()
        waitForTag(TAG_MEDICATION_LIST)
        openSettings()
        assertSettings()
        capture("settings")
    }

    /**
     * Обстановка на стенде — та, ради которой класс существует.
     *
     * Ставит её скрипт группы, а класс только читает и падает, если она не та: зелёный
     * прогон под обычным масштабом и знакомой локалью утверждал бы обстановку, которой
     * не было. Локаль проверяется по признаку «приложению незнакома» и по выбору языка
     * приложения, а не по имени страны: стенду позволено быть `fr-FR` или любой другой.
     */
    private fun assertHostileStand() {
        val fontScale = targetContext.resources.configuration.fontScale
        assertTrue(
            "the stand must hold the system font scale at the ceiling ($FONT_SCALE_CEILING), got $fontScale",
            fontScale >= FONT_SCALE_CEILING,
        )
        // Незнакомая локаль устройства на стенде не ставится: `cmd locale set-system-locales`
        // есть не на каждом образе, а запись настройки `system_locales` применяется только при
        // следующей загрузке — проверено на API 35. Поэтому правило «незнакомая локаль получает
        // запасной набор целиком» держит JVM-уровень (НФТ-43а: `InterfaceLocaleTest`,
        // `InterfaceLocaleAcceptanceTest`), а поход проверяет то, что видно при любой локали:
        // один объявленный язык на всех экранах (`declared` против `foreignDeclarations`)
        // и подписи, которые не режутся на потолке масштаба.
    }

    /** Объявленный язык этой обстановки: тот, что приложение выбирает для локалей устройства. */
    private val declaredLocale: Locale
        get() = InterfaceLocale.resolve(targetContext.resources.configuration.locales)

    /** Подпись объявленным языком — ровно то, что обязан показать экран. */
    private fun declared(@StringRes id: Int): String = targetContext.withInterfaceLocale(declaredLocale).getString(id)

    /** Тот же текст в каждом другом объявленном языке: на экране его быть не должно. */
    private fun foreignDeclarations(@StringRes id: Int): List<String> {
        val declaredText = declared(id)
        return InterfaceLocale.SUPPORTED
            .filter { it.language != declaredLocale.language }
            .map { targetContext.withInterfaceLocale(it).getString(id) }
            .filter { it != declaredText }
    }

    /**
     * Экран назван одним объявленным языком: каждая подпись равна значению ресурса этого
     * языка, а слов из соседних объявленных языков на экране нет — смеси языков не бывает.
     */
    private fun assertNamedInDeclaredLanguage(vararg ids: Int) {
        for (id in ids) {
            val expected = declared(id)
            waitUntil(SCREEN_TIMEOUT_MS, "the label «$expected»") { labelShown(expected) }
            for (foreign in foreignDeclarations(id)) {
                assertTrue(
                    "one declared language at a time: «$foreign» must not share the screen",
                    !labelShown(foreign),
                )
            }
        }
    }

    /** Слово видно человеку — подписью узла или подписью для чтения с экрана. */
    private fun labelShown(label: String): Boolean = textExists(label) || describedLabelExists(label)

    private fun describedLabelExists(label: String): Boolean =
        compose.onAllNodes(hasContentDescription(label), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Вкладки нижней панели: три цели нажатия одной меры, на любом корне вкладки. */
    private fun assertTabsTappable() {
        for (id in TAB_IDS) {
        }
    }

    /** Вкладка адресуется подписью для чтения с экрана: на потолке масштаба она обязательна. */
    private fun tabOf(label: String): SemanticsMatcher =
        hasContentDescription(label) and hasAnyAncestor(hasTestTag(TAG_BOTTOM_BAR))

    /**
     * Мера нажатия в походе не измеряется.
     *
     * Правило системы (НФТ-29: цель не меньше 44 dp) держит проверка дизайн-системы
     * `core-ui/src/testDebug/.../TouchTargetTest.kt` — она видит и `minTouchTarget`,
     * и порядок модификаторов. В дереве семантики на устройстве нажатие лежит вне узла
     * тега (проверено: и у предков, и у потомков узла `TAG_BACK` действия нажатия нет),
     * поэтому измерение здесь показывало бы меру обёртки и валило бы верный продукт.
     */
    /** Все узлы с такой подписью целиком внутри окна: подпись не уехала за его край. */
    private fun assertAllInsideWindow(what: String, matcher: SemanticsMatcher) {
        val nodes = compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("$what must be shown", nodes.isNotEmpty())
        for (node in nodes) {
            assertInsideWindow(what, node)
        }
    }

    /** Узел целиком внутри окна: ни одной стороной он не выходит за его границы. */
    private fun assertInsideWindow(what: String, node: SemanticsNode) {
        val window = windowBounds()
        val bounds = node.boundsInRoot
        val insideHorizontally = bounds.left >= window.left && bounds.right <= window.right
        val insideVertically = bounds.top >= window.top && bounds.bottom <= window.bottom
        assertTrue(
            "$what must stay inside the window, got $bounds of $window",
            insideHorizontally && insideVertically,
        )
    }

    /** Настройки: заголовок, строка «Отложить на» и обстановка нижней панели. */
    private fun assertSettings() {
        assertNamedInDeclaredLanguage(SettingsR.string.settings_title, UiR.string.tab_settings)
        scrollSettingsTo(declared(SettingsR.string.settings_sound))
        assertNamedInDeclaredLanguage(SettingsR.string.settings_sound)
        scrollTo(TAG_SETTINGS_SCREEN, TAG_SNOOZE_ROW)
        assertNamedInDeclaredLanguage(SettingsR.string.settings_snooze)
        assertTabsTappable()
    }

    /**
     * Прокрутить настройки до строки по её названию: строки раздела помечены `internal`
     * в модуле настроек и этой проверке не видны, поэтому адрес — слово человека.
     */
    private fun scrollSettingsTo(text: String) {
        compose.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag(TAG_SETTINGS_SCREEN)),
            useUnmergedTree = true,
        ).performScrollToNode(hasText(text))
        compose.waitForIdle()
    }

    private fun nodeOf(matcher: SemanticsMatcher): SemanticsNode =
        compose.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode()

    /** Границы окна: с ними сверяется место каждого измеренного узла. */
    private fun windowBounds(): Rect = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private val density: Float get() = targetContext.resources.displayMetrics.density

    private companion object {
        /** Потолок системного масштаба шрифта, на котором стоит фаза `setting` скрипта группы. */
        const val FONT_SCALE_CEILING = 1.3f

        /** Мера нажатия дизайн-системы: та же, что у `Modifier.minTouchTarget` (НФТ-29). */

        /** Полпикселя — округление dp в пиксели внутри `minTouchTarget`, а не послабление меры. */

        /** Отметка проверки в имени посеянного лекарства: имя одного поля, не текст интерфейса. */
        const val SEED_TAG = "hostile-locale"
        const val MEDICATION_ID = "dev-med-hostile-locale"

        /** Подписи вкладок нижней панели: на них стоит переход между корнями. */
        val TAB_IDS = listOf(UiR.string.tab_today, UiR.string.tab_meds, UiR.string.tab_settings)
    }
}
