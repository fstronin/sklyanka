package ru.heuri.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import org.junit.jupiter.api.Assertions.assertEquals
import ru.heuri.sklyanka.acceptance.RussianDates
import ru.heuri.sklyanka.acceptance.steps.localMoment
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import java.time.LocalTime

/**
 * Шаги Предыстории SKL-15: профили сценария и лекарства, которыми они различаются.
 *
 * Объявлены в общем артефакте, а не в модуле экрана, намеренно (SKL-26, TDD §4.5):
 * эти же строки читает и доменный прогон на JVM, и экранный прогон профилей в `app`.
 * Объявить их второй раз в модуле экрана значило бы завести дубль, которого Cucumber
 * не видит, а прогон от него становится пустым и зелёным.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class ProfileContextSteps(private val world: AcceptanceWorld) {

    @Given("^существует свой профиль «(.+?)» с подписью «(.+?)»$")
    fun theOwnProfileExists(name: String, caption: String) {
        // Владелец телефона — первый заведённый в сценарии профиль (правило одно на всю
        // приёмку, см. ScenarioProfiles): строка Предыстории обязана быть первой.
        val profile = world.profiles.get(name)
        assertEquals(true, profile.isSelf, "«$name» must be the phone owner, it is declared first")
        // Подпись своего профиля — текст интерфейса списка профилей, и проверяет её
        // сценарий «Переключение профиля выполняется из настроек». Здесь она лишь
        // обязана быть непустой: пустая подпись сделала бы тот сценарий беспредметным.
        assertEquals(true, caption.isNotBlank(), "the own profile must carry a caption")
    }

    @Given("^существует профиль «(.+?)» с цветом профиля и (\\S+) лекарствами$")
    fun aForeignProfileExists(name: String, count: String) {
        val profile = world.profiles.get(name)
        assertEquals(false, profile.isSelf, "«$name» must not be the phone owner")
        // Число лекарств из Предыстории проверяется там, где оно показано, — в строке
        // списка профилей; здесь оно обязано быть числом, а не словом без смысла.
        assertEquals(true, RussianDates.count(count) > 0, "a foreign profile must own medications")
    }

    @Given("^у профиля «(.+?)» есть лекарство «(.+?)» с приёмом в (\\d{2}:\\d{2}), доза (\\S+) таблетк\\S+$")
    fun theProfileHasAMedicationWithADose(profile: String, medication: String, time: String, dose: String) {
        add(profile, medication, time)
        world.medications.update(medication) { it.copy(doseAmount = RussianDates.amount(dose)) }
    }

    @Given(
        "^у профиля «(.+?)» есть лекарство «(.+?)» с приёмом в (\\d{2}:\\d{2}), " +
            "доза (\\S+) таблетк\\S+, дозировка (\\S+) (\\S+)$",
    )
    @Suppress("LongParameterList")
    fun theProfileHasAMedicationWithADoseAndStrength(
        profile: String,
        medication: String,
        time: String,
        dose: String,
        strength: String,
        unit: String,
    ) {
        add(profile, medication, time)
        world.medications.update(medication) {
            it.copy(
                doseAmount = RussianDates.amount(dose),
                strength = RussianDates.amount(strength),
                strengthUnit = unit,
            )
        }
    }

    @Given("^приём «([^»]+)» профиля «([^»]+)» на (.+?), (\\d{2}:\\d{2}) ожидается$")
    fun theIntakeOfThatProfileIsPending(medication: String, profile: String, date: String, time: String) {
        // Профиль назван в строке не для порядка: сценарий проверяет, что отметка ушла
        // в чужой профиль, и приём обязан принадлежать именно ему (ПРФ-10).
        val owner = world.profiles.get(profile)
        assertEquals(
            owner.id,
            world.medications.get(medication).profileId,
            "«$medication» must belong to «$profile»",
        )
        world.intakes.pending(medication, world.localMoment(date, time))
    }

    private fun add(profile: String, medication: String, time: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            times = listOf(LocalTime.parse(time)),
        )
    }
}
