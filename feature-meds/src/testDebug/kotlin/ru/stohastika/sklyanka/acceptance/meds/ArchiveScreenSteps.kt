package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.ArchiveCaption
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.TAG_SEGMENTED_CONTROL
import ru.stohastika.sklyanka.feature.meds.archive.ArchiveIntent
import ru.stohastika.sklyanka.feature.meds.archive.ArchiveNotice
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_CAPTION
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_CARD
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_EMPTY
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_NOTICE
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_RESTORE
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_SCREEN
import ru.stohastika.sklyanka.feature.meds.archive.TAG_GROUP_FINISHED
import ru.stohastika.sklyanka.feature.meds.archive.TAG_GROUP_MANUAL
import ru.stohastika.sklyanka.feature.meds.archive.TAG_HISTORY_BANNER
import ru.stohastika.sklyanka.feature.meds.list.MedsIntent
import ru.stohastika.sklyanka.feature.meds.list.MedsTab
import ru.stohastika.sklyanka.ui.component.TAG_SCREEN_HEADER
import ru.stohastika.sklyanka.feature.meds.card.TAG_RESTORE as TAG_CARD_RESTORE

/**
 * Экранные шаги приёмки архива курсов (SKL-11, `@screen`; заведены SKL-26).
 *
 * Состояние архива собирает [ArchiveStand] тем же путём, что `ArchiveViewModel`,
 * а «Вернуть» применяется настоящим `RestoreMedicationUseCase`. Переходы к активным
 * и к карточке лекарства проверяются контрактом намерений: маршрут живёт в `app`,
 * а экраны-приёмники — в этом же модуле и собираются здесь.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class ArchiveScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Намерения последнего действия: по ним проверяются переходы наружу. */
    private var lastIntents: List<Any> = emptyList()

    // --- Открытие архива и вкладки ---------------------------------------------------

    @Допустим("^человек на экране «Лекарства» на вкладке активных$")
    fun thePersonIsOnTheActiveTab() {
        MedsListStand.ask(world) { }
    }

    @Когда("^(?:он|человек) нажимает «Архив · (\\d+)»$")
    fun thePersonPressesTheArchiveTab(count: Int) {
        val label = context.getString(R.string.meds_tab_archive, count)
        val intents = MedsListStand.act(world) { test -> test.onNodeWithText(label).performClick() }
        lastIntents = intents
        ArchiveStand.ask(world) { }
    }

    @Тогда("^открывается архив$")
    fun theArchiveOpens() {
        assertTrue(
            "the archive tab must ask to switch: ${lastIntents.filterIsInstance<MedsIntent>()}",
            MedsIntent.SelectTab(MedsTab.ARCHIVE) in lastIntents,
        )
        ArchiveStand.ask(world) { test -> test.onNodeWithTag(TAG_ARCHIVE_SCREEN).assertIsDisplayed() }
    }

    @Когда("^человек открывает архив$")
    fun thePersonOpensTheArchive() {
        ArchiveStand.ask(world) { test -> test.onNodeWithTag(TAG_ARCHIVE_SCREEN).assertIsDisplayed() }
    }

    @Допустим("^человек в архиве$")
    fun thePersonIsInTheArchive() {
        ArchiveStand.ask(world) { test -> test.onNodeWithTag(TAG_ARCHIVE_SCREEN).assertIsDisplayed() }
    }

    @Тогда("^заголовок экрана остаётся «(.+)»$")
    fun theHeaderStays(title: String) {
        ArchiveStand.ask(world) { test ->
            assertTrue(title in MedsProbe.texts(test, TAG_SCREEN_HEADER))
        }
    }

    // --- Состав архива ----------------------------------------------------------------

    @Тогда("^показаны группы с заголовками «(.+)» и «(.+)»$")
    fun theGroupsAreShown(first: String, second: String) {
        ArchiveStand.ask(world) { test ->
            val groups = listOf(
                MedsProbe.texts(test, TAG_GROUP_FINISHED),
                MedsProbe.texts(test, TAG_GROUP_MANUAL),
            )
            assertTrue(
                "the finished group must read «$first»: $groups",
                groups[0].any { it.equals(first, ignoreCase = true) },
            )
            assertTrue(
                "the manual group must read «$second»: $groups",
                groups[1].any { it.equals(second, ignoreCase = true) },
            )
        }
    }

    @Тогда("^«([^»]+)» и «([^»]+)» показаны в группе «(.+)»$")
    fun thoseMedicationsAreShownInThatGroup(first: String, second: String, group: String) {
        listOf(first, second).forEach { medication ->
            assertCardInGroup(medication, tagOfGroup(group))
        }
    }

    @Тогда("^итога приверженности в этих карточках нет$")
    fun thoseCardsCarryNoOutcome() {
        // Подпись — следствие причины (ЭКР-8): у убранного вручную итога не бывает.
        val content = requireNotNull(ArchiveStand.state(world).content) { "the archive must not be empty" }
        assertTrue(
            "a manually archived card must not carry an outcome: ${content.manual.map { it.caption }}",
            content.manual.all { it.caption is ArchiveCaption.ArchivedOn },
        )
        ArchiveStand.ask(world) { test ->
            content.manual.forEach { card ->
                val texts = MedsProbe.textsOf(
                    test.onNode(
                        hasTestTag(TAG_ARCHIVE_CARD) and hasAnyDescendant(hasText(card.title)),
                        useUnmergedTree = true,
                    ).fetchSemanticsNode(),
                )
                assertTrue("no outcome in the manual card ${card.title}: $texts", texts.none { OUTCOME_MARKER in it })
            }
        }
    }

    @Тогда("^внизу показана плашка «(.+)»$")
    fun theHistoryBannerIsShown(text: String) {
        ArchiveStand.ask(world) { test ->
            val texts = MedsProbe.texts(test, TAG_HISTORY_BANNER)
            assertTrue("the banner must read «$text»: $texts", text in texts)
        }
    }

    @Тогда("^у карточки каждого архивированного лекарства есть кнопка «(.+)»$")
    fun everyArchivedCardHasThatButton(button: String) {
        val archived = world.medications.all().count {
            it.isArchived && it.profileId == world.profiles.get(world.profiles.active).id
        }
        ArchiveStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_ARCHIVE_CARD, useUnmergedTree = true).assertCountEquals(archived)
            test.onAllNodesWithTag(TAG_ARCHIVE_RESTORE, useUnmergedTree = true).assertCountEquals(archived)
            assertTrue(
                "every restore button must read «$button»",
                MedsProbe.texts(test, TAG_ARCHIVE_RESTORE).all { it == button },
            )
        }
    }

    @Тогда("^заголовка «(.+)» на экране нет$")
    fun thereIsNoSuchGroupHeading(label: String) {
        ArchiveStand.ask(world) { test ->
            test.onAllNodesWithText(label, ignoreCase = true).assertCountEquals(0)
        }
    }

    @Тогда("^показана только группа «(.+)»$")
    fun onlyThatGroupIsShown(label: String) {
        ArchiveStand.ask(world) { test ->
            test.onNodeWithTag(tagOfGroup(label)).assertExists()
            val other = if (tagOfGroup(label) == TAG_GROUP_FINISHED) TAG_GROUP_MANUAL else TAG_GROUP_FINISHED
            test.onAllNodesWithTag(other, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // --- Пустой архив и счётчики на списке ----------------------------------------------

    @Тогда("^сегмент «Архив · (\\d+)» доступен для нажатия$")
    fun theArchiveSegmentIsTappable(count: Int) {
        val label = context.getString(R.string.meds_tab_archive, count)
        MedsListStand.ask(world) { test ->
            test.onNode(hasText(label) and hasAnyAncestor(hasTestTag(TAG_SEGMENTED_CONTROL)), useUnmergedTree = true)
                .assertExists()
            test.onNodeWithText(label).assertIsEnabled()
        }
    }

    @Тогда("^вместо списка показано пояснение, что архив пуст$")
    fun theEmptyArchiveIsExplained() {
        ArchiveStand.ask(world) { test ->
            test.onNodeWithTag(TAG_ARCHIVE_EMPTY).assertExists()
            val texts = MedsProbe.texts(test, TAG_ARCHIVE_EMPTY)
            assertTrue(
                "the empty title must be shown: $texts",
                context.getString(R.string.archive_empty_title) in texts,
            )
            assertTrue("the empty text must be shown: $texts", context.getString(R.string.archive_empty_text) in texts)
        }
    }

    @Тогда("^сегмент показывает «Архив · (\\d+)»$")
    fun theSegmentShowsThatArchiveCount(count: Int) {
        val label = context.getString(R.string.meds_tab_archive, count)
        MedsListStand.ask(world) { test -> test.onNodeWithText(label).assertIsDisplayed() }
    }

    @Тогда("^сегмент показывает «Активные · (\\d+)» и «Архив · (\\d+)»$")
    fun theSegmentShowsBothCounts(active: Int, archived: Int) {
        val labels = listOf(
            context.getString(R.string.meds_tab_active, active),
            context.getString(R.string.meds_tab_archive, archived),
        )
        MedsListStand.ask(world) { test ->
            val shown = MedsProbe.texts(test, TAG_SEGMENTED_CONTROL)
            labels.forEach { label -> assertTrue("the segment must read «$label»: $shown", label in shown) }
        }
    }

    @Тогда("^в списке активных его нет$")
    fun itIsNotInTheActiveList() {
        val medication = world.medications.last
        val id = world.medications.get(medication).id
        MedsListStand.ask(world) { }
        assertTrue(
            "the archived medication must leave the active list (ПР-11)",
            MedsListStand.state(world).items.none { it.medicationId == id },
        )
    }

    // --- Профили -------------------------------------------------------------------------

    @Тогда("^«([^»]+)» в архиве нет$")
    fun thatMedicationIsAbsentFromTheArchive(medication: String) {
        val id = world.medications.get(world.medicationNamed(medication)).id
        val content = ArchiveStand.state(world).content
        assertTrue(
            "«$medication» must not be in the archive (ПР-10)",
            content == null || (content.finished + content.manual).none { it.medicationId == id },
        )
        ArchiveStand.ask(world) { test ->
            test.onAllNodesWithText(MedsListStand.displayName(world, medication)).assertCountEquals(0)
        }
    }

    @Тогда("^счётчик «Архив» его не учитывает$")
    fun theArchiveCounterIgnoresIt() {
        val profileId = world.profiles.get(world.profiles.active).id
        val own = world.medications.all().count { it.isArchived && it.profileId == profileId }
        assertEquals(
            "the archive counter must count the active profile only (ПР-10)",
            own,
            ArchiveStand.state(world).archiveCount,
        )
    }

    // --- Переходы -------------------------------------------------------------------------

    @Когда("^человек нажимает на карточку «([^»]+)» в архиве$")
    fun thePersonPressesThatArchiveCard(medication: String) {
        val id = world.medications.get(world.medicationNamed(medication)).id
        val intents = ArchiveStand.act(world) { test ->
            test.onNode(archiveCard(medication), useUnmergedTree = true).performClick()
        }
        lastIntents = intents
        assertTrue("the card must ask to open the medication: $intents", ArchiveIntent.OpenMedication(id) in intents)
    }

    @Тогда("^открывается карточка лекарства «([^»]+)» в архивном виде$")
    fun theMedicationCardOpensInArchivedForm(medication: String) {
        val id = world.medications.get(world.medicationNamed(medication)).id
        assertTrue(
            "the archive card must open the medication «$medication»: ${lastIntents.filterIsInstance<ArchiveIntent>()}",
            ArchiveIntent.OpenMedication(id) in lastIntents,
        )
        val state = MedCardStand.state(world, medication)
        assertTrue("the card must be shown in its archived form (SKL-10)", state.isArchived)
        MedCardStand.ask(world, medication) { test -> test.onNodeWithTag(TAG_CARD_RESTORE).assertIsDisplayed() }
    }

    @Когда("^он нажимает «Активные · (\\d+)»$")
    fun thePersonPressesTheActiveTab(count: Int) {
        val label = context.getString(R.string.meds_tab_active, count)
        val intents = ArchiveStand.act(world) { test -> test.onNodeWithText(label).performClick() }
        lastIntents = intents
        MedsListStand.ask(world) { }
    }

    @Тогда("^открывается список активных лекарств$")
    fun theActiveListOpens() {
        assertTrue(
            "the active tab must ask to switch: ${lastIntents.filterIsInstance<ArchiveIntent>()}",
            ArchiveIntent.SelectActive in lastIntents,
        )
        MedsListStand.ask(world) { test ->
            assertTrue(context.getString(R.string.meds_title) in MedsProbe.texts(test, TAG_SCREEN_HEADER))
        }
    }

    // --- Возврат из архива ------------------------------------------------------------------

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «Вернуть» в карточке «([^»]+)»$")
    fun thePersonRestoresFromThatCard(date: String, time: String, medication: String) {
        val id = world.medications.get(world.medicationNamed(medication)).id
        val intents = ArchiveStand.act(world) { test ->
            test.onNode(restoreButton(medication), useUnmergedTree = true).performClick()
        }
        lastIntents = intents
        assertTrue("the card must ask to restore the course: $intents", ArchiveIntent.Restore(id) in intents)
    }

    @Тогда("^«([^»]+)» появляется в списке активных лекарств$")
    fun thatMedicationAppearsInTheActiveList(medication: String) {
        val id = world.medications.get(world.medicationNamed(medication)).id
        assertTrue(
            "«$medication» must return to the active list (ПР-11)",
            MedsListStand.state(world).items.any { it.medicationId == id },
        )
    }

    @Тогда("^его карточки в архиве больше нет$")
    fun itsArchiveCardIsGone() {
        val medication = world.medications.last
        val id = world.medications.get(medication).id
        val content = ArchiveStand.state(world).content
        assertTrue(
            "the restored course must leave the archive",
            content == null || (content.finished + content.manual).none { it.medicationId == id },
        )
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «Вернуть»$")
    fun thePersonRestoresTheFocusedCourse(date: String, time: String) {
        val medication = world.medications.last
        val id = world.medications.get(medication).id
        val intents = ArchiveStand.act(world) { test ->
            test.onNode(restoreButton(medication), useUnmergedTree = true).performClick()
        }
        lastIntents = intents
        assertTrue("the card must ask to restore the course: $intents", ArchiveIntent.Restore(id) in intents)
    }

    @Тогда(
        "^человек получает объяснение, что период приёма нужно продлить, или ему предлагается править период приёма до возврата$",
    )
    fun thePersonIsToldToExtendThePeriod() {
        assertEquals(
            "the refusal must be explained, not swallowed (ОВ-14)",
            ArchiveNotice.PeriodNeedsUpdate,
            ArchiveStand.notice,
        )
        ArchiveStand.ask(world) { test ->
            val texts = MedsProbe.texts(test, TAG_ARCHIVE_NOTICE)
            assertTrue(
                "the notice must be shown: $texts",
                context.getString(R.string.card_notice_period_needs_update) in texts,
            )
        }
    }

    @Тогда("^молчаливого возврата курса, не порождающего ни одного приёма, не происходит$")
    fun noSilentRestoreHappens() {
        val medication = world.medications.last
        assertTrue("the course must stay archived", world.medications.get(medication).isArchived)
        assertTrue(
            "a refused restore must not generate intakes: ${world.intakes.of(medication)}",
            world.intakes.of(medication).none { it.isPending },
        )
    }

    // --- Доступность ----------------------------------------------------------------------

    @Тогда("^у каждой группы есть текстовый заголовок$")
    fun everyGroupHasATextHeading() {
        ArchiveStand.ask(world) { test ->
            assertTrue(
                "the finished group needs a heading",
                MedsProbe.texts(test, TAG_GROUP_FINISHED).any {
                    it.isNotBlank()
                },
            )
            assertTrue(
                "the manual group needs a heading",
                MedsProbe.texts(test, TAG_GROUP_MANUAL).any {
                    it.isNotBlank()
                },
            )
        }
    }

    @Тогда("^подпись внутри карточки называет либо итог курса, либо дату архивации$")
    fun everyCaptionNamesEitherOutcomeOrDate() {
        val captions = ArchiveStand.ask(world) { test -> MedsProbe.texts(test, TAG_ARCHIVE_CAPTION) }
        assertTrue("the archive must show captions", captions.isNotEmpty())
        val datePrefix = context.getString(R.string.archive_caption_since, "").trim()
        captions.forEach { caption ->
            assertTrue(
                "the caption must name the outcome or the archive date: «$caption»",
                OUTCOME_MARKER in caption || caption.startsWith(datePrefix),
            )
        }
    }

    @Тогда("^различить разделы можно без восприятия цвета$")
    fun theGroupsAreDistinguishableWithoutColour() {
        // Различие разделов несут слова: заголовки групп и подписи карточек (НФТ-33).
        val captions = ArchiveStand.ask(world) { test -> MedsProbe.texts(test, TAG_ARCHIVE_CAPTION) }
        val datePrefix = context.getString(R.string.archive_caption_since, "").trim()
        assertTrue(
            "the outcome captions must differ from the date ones: $captions",
            captions.any { OUTCOME_MARKER in it } && captions.any { it.startsWith(datePrefix) },
        )
    }

    // --- Обвязка ---------------------------------------------------------------------------

    private fun archiveCard(medication: String) = hasTestTag(TAG_ARCHIVE_CARD) and
        hasAnyDescendant(hasText(MedsListStand.displayName(world, medication)))

    private fun restoreButton(medication: String) = hasText(context.getString(R.string.archive_restore)) and
        hasAnyAncestor(archiveCard(medication))

    private fun tagOfGroup(label: String): String = when (label) {
        context.getString(R.string.archive_group_finished) -> TAG_GROUP_FINISHED
        context.getString(R.string.archive_group_manual) -> TAG_GROUP_MANUAL
        else -> error("unknown archive group: $label")
    }

    /** Карточка стоит под заголовком своей группы — по расположению, как у лент «Сегодня». */
    private fun assertCardInGroup(medication: String, groupTag: String) {
        ArchiveStand.ask(world) { test ->
            val header = test.onNodeWithTag(groupTag, useUnmergedTree = true).fetchSemanticsNode()
            val card = test.onNode(archiveCard(medication), useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(
                "the card of «$medication» must stand under $groupTag",
                card.boundsInRoot.top >= header.boundsInRoot.top,
            )
            val headers = test.onAllNodes(
                hasTestTag(TAG_GROUP_FINISHED) or hasTestTag(TAG_GROUP_MANUAL),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            val next = headers.map { it.boundsInRoot.top }.filter { it > header.boundsInRoot.top }.minOrNull()
            if (next != null) {
                assertTrue("the card of «$medication» must stand above the next group", card.boundsInRoot.top < next)
            }
        }
    }

    private companion object {
        const val OUTCOME_MARKER = "принято"
    }
}
