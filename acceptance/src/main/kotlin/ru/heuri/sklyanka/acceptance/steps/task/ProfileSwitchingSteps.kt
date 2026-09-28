package ru.heuri.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.acceptance.steps.localMoment
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.profile.SwitchReason
import java.time.LocalTime

/**
 * Шаги SKL-15: переключение и хранение активного профиля (`ПРФ-17`…`ПРФ-19`).
 *
 * Доменная часть группы `@switching` живёт в общем артефакте, а не в модуле экрана
 * (`SKL-26/TDD.md §8.1`): «после перезапуска активный профиль не меняется молча» —
 * вопрос состояния приложения, и экран для него не нужен.
 *
 * Переключение идёт настоящим [ru.heuri.sklyanka.domain.profile.SwitchProfileUseCase],
 * а перезапуск — пересборкой поставщика контекста над тем же записанным значением
 * ([ru.heuri.sklyanka.acceptance.world.ScenarioProfileSession]). Копии правила
 * «куда переключаться можно» в приёмке нет.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class ProfileSwitchingSteps(private val world: AcceptanceWorld) {

    /**
     * Действие, о котором говорят и доменный, и экранный прогоны.
     *
     * Объявлено в артефакте, а не в шагах `app`, потому что строка одна и та же
     * в сценарии разделения профилей (домен) и в сценарии индикации (экран): два
     * объявления одного выражения встретились бы в экранном прогоне и погасили бы
     * его целиком.
     */
    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек переключается (?:обратно )?на профиль «([^»]+)»$")
    fun thePersonSwitchesToThatProfile(date: String, time: String, name: String) {
        world.clock.moveTo(world.localMoment(date, time))
        world.profiles.use(name)
    }

    // --- Хранение активного профиля --------------------------------------------

    @Допустим("^(.+?) в (\\d{2}:\\d{2}) человек переключился на профиль «([^»]+)»$")
    fun thePersonHasSwitchedTo(date: String, time: String, profile: String) {
        world.clock.moveTo(world.localMoment(date, time))
        world.profileSession.switchTo(profile, SwitchReason.USER_SETTINGS)
    }

    /**
     * Закрытие приложения.
     *
     * Состояния у закрытия никакого нет и быть не должно: записанное значение живёт
     * в хранилище, а не в процессе. Шаг называет момент, в который человек закрыл
     * приложение, — этого достаточно, чтобы следующий шаг открыл его позже.
     */
    @Когда("^он закрывает приложение в (\\d{2}:\\d{2})$")
    fun heClosesTheApplication(time: String) {
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
    }

    @Когда("^открывает его снова (.+?) в (\\d{2}:\\d{2})$")
    fun heOpensItAgain(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        world.profileSession.restart()
    }

    @Тогда("^активным профилем остаётся «([^»]+)»$")
    fun theActiveProfileRemains(profile: String) {
        val context = world.profileSession.context()
        assertEquals(
            profile,
            context.name,
            "a restart must not silently return to the own profile: the stored value is the active one (ПРФ-18)",
        )
        assertFalse(context.isSelf, "«$profile» is a foreign profile and must stay foreign after a restart")
    }

    /**
     * Признаки чужого профиля — свойство контекста, а не разметки.
     *
     * Экран в JVM-прогоне не собирается (`ADR-0007`), и проверяется единственный
     * источник, из которого берутся все шесть признаков: контекст активного профиля
     * (`TDD.md §5.1`). Что сами признаки видны на собранном экране, проверяет экранный
     * прогон `app` — группа `@indication`. Здесь важно, что после перезапуска контекст
     * остался чужим, а не «вернулся к себе».
     */
    @Тогда("^на экране сразу видны все признаки чужого профиля$")
    fun theSignsOfAForeignProfileAreVisibleAtOnce() {
        assertTrue(
            world.profileSession.context().showsForeignIndication,
            "after a restart the context must still demand the signs of a foreign profile (ПРФ-18)",
        )
    }
}
