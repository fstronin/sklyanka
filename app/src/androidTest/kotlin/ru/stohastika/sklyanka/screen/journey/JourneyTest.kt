package ru.stohastika.sklyanka.screen.journey

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OpLogRecord
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.feature.meds.card.TAG_MEDICATION_CARD_SCREEN
import ru.stohastika.sklyanka.feature.meds.form.TAG_SCHEDULE_PICKER
import ru.stohastika.sklyanka.feature.meds.form.TAG_SCHEDULE_ROW
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.screen.DeviceScreenTest
import ru.stohastika.sklyanka.screen.support.DeviceActivities
import ru.stohastika.sklyanka.screen.support.DeviceJourneyStorage
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_BAR
import ru.stohastika.sklyanka.ui.component.TAG_LIST_PICKER_OPTION
import java.time.Instant
import java.time.LocalDate
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * База походов (SKL-140, TDD §2, §9).
 *
 * Поход идёт на настоящем файле базы: состояния экрана мало, поход утверждает состояние
 * человека — запись, остаток, след в журнале, — и оно обязано жить там же, где живёт
 * у человека. Правила каркаса экранных проверок остаются: ручной запуск `MainActivity`
 * без `ActivityScenario`, кадр на сценарий, управляемые часы, никаких `Thread.sleep`.
 *
 * Здесь же лежат общие шаги человека, которых нет у экранной проверки: системная «Назад»,
 * поворот экрана, повторный вход в приложение, открытие карточки лекарства по имени.
 */
abstract class JourneyTest : DeviceScreenTest() {

    /**
     * Уборка перед походом: требуем файловое хранилище и стираем файл базы **до** сборки
     * графа. Поход не наследует данные предыдущего — кроме фазы выживания, которая
     * переопределяет уборку и утверждает именно сохранность состояния.
     */
    override fun resetBeforeScenario() {
        DeviceJourneyStorage.requireFile()
        DeviceJourneyStorage.wipe(targetContext)
        super.resetBeforeScenario()
    }

    /** Строка интерфейса продукта: поход называет человеку то же, что видит человек. */
    protected fun ui(@StringRes id: Int): String = targetContext.getString(id)

    /** Вернуться на «Сегодня» нижней панелью: путь человека, а не системный возврат. */
    protected fun openToday() {
        compose.onNode(
            hasContentDescription(ui(UiR.string.tab_today)) and hasAnyAncestor(hasTestTag(TAG_BOTTOM_BAR)),
            useUnmergedTree = true,
        ).performClick()
        waitForTag(TAG_TODAY_SCREEN)
    }

    /**
     * Прокрутить список до названного лекарства: нижние карточки ленивого списка
     * не компонуются, пока к ним не прокрутили.
     */
    protected fun scrollListTo(name: String) {
        waitUntil(SCREEN_TIMEOUT_MS, "строки «$name» в списке") {
            if (textExists(name)) {
                true
            } else {
                scrollListOnce(name) && textExists(name)
            }
        }
    }

