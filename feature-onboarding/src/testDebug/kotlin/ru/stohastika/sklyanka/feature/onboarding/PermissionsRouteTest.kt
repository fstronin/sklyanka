package ru.stohastika.sklyanka.feature.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.onboarding.PermissionsStand
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource

/**
 * Системная «Назад» на маршруте экрана разрешений (ОВ-86, SKL-40).
 *
 * Идёт через тот же стенд, что и приёмка: маршрут собирается целиком, а нажатие проходит
 * через настоящий `BackHandler` продукта — поддельный владелец диспетчера в стенде только
 * доставляет системное событие. Проверяется наблюдаемое: хозяин экрана получает исход
 * «уйти», и это тот же исход, что у кнопки «Пропустить, настрою позже».
 *
 * Повторный вход проверяется здесь же: он обязан не наследовать исход прошлого ухода —
 * иначе `StateFlow` не эмитит равное значение, и экран либо закрывается сам, либо
 * не закрывается по «Назад» (правка `OnboardingViewModel.start()`, `TDD.md §5.2`).
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона, и нижние кнопки иначе не собираются (SKL-24).
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class PermissionsRouteTest {

    private val world = AcceptanceWorld()

    @After
    fun restoreMainDispatcher() {
        PermissionsStand.dropMainDispatcher()
    }

    @Test
    fun theSystemBackClosesTheScreenWithTheSkipExit() {
        PermissionsStand.open(world, OnboardingSource.FIRST_LAUNCH)

        PermissionsStand.pressSystemBack(world)

        assertEquals(OnboardingExit.TODAY, PermissionsStand.exitedTo)
        assertEquals(OnboardingExit.TODAY, PermissionsStand.model(world).exit.value)
    }

    @Test
    fun theSkipButtonClosesTheScreenWithTheVerySameExit() {
        PermissionsStand.open(world, OnboardingSource.FIRST_LAUNCH)

        PermissionsStand.pressExitButton(world, TAG_SKIP)

        assertEquals(OnboardingExit.TODAY, PermissionsStand.exitedTo)
    }

    @Test
    fun theScreenStaysOpenUntilTheSystemBackIsPressed() {
        PermissionsStand.open(world, OnboardingSource.FIRST_LAUNCH)

        PermissionsStand.showRoute(world)

        assertNull(PermissionsStand.exitedTo)
        assertNull(PermissionsStand.model(world).exit.value)
    }

    @Test
    fun reEnteringAfterAnExitKeepsTheScreenOpenAndClosesItByBackAgain() {
        PermissionsStand.open(world, OnboardingSource.FIRST_LAUNCH)
        PermissionsStand.pressSystemBack(world)
        assertEquals(OnboardingExit.TODAY, PermissionsStand.exitedTo)

        PermissionsStand.open(world, OnboardingSource.TODAY_WARNING)

        assertNull(
            "starting the screen must clear the exit of the previous leave (SKL-40)",
            PermissionsStand.model(world).exit.value,
        )
        PermissionsStand.showRoute(world)
        assertNull("the re-entered screen must not leave by itself", PermissionsStand.exitedTo)
        PermissionsStand.pressSystemBack(world)
        assertEquals(OnboardingExit.TODAY, PermissionsStand.exitedTo)
    }
}
