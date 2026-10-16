package ru.stohastika.sklyanka.acceptance.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.TAG_ACTIVE_PROFILE
import ru.stohastika.sklyanka.feature.settings.TAG_PROFILE_ROW
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_WORD_LABEL
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги SKL-105, говорящие о блоке «Профили» и о слове-подтверждении (группа `@screen`).
 *
 * Шаги живут в `feature-settings`, потому что говорят об экране настроек и об экране удаления
 * профиля (`SKL-26/TDD.md §4.3`). Состояние собирают настоящие `SettingsViewModel`
 * и `DeleteProfileViewModel` на мире сценария ([SettingsScreenProbe],
 * [DeleteProfileScreenProbe]); здесь — только вопросы к собранной композиции.
 *
 * Строка «Добавить профиль» возвращена в блок мастером (SKL-121, ПРФ-9), и здесь её
 * больше нет: SKL-105 держит только то, что снято его решением, — **обещание**
 * «Будет доступно в ближайшем обновлении». Шаг ищет его по тексту экрана, а не по метке
 * строки: обещания нереализованной функции в продукте нет ни в одном виде (П-7).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class Sk105SettingsSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // --- Блок «Профили»: список и переключение ------------------------------------------------

    /**
     * Обещания «Будет доступно в ближайшем обновлении» на экране нет.
     *
     * Обещание нереализованной функции — та же ложь, что лже-отчёт (П-7): строка «Добавить
     * профиль» снята вместе с обещанием, и в ресурсах его тоже нет.
     */
    @Но("^обещания «Будет доступно в ближайшем обновлении» на экране нет$")
    fun thereIsNoUpcomingUpdatePromise() {
        SettingsScreenProbe.ask(world) {
            assertEquals(
                emptyList<String>(),
                screenTexts().filter { it.contains(UPDATE_PROMISE, ignoreCase = true) },
                "обещания «$UPDATE_PROMISE» на экране нет (П-7, Р-29)",
            )
        }
    }

    // --- Блок «Профили»: список и переключение ------------------------------------------------

    /** Профили перечисляются по-прежнему: первой — строка владельца, и на ней отметка активного. */
    @Тогда("^первой идёт строка владельца с отметкой активного профиля$")
    fun theOwnerRowComesFirstWithTheActiveMark() {
        SettingsScreenProbe.ask(world) {
            val rows = onAllNodesWithTag(TAG_PROFILE_ROW, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(rows.isNotEmpty(), "блок «Профили» обязан перечислять хотя бы владельца")
            val owner = rows.minBy { it.boundsInRoot.top }
            assertTrue(
                textsOf(owner).contains(string(R.string.settings_profile_self)),
                "первой идёт строка владельца «${string(R.string.settings_profile_self)}»: ${textsOf(owner)}",
            )
            val marks = onAllNodesWithTag(TAG_ACTIVE_PROFILE, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(1, marks.size, "активным отмечен ровно один профиль")
            val mark = marks.single().boundsInRoot
            assertTrue(
                mark.top >= owner.boundsInRoot.top && mark.bottom <= owner.boundsInRoot.bottom,
                "отметка активного профиля стоит на строке владельца",
            )
        }
    }

    /**
     * Переключение профиля из блока доступно по-прежнему.
     *
     * Снятие строки «Добавить профиль» не сняло переключение: каждая строка профиля нажимается
     * и несёт действие смены профиля (ПРФ-19) — то есть из блока им по-прежнему пользуются.
     */
    @И("^переключение профиля по-прежнему доступно из этого блока$")
    fun theProfileSwitchingIsStillAvailable() {
        SettingsScreenProbe.ask(world) {
            // Действие нажатия живёт в объединённом дереве: `clickable` сливает строку с её
            // содержимым, и там же — действие смены профиля.
            val rows = onAllNodesWithTag(TAG_PROFILE_ROW).fetchSemanticsNodes()
            assertTrue(rows.isNotEmpty(), "блок «Профили» обязан перечислять хотя бы владельца")
            rows.forEachIndexed { index, row ->
                assertTrue(
                    row.config.contains(SemanticsActions.OnClick),
                    "строка профиля №${index + 1} обязана остаться нажимаемой — переключение живёт здесь",
                )
            }
        }
    }

    // --- Слово-подтверждение удаления профиля --------------------------------------------------

    /** Открытие экрана подтверждения удаления: состояние строит настоящий `DeleteProfileViewModel`. */
    @Допустим("^человек открыл «Удаление профиля»$")
    fun thePersonOpenedTheProfileDeletion() {
        DeleteProfileScreenProbe.open(world)
    }

    /**
     * Подтверждение просит ввести слово, названное ресурсом экрана удаления профиля.
     *
     * Слово сверяется с ресурсом `profile_delete_word`, а не с литералом: подпись-образец
     * собирается тем же словом, и литерал в шаге разошёлся бы с продуктом молча.
     */
    @Тогда("^подтверждение просит ввести слово «(.+)»$")
    fun theConfirmationAsksForTheWord(word: String) {
        val stored = string(R.string.profile_delete_word)
        assertEquals(word, stored, "сценарий и ресурс экрана называют одно слово подтверждения")
        val label = string(UiR.string.confirm_word_label, stored)
        assertTrue(
            DeleteProfileScreenProbe.hasText(DeleteProfileScreenProbe.state(world), label),
            "подтверждение обязано просить ввести слово: ожидалось «$label»",
        )
    }

    /**
     * Подпись-образец называет то же слово, что сверяется.
     *
     * Подпись собирается из `confirm_word_label` (общий компонент `core-ui`) с тем же словом
     * `profile_delete_word`. Что это **то же** слово, доказывает поле: набранное слово из подписи
     * включает подтверждение, — второе правило сравнения не заводится (§5.7).
     */
    @И("^подпись-образец называет это же слово$")
    fun theSampleLabelNamesTheSameWord() {
        val word = string(R.string.profile_delete_word)
        val expected = string(UiR.string.confirm_word_label, word)
        DeleteProfileScreenProbe.ask(world) {
            val labels = onAllNodesWithTag(TAG_CONFIRM_WORD_LABEL, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(1, labels.size, "подпись-образец на экране одна")
            assertTrue(
                textsOf(labels.single()).contains(expected),
                "подпись-образец обязана назвать слово «$word»: ожидалось «$expected»",
            )
        }
        DeleteProfileScreenProbe.typeWord(world, word)
        DeleteProfileScreenProbe.assertConfirmAvailability(world, enabled = true)
    }

    // --- Обвязка ------------------------------------------------------------------------------

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** Тексты всего экрана настроек: вопрос задаётся собранной композиции, а не снимку состояния. */
    private fun ComposeUiTest.screenTexts(): List<String> =
        onAllNodesWithTag(TAG_SETTINGS_SCREEN, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { textsOf(it) }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    private companion object {
        /** Прежнее обещание нереализованной строки, снятое вместе с ней (П-7). Литерал намеренный. */
        const val UPDATE_PROMISE = "Будет доступно в ближайшем обновлении"
    }
}