    /** Одна попытка прокрутки ленивого списка: до нижних карточек он их не компонует. */
    private fun scrollListOnce(name: String): Boolean = runCatching {
        compose.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag(TAG_MEDICATION_LIST)),
            useUnmergedTree = true,
        ).performScrollToNode(hasText(name))
    }.isSuccess

    /** Открыть карточку названного лекарства из списка: строк может быть несколько. */
    protected fun openMedicationCard(name: String) {
        scrollListTo(name)
        compose.onNode(
            hasTestTag(TAG_MEDICATION_CARD) and hasAnyDescendant(hasText(name)),
            useUnmergedTree = true,
        ).performClick()
        waitForTag(TAG_MEDICATION_CARD_SCREEN)
    }

    /**
     * Выбрать вид расписания строкой — путь человека: строка вида → список пяти видов →
     * название вида (SKL-117, §5.10).
     *
     * Список — диалог выбора значения (`ListPickerDialog`), и вариант адресуется той же
     * подписью, которую читает человек: строка списка сливает свою подпись, поэтому
     * ищется она слитым деревом.
     */
    protected fun chooseScheduleKind(@StringRes labelRes: Int) {
        scrollToAndClick(TAG_SCHEDULE_ROW)
        waitForTag(TAG_SCHEDULE_PICKER)
        compose.onNode(hasTestTag(TAG_LIST_PICKER_OPTION) and hasText(ui(labelRes))).performClick()
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия списка видов расписания") { !exists(TAG_SCHEDULE_PICKER) }
    }

    /**
     * Что говорит строка вида расписания: подписи строки слиты — так её читает человек
     * и программа чтения, а не разметка строки.
     */
    protected fun scheduleRowText(): String = compose.onNodeWithTag(TAG_SCHEDULE_ROW)
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.Text)
        .orEmpty()
        .joinToString(" ") { it.text }

    /**
     * Двойное нажатие, как его делает человек: две точки подряд, без простоя между ними.
     *
     * Ожидание простоя между нажатиями мерило бы не двойное нажатие, а скорость закрытия
     * экрана, и отказ говорил бы не о том, что сделал человек. По той же причине нажатие идёт
     * **одним** обращением к узлу: два `performClick` подряд — это два обращения, и между ними
     * экран успевает смениться, если первое нажатие уже сделало своё дело (SKL-119, прогон
     * 16.10.2026: «Заменить всё» успела показать отчёт, и второе обращение не нашло кнопки —
     * проверка падала на скорости стенда, а не на продукте).
     *
     * Годится там, где кнопка переживает первое нажатие: занятая («Сохранить лекарство»,
     * «Заменить всё»). Действие, которое первым нажатием снимается с экрана, так нажимать
     * нельзя: второе нажатие пришлось бы в то, что встало на его место, — у таких действий
     * есть свой приём рядом с проверкой (`HostileDeleteSheetTest.clickTwice`).
     */
    protected fun doublePressByTag(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput {
            click()
            click()
        }
        compose.waitForIdle()
    }

    /**
     * Системная «Назад» — тем же путём, каким она приходит человеку: через диспетчер
     * возврата активного экрана. Обработчики Compose (`BackHandler`) срабатывают на нём же.
     */
    protected fun pressBack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val shown = requireNotNull(DeviceActivities.resumedActivity()) { "на экране нет активности" }
        val activity = shown as? ComponentActivity
            ?: error("экран ${shown.javaClass.simpleName} не умеет системный возврат")
        instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /**
     * Поворот экрана в середине работы: настоящая смена конфигурации, а не подмена
     * композиции. Поход утверждает, что введённое и открытое состояние её переживают.
     */
    protected fun rotateTo(orientation: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        waitUntil(LAUNCH_TIMEOUT_MS, "активности перед поворотом") { DeviceActivities.resumedActivity() != null }
        val activity = requireNotNull(DeviceActivities.resumedActivity()) { "на экране нет активности" }
        instrumentation.runOnMainSync { activity.requestedOrientation = orientation }
        waitUntil(LAUNCH_TIMEOUT_MS, "поворота экрана") {
            targetContext.resources.configuration.orientation == orientation
        }
    }

    /** Альбомный поворот: конфигурация меняется на глазах у человека. */
    protected fun rotateLandscape() = rotateTo(Configuration.ORIENTATION_LANDSCAPE)

    /**
     * Альбомный поворот, который может не состояться на стенде.
     *
     * Образ эмулятора (и особенно его окно без дисплея) не всегда меняет ориентацию: тогда
     * проверка поворота не имеет предмета, и это состояние стенда, а не продукта. Возвращает,
     * состоялся ли поворот, чтобы класс мог назвать пробел, а не покраснеть из-за образа.
     */
    protected fun rotateLandscapeOrNull(): Boolean {
        val before = targetContext.resources.configuration.orientation
        // Поворот делает сам стенд: читать список активностей и менять ориентацию можно
        // только с главного потока, и обе операции — один выход туда (SKL-149).
        if (!DeviceActivities.rotateResumedToLandscape()) return false
        val changed = runCatching {
            compose.waitUntil(ROTATION_TIMEOUT_MS) {
                targetContext.resources.configuration.orientation != before
            }
        }.isSuccess
        return changed
    }

    /** Книжный поворот: экран возвращается в обычное положение. */
    protected fun rotatePortrait() = rotateTo(Configuration.ORIENTATION_PORTRAIT)

    /**
     * Закрыть приложение и войти заново: у человека это «ушёл на домашний экран и вернулся».
     *
     * Процесс при этом жив — настоящую смерть процесса проверяет эстафета выживания
     * (`SurvivalPhaseOneJourneyTest` / `SurvivalPhaseTwoJourneyTest`).
     */
    protected fun relaunchApp() {
        DeviceActivities.closeAll(compose)
        launchApp()
    }

    /** Записи журнала операций: следствия действий похода, без названий лекарств и доз. */
    protected fun opLog(): List<OpLogRecord> = DeviceTestSeed.opLogRecent(targetContext)

    /** Есть ли в журнале операций событие: отметка, отмена, снятие, архивация. */
    protected fun opLogHas(event: OpEvent): Boolean = opLog().any { it.event == event }

    /** Число записей журнала операций указанного события: повторное действие видно числом. */
    protected fun opLogCount(event: OpEvent): Int = opLog().count { it.event == event }

    /** Снимок будильников: что пересборка ставила на ближайшее окно. */
    protected fun alarmMirror(): Map<String, Instant> = DeviceTestSeed.alarmMirror(targetContext)

    /** Прочитан ли признак «экран разрешений показан» — из хранилища, а не из памяти процесса. */
    protected fun onboardingIsSeen(): Boolean = DeviceTestSeed.onboardingSeenFlag(targetContext)

    /** Приёмы профиля владельца за день: состояние после действия похода. */
    protected fun dayIntakes(date: LocalDate = DeviceTestClock.today): List<Intake> =
        DeviceTestSeed.dayIntakes(targetContext, ownerProfile.id, date)

    /** Поворот стенда: конфигурация меняется не мгновенно, а следом за системой. */
    private companion object {
        const val ROTATION_TIMEOUT_MS = 10_000L
    }
}
