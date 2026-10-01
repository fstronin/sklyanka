package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.ShouldShowOnboardingUseCase
import ru.stohastika.sklyanka.feature.onboarding.OnboardingExit
import ru.stohastika.sklyanka.feature.onboarding.TAG_NEXT
import ru.stohastika.sklyanka.feature.onboarding.TAG_SKIP

/**
 * Экранные шаги приёмки системной «Назад» (SKL-40, группа `@screen`).
 *
 * Шаги живут рядом с экраном разрешений, потому что говорят о его маршруте
 * (`SKL-26/TDD.md §4.3`): «Назад» идёт через настоящий `BackHandler` в `PermissionsRoute`
 * ([PermissionsStand.pressSystemBack]), а кнопки — тем же маршрутом, и это позволяет
 * сверять «ведёт туда же, что кнопка» на одном и том же исходе.
 *
 * Возврат «в «Настройки»» и «на «Сегодня»» решается хозяином экрана, а не экраном: стенд
 * проверяет исход, который экран отдаёт хозяину (`OnboardingExit.TODAY` — тот же, что
 * у «Пропустить, настрою позже»). Адреса возврата решает хозяин навигации, и этой задачей
 * он не тронут: из настроек `PermissionsActivity` закрывает экран по любому исходу,
 * из баннера возврат на «Сегодня» задаёт `MainActivity`; сам `Root` приёмкой не покрыт —
 * унаследованный предел SKL-18/SKL-31.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class PermissionsBackSteps(private val world: AcceptanceWorld) {

    // --- Точки входа ---------------------------------------------------------------------------

    @Допустим("^человек открывает экран «Настроить надёжность» из предупреждения на «Сегодня»$")
    fun thePersonOpensPermissionsFromTheWarning() {
        PermissionsStand.open(world, OnboardingSource.TODAY_WARNING)
    }

    @Допустим("^человек открывает экран «Настроить надёжность» из настроек$")
    fun thePersonOpensPermissionsFromSettings() {
        PermissionsStand.open(world, OnboardingSource.SETTINGS)
    }

    // --- Выходы --------------------------------------------------------------------------------

    @Когда("^(?:человек )?нажимает системную кнопку «Назад»$")
    fun thePersonPressesSystemBack() {
        PermissionsStand.pressSystemBack(world)
    }

    @Когда("^человек нажимает «Пропустить, настрою позже»$")
    fun thePersonPressesSkip() {
        PermissionsStand.pressExitButton(world, TAG_SKIP)
    }

    @Когда("^человек нажимает «Дальше»$")
    fun thePersonPressesNext() {
        PermissionsStand.pressExitButton(world, TAG_NEXT)
    }

    // --- Проверки ------------------------------------------------------------------------------

    @Тогда("^экран «Настроить надёжность» закрывается$")
    fun thePermissionsScreenCloses() {
        assertNotNull(
            PermissionsStand.exitedTo,
            "«Назад» must leave the screen the same way the visible exit does (ОВ-86)",
        )
    }

    @И("^человек попадает на «Сегодня» с предупреждением о ненадёжности напоминаний$")
    fun thePersonLandsOnTodayWithTheWarning() {
        assertEquals(
            OnboardingExit.TODAY,
            PermissionsStand.exitedTo,
            "the exit must be the one Today is opened by, not the watch setup",
        )
        assertTrue(warningStays(), "the chain is unfinished, so Today shows the warning")
    }

    @И("^человек возвращается на «Сегодня»$")
    fun thePersonReturnsToToday() {
        assertEquals(OnboardingExit.TODAY, PermissionsStand.exitedTo, "the banner entry returns to Today")
    }

    @И("^предупреждение о ненадёжности напоминаний остаётся$")
    fun theWarningStays() {
        assertTrue(warningStays(), "nothing was fixed, so the warning must stay (ОВ-86)")
    }

    @И("^человек возвращается в «Настройки»$")
    fun thePersonReturnsToSettings() {
        // Адрес возврата — уже решённое поведение хозяина: `PermissionsActivity` закрывает
        // экран по любому исходу (`onExit = finish`), и человек оказывается в настройках.
        // Стенд проверяет то, что решает сам экран: «Назад» не завела себе второго исхода
        // и ведёт тем же путём, что видимые выходы.
        assertEquals(OnboardingExit.TODAY, PermissionsStand.exitedTo, "the settings entry closes the screen")
    }

    @И("^при следующем запуске приложение само онбординг не открывает$")
    fun theOnboardingDoesNotOpenItself() {
        assertFalse(
            runBlocking { ShouldShowOnboardingUseCase(world.onboarding)() },
            "back at first launch marks the screen seen, as «Пропустить» does (НАП-21)",
        )
    }

    @И("^онбординг отмечен просмотренным$")
    fun theOnboardingIsMarkedSeen() {
        assertTrue(runBlocking { world.onboarding.seen() }, "the visible exit must be remembered (НАП-21)")
    }

    /**
     * Предупреждение остаётся: цепочка по-прежнему не пройдена.
     *
     * Сама строка живёт на «Сегодня» (SKL-29) и читает те же факты системы: стенд проверяет
     * доменное состояние, а отрисовку баннера «Сегодня» проверяет прогон feature-today.
     */
    private fun warningStays(): Boolean {
        val checklist = requireNotNull(PermissionsStand.state(world).checklist) { "the screen must read the system" }
        return checklist.remindersUnreliable
    }
}
