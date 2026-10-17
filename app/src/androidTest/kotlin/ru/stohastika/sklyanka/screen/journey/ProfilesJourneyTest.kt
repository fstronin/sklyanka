package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_CONFIRM
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_DONE
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_HISTORY
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_MEDICATIONS
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_RESULT_TITLE
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_SCREEN
import ru.stohastika.sklyanka.feature.settings.profile.TAG_PROFILE_BLOCK
import ru.stohastika.sklyanka.feature.settings.profile.TAG_PROFILE_COLOURS
import ru.stohastika.sklyanka.feature.settings.profile.TAG_PROFILE_FORM
import ru.stohastika.sklyanka.feature.settings.profile.TAG_PROFILE_MIRROR
import ru.stohastika.sklyanka.feature.settings.profile.TAG_PROFILE_NAME_FIELD
import ru.stohastika.sklyanka.feature.settings.profile.TAG_PROFILE_SUBMIT
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestGraph
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_WORD_FIELD
import ru.stohastika.sklyanka.ui.component.TAG_PROFILE_LABEL
import ru.stohastika.sklyanka.ui.component.TAG_PROFILE_STRIPE
import ru.stohastika.sklyanka.ui.token.ProfileColor
import java.math.BigDecimal
import java.util.Locale
import ru.stohastika.sklyanka.feature.settings.R as SettingsR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Поход «Чужой профиль» (SKL-140, реестр походов № 8).
 *
 * Человек уходит в профиль того, за кем присматривает, — и обязан на каждом экране видеть,
 * что он не в своём: цветная полоса, надпись «Профиль · Имя» и несворачиваемый баннер
 * избыточны намеренно (ПРФ-11…ПРФ-13). Данные при этом не смешиваются: приёмы одного
 * профиля не видны в другом, и это свойство хранилища, а не экрана (ПРФ-4).
 *
 * Поход идёт до конца: человек возвращается в свой профиль, а затем удаляет чужой —
 * словом-подтверждением (§5.7) — и профиль исчезает вместе с лекарствами и историей.
 * Утверждается это на данных (репозиторий, приёмы профиля, журнал операций), а не на виде
 * экрана: экран мог закрыться, а строки в базе остаться.
 *
 * Мастер добавления профиля появился в SKL-121, но чужой профиль поход всё равно сеет
 * репозиторием: подопечному нужны свои лекарство и история, а мастер их не заводит.
 * Владельца сеет тестовый граф, чужого — репозиторий профилей (см. [JourneyProfileGraph]).
 *
 * Вместе с SKL-121 поход знает и другие пути человека по профилям: правку заведённого
 * (имя у любого, цвет и дублирование — у чужого, ПРФ-26…ПРФ-28) и платную границу на
 * создании при двух заведённых профилях (ПРФ-33, Р-84).
 */
@HiltAndroidTest
class ProfilesJourneyTest : JourneyTest() {

    @Test
    fun thePersonVisitsAForeignProfileAndRemovesIt() {
        seedMedication(
            id = OWNER_MEDICATION_ID,
            name = ownerMedication,
            times = listOf(DeviceTestClock.scheduledTime),
        )
        val ward = seedWardProfile(wardName)
        seedMedicationFor(ward.id, WARD_MEDICATION_ID, wardMedication)
        planIntakes()
        DeviceTestSeed.planIntakes(targetContext, ward.id)

        launchApp()
        awaitRoot()

        // В своём профиле признаков чужого нет вовсе: их отсутствие — тоже сигнал (ПРФ-16).
        openSettings()
        assertTrue("the owner profile must show no foreign stripe", !exists(TAG_PROFILE_STRIPE))
        assertTrue("the owner profile must show no foreign label", !exists(TAG_PROFILE_LABEL))
        capture("settings-own")

        // Переключение — из настроек: строка чужого профиля названа его именем.
        switchToProfile(wardName)
        waitUntil(SCREEN_TIMEOUT_MS, "признаков чужого профиля") { foreignIndicationsShown() }
        capture("settings-ward")

        openToday()
        waitUntil(SCREEN_TIMEOUT_MS, "лекарства «$wardMedication» на «Сегодня»") { textExists(wardMedication) }
        assertTrue("the owner medication must stay invisible in the foreign profile", !textExists(ownerMedication))
        assertTrue("the foreign profile must be indicated on the day", foreignIndicationsShown())
        capture("today-ward")

        // Возврат в свой профиль: подсказки снимаются, чужое лекарство пропадает.
        openSettings()
        switchToProfile(ownerRow)
        waitUntil(SCREEN_TIMEOUT_MS, "снятия признаков чужого профиля") { !foreignIndicationsShown() }
        assertTrue("the foreign stripe must be gone in the owner profile", !exists(TAG_PROFILE_STRIPE))
        capture("settings-own-again")

        openToday()
        waitUntil(SCREEN_TIMEOUT_MS, "лекарства «$ownerMedication» на «Сегодня»") { textExists(ownerMedication) }
        assertTrue("the foreign medication must stay invisible in the owner profile", !textExists(wardMedication))

        // Удалить можно ровно того, в чьём контексте человек находится (ОВ-7): поход
        // заходит в чужой профиль снова — так делает и человек, вернувшийся к своим приёмам.
        openSettings()
        switchToProfile(wardName)
        clickRow(deleteRow)
        waitForTag(TAG_DELETE_SCREEN)
        // Числа потерь приходят потоком: сначала дожидаемся их, потом судим. Чужое
        // лекарство одно, а история непуста — это утверждение о том, что будет стёрто.
        val lostMedications = targetContext.getString(SettingsR.string.profile_delete_medications, 1, 0)
        waitUntil(SCREEN_TIMEOUT_MS, "числа лекарств на экране удаления") {
            exists(TAG_DELETE_MEDICATIONS) && nodeText(TAG_DELETE_MEDICATIONS) == lostMedications
        }
        assertEquals(
            "the delete screen must name the single medication that will be lost",
            lostMedications,
            nodeText(TAG_DELETE_MEDICATIONS),
        )
        assertTrue(
            "the delete screen must count a non-empty history",
            nodeText(TAG_DELETE_HISTORY) != targetContext.getString(SettingsR.string.profile_delete_history, 0),
        )
        capture("delete-confirm")

        // Слово набирается с подписи-образца: до него подтверждающая кнопка недоступна (§5.7).
        typeInto(TAG_CONFIRM_WORD_FIELD, targetContext.getString(SettingsR.string.profile_delete_word))
        scrollToAndClick(TAG_DELETE_CONFIRM)
        waitForTag(TAG_DELETE_RESULT_TITLE)
        capture("delete-result")
        click(TAG_DELETE_DONE)
        waitForTag(TAG_SETTINGS_SCREEN)

        // Профиль исчез вместе с лекарством и историей: последнее — утверждение о базе.
        // Сначала дожидаемся, что список профилей собран: пустой список тоже «не содержит»
        // чужого профиля, и без этой опоры утверждение было бы зелёным при любом поведении.
        waitUntil(SCREEN_TIMEOUT_MS, "списка профилей после удаления") { textExists(ownerRow) }
        assertTrue("the foreign profile must be gone from settings", !textExists(wardName))
        assertTrue("no foreign profile must remain to switch to", !textExists(deleteRow))
        assertTrue("the foreign history must be erased with the profile", wardIntakes().isEmpty())
        assertNull(
            "the foreign medication must be erased with the profile",
            runBlocking { testGraph().medications().get(WARD_MEDICATION_ID) },
        )
        assertTrue("the owner intakes must survive the deletion", dayIntakes().isNotEmpty())
        assertTrue("the switch must be in the operation log", opLogHas(OpEvent.PROFILE_SWITCHED))
        assertTrue("the deletion must be in the operation log", opLogHas(OpEvent.PROFILE_DELETED))

        // Активным стал свой профиль: на «Сегодня» снова своё лекарство, чужого нет.
        openToday()
        waitUntil(SCREEN_TIMEOUT_MS, "лекарства «$ownerMedication» после удаления") { textExists(ownerMedication) }
        assertTrue("the deleted profile must leave no medication on the day", !textExists(wardMedication))
        capture("today-after")

        // Удаление переживает выход из приложения: чужого профиля нет и после перезапуска.
        relaunchApp()
        awaitRoot()
        openSettings()
        waitUntil(SCREEN_TIMEOUT_MS, "списка профилей после перезапуска") { textExists(ownerRow) }
        assertTrue("the deleted profile must not come back after a relaunch", !textExists(wardName))
    }

    /**
     * Поход «Правка заведённого профиля» (SKL-121, ПРФ-26…ПРФ-28, П-7).
     *
     * Никакой предел не закрывает настройку заведённого: имя правится у любого профиля,
     * включая владельца (Р-86). Поход идёт от строки настроек — тихое «Изменить»
     * в строке профиля, — и правит профиль,
     * который **не** активен: чтобы исправить имя, в профиль не переключаются (§5.14).
     *
     * Активный контекст проверяется на данных, а не по виду экрана: отметка активного
     * живёт в строке списка, а правда о нём — в постоянных настройках устройства (ПРФ-18).
     *
     * У владельца в форме нет ни выбора цвета, ни вопроса о дублировании (ПРФ-26): цвет
     * владельца — основной акцент приложения и как индикация не показывается (ПРФ-16),
     * поэтому и правки цвета у него быть не может.
     */
    @Test
    fun thePersonRenamesAProfileAndTheOwnerFormHasNeitherColourNorMirror() {
        val ward = seedWardProfile(wardName)
        launchApp()
        awaitRoot()
        openSettings()
        waitUntil(SCREEN_TIMEOUT_MS, "строки «$wardName» в настройках") { textExists(wardName) }

        // Правка чужого имени: вход — подпись «Изменить» в его строке (тег строки внутренний).
        clickProfileEdit(wardName)
        waitForTag(TAG_PROFILE_FORM)
        assertTrue("the foreign profile must keep its colour block", exists(TAG_PROFILE_COLOURS))
        replaceText(TAG_PROFILE_NAME_FIELD, renamedWard)
        click(TAG_PROFILE_SUBMIT)
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия формы правки чужого профиля") { !exists(TAG_PROFILE_FORM) }
        waitForTag(TAG_SETTINGS_SCREEN)
        waitUntil(SCREEN_TIMEOUT_MS, "нового имени «$renamedWard» в списке профилей") {
            textExists(renamedWard)
        }

        assertTrue("the old name must leave the profile list", !textExists(wardName))
        assertEquals(
            "renaming must keep the profile itself",
            renamedWard,
            runBlocking { profileGraph().profiles().get(ward.id) }?.name,
        )
        assertTrue("the rename must be in the operation log", opLogHas(OpEvent.PROFILE_RENAMED))
        assertEquals(
            "editing a profile must not change the active one",
            ownerProfile.id,
            activeProfileId(),
        )
        capture("profiles-ward-renamed")

        // Владелец: имя правится, а цвета и дублирования у него нет вовсе (ПРФ-26).
        // Судим по приросту, а не по наличию записи: у чужого профиля выше могла остаться
        // запись о цвете (если его цвет меняли), и «записей нет» само по себе говорило бы
        // не о владельце. Дублирование при переименовании не пишется ни у кого: флаг
        // трогает только смена самого флага, а не сохранение формы.
        val colourChanges = opLogCount(OpEvent.PROFILE_COLOUR_CHANGED)
        val mirrorChanges = opLogCount(OpEvent.PROFILE_MIRROR_CHANGED)
        clickProfileEdit(ownerRow)
        waitForTag(TAG_PROFILE_FORM)
        assertTrue("the owner form must show no colour block", !exists(TAG_PROFILE_COLOURS))
        assertTrue("the owner form must show no duplication question", !exists(TAG_PROFILE_MIRROR))
        capture("profiles-owner-edit")
        replaceText(TAG_PROFILE_NAME_FIELD, renamedOwner)
        click(TAG_PROFILE_SUBMIT)
        // Форма уходит только после успешной записи: до этого читать имя из базы нечего.
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия формы правки владельца") { !exists(TAG_PROFILE_FORM) }
        waitForTag(TAG_SETTINGS_SCREEN)

        val owner = runBlocking { profileGraph().profiles().getSelf() }
        assertEquals("the owner must be renamed too (ПРФ-27)", renamedOwner, owner?.name)
        assertEquals(
            "the owner colour must stay the app accent (ПРФ-16)",
            ownerProfile.color,
            owner?.color,
        )
        assertEquals(
            "the owner form must not write a colour change",
            colourChanges,
            opLogCount(OpEvent.PROFILE_COLOUR_CHANGED),
        )
        assertEquals(
            "the owner form must not write a duplication change",
            mirrorChanges,
            opLogCount(OpEvent.PROFILE_MIRROR_CHANGED),
        )
        capture("profiles-owner-renamed")
    }

    /**
     * Поход «Третий профиль» (SKL-121, SKL-153, ПРФ-32, Р-86).
     *
     * Профили с третьего по пятый доступны всем: продукт бесплатный, доступа не покупают,
     * и гейт создания видит `included = true` (ADR-0016 §3). Поэтому мастер завершает
     * создание сразу — без платной причины над кнопкой, без витрины и без подложенного
     * ответа магазина. Предел пяти остаётся продуктовым ограничением (ПРФ-32), но здесь
     * до него далеко: заводятся три профиля, и третий — обычный чужой профиль.
     *
     * Новый профиль активным не становится (ПРФ-18): активный профиль меняет только
     * осознанное переключение (ПРФ-19).
     */
    @Test
    fun thePersonAddsAThirdProfileForFree() {
        // Чужой профиль — оливой: тогда первый свободный цвет в мастере — терракота,
        // и выбор бирюзы видно по значению в базе, а не только по нажатию (§5.13).
        seedWardProfile(wardName, ProfileColor.Olive.argb)
        launchApp()
        awaitRoot()
        openSettings()
        waitUntil(SCREEN_TIMEOUT_MS, "строки «$wardName» в настройках") { textExists(wardName) }

        clickSettingsRow(addProfileRow)
        waitForTag(TAG_PROFILE_FORM)
        assertTrue("the wizard must ask about duplication", exists(TAG_PROFILE_MIRROR))
        typeInto(TAG_PROFILE_NAME_FIELD, thirdName)
        // Бесплатная сборка не знает платной границы: завершение доступно сразу (Р-86).
        assertFalse("the free wizard must show no paid boundary", exists(TAG_PROFILE_BLOCK))
        compose.onNodeWithTag(TAG_PROFILE_SUBMIT, useUnmergedTree = true).assertIsEnabled()
        capture("profiles-form-free")

        // Цвет — образцом из закрытого набора (ПРФ-15).
        scrollToAndClick(colourTeal)
        click(TAG_PROFILE_SUBMIT)
        waitForTag(TAG_SETTINGS_SCREEN)
        waitUntil(SCREEN_TIMEOUT_MS, "нового профиля «$thirdName» в списке") { textExists(thirdName) }

        val created = runBlocking { profileGraph().profiles().observeAll().first() }
            .single { it.name == thirdName }
        assertTrue("the third profile must be one to look after", !created.isSelf)
        assertEquals("the chosen colour must be saved", ProfileColor.Teal.argb, created.color)
        assertTrue("duplication must stay on by default (Р-29)", created.mirrorReminders)
        assertEquals("three profiles must be registered", 3, profileCount())
        assertEquals(
            "a new profile must not become active",
            ownerProfile.id,
            activeProfileId(),
        )
        assertTrue("the creation must be in the operation log", opLogHas(OpEvent.PROFILE_CREATED))
        capture("profiles-settings-created")
    }

    /** Лекарство профиля: тот же доменный черновик, что у формы, — инварианты те же (ИНВ-4). */
    private fun seedMedicationFor(profileId: String, id: String, name: String) {
        val draft = MedicationDraft.new(profileId = profileId, today = DeviceTestClock.today.minusDays(1)).copy(
            name = name,
            doseAmount = BigDecimal.ONE,
            times = listOf(DeviceTestClock.scheduledTime),
        )
        DeviceTestSeed.medication(targetContext, draft.toMedication(id))
    }

    /**
     * Чужой профиль репозиторием: цвет — из закрытого набора палитры (ПРФ-15).
     *
     * Цвет по умолчанию — терракота (первый цвет набора, он же цвет из спецификации):
     * походу, которому важен другой цвет, он передаётся явно.
     */
    private fun seedWardProfile(name: String, color: Int = ProfileColor.Terracotta.argb): Profile {
        val ward = Profile(
            id = WARD_PROFILE_ID,
            name = name,
            color = color,
            isSelf = false,
            mirrorReminders = true,
        )
        runBlocking { profileGraph().profiles().save(ward) }
        return ward
    }

    /** Переключение профиля строкой настроек: строки чужого профиля адресуются именем (ПРФ-26). */
    private fun switchToProfile(rowText: String) = clickRow(rowText)

    /**
     * Три признака чужого профиля, которые видит человек: полоса, надпись и баннер
     * (ПРФ-11). Шестой признак — подпись «отметили вы» — принадлежит списку приёмов.
     */
    private fun foreignIndicationsShown(): Boolean =
        exists(TAG_PROFILE_STRIPE) && exists(TAG_PROFILE_LABEL) && textExists(bannerText)

    /** Строка списка адресуется текстом: её собственный тег внутренний для `feature-settings`. */
    private fun clickRow(text: String) {
        waitUntil(SCREEN_TIMEOUT_MS, "строки «$text»") { textExists(text) }
        compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /**
     * Правка профиля из его строки: тихое «Изменить» адресуется подписью рядом с именем
     * профиля — тег строки внутренний для `feature-settings` и app-проверке не виден,
     * а имя и подпись видит человек (тем же приёмом адресуются строки настроек в походе витрины).
     */
    private fun clickProfileEdit(profileName: String) {
        waitUntil(SCREEN_TIMEOUT_MS, "строки «$profileName» в настройках") { textExists(profileName) }
        scrollSettingsTo(profileName)
        val edit = targetContext.getString(SettingsR.string.profile_row_edit)
        waitUntil(SCREEN_TIMEOUT_MS, "строки «$profileName» с подписью «$edit»") {
            compose.onAllNodes(hasText(edit) and hasAnyAncestor(hasText(profileName)))
                .fetchSemanticsNodes().size == 1
        }
        compose.onNode(hasText(edit) and hasAnyAncestor(hasText(profileName))).performClick()
        compose.waitForIdle()
    }

    /** Строка настроек по её названию: тег строки внутренний для `feature-settings`. */
    private fun clickSettingsRow(title: String) {
        waitUntil(SCREEN_TIMEOUT_MS, "строки «$title» в настройках") { textExists(title) }
        scrollSettingsTo(title)
        compose.onNodeWithText(title).performClick()
        compose.waitForIdle()
    }

    /** До строки списка настроек доводят прокруткой: ленивый список её не компонует заранее. */
    private fun scrollSettingsTo(text: String) {
        compose.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag(TAG_SETTINGS_SCREEN)),
            useUnmergedTree = true,
        ).performScrollToNode(hasText(text))
    }

    /** Активный профиль — из постоянных настроек устройства, а не из вида экрана (ПРФ-18). */
    private fun activeProfileId(): String = runBlocking { profileGraph().activeProfile().current() }

    /** Сколько профилей заведено — вопрос к базе, а не к списку на экране (ПРФ-33). */
    private fun profileCount(): Int = runBlocking { profileGraph().profiles().count() }

    /** Приёмы чужого профиля за сегодня: после удаления их нет ни одной. */
    private fun wardIntakes() = DeviceTestSeed.dayIntakes(targetContext, WARD_PROFILE_ID, DeviceTestClock.today)

    /** Продуктовые чтения похода: те же репозитории, что у экранов (TDD §6.2). */
    private fun testGraph(): DeviceTestGraph =
        EntryPointAccessors.fromApplication(targetContext.applicationContext, DeviceTestGraph::class.java)

    private fun profileGraph(): JourneyProfileGraph =
        EntryPointAccessors.fromApplication(targetContext.applicationContext, JourneyProfileGraph::class.java)

    private val ownerMedication: String get() = name("profiles-owner")
    private val wardMedication: String get() = name("profiles-ward-med")

    /**
     * Имя чужого профиля.
     *
     * Своей строки у стенда нет: имя собирается форматом имени лекарства
     * (`device_test_medication_name`). Заводить ресурс имени профиля значило бы добавить
     * строку сразу в шесть каталогов тестового APK ради одного имени — стенд живёт
     * нейтральными именами, и здесь оно нейтрально.
     */
    private val wardName: String get() = name("profiles-ward")

    /** Имя чужого профиля после правки: новое имя — то же, что вводит человек. */
    private val renamedWard: String get() = name("profiles-ward-renamed")

    /** Имя владельца после правки: в списке его не видно (строка названа «Мои лекарства»). */
    private val renamedOwner: String get() = name("profiles-owner-renamed")

    /** Имя профиля, который заводит мастер: третий профиль и первый платный. */
    private val thirdName: String get() = name("profiles-third")

    /** Строка «Добавить профиль» в перечне профилей — вход в мастер (ПРФ-9, §5.13). */
    private val addProfileRow: String get() = targetContext.getString(SettingsR.string.profile_add_action)

    /** Образец цвета в мастере: тег образца собирается формой из имени цвета (§5.13). */
    private val colourTeal: String get() = "profile_colour_${ProfileColor.Teal.name.lowercase(Locale.ROOT)}"

    private fun name(suffix: String): String = testContext.getString(R.string.device_test_medication_name, suffix)

    /** Строка владельца в списке профилей называется «Мои лекарства», а не его именем. */
    private val ownerRow: String get() = targetContext.getString(SettingsR.string.settings_profile_self)

    /** Строка удаления называет того самого профиля, в чьём контексте человек находится (ОВ-7). */
    private val deleteRow: String
        get() = targetContext.getString(SettingsR.string.settings_profile_delete, wardName)

    /** Несворачиваемый баннер чужого профиля — второй признак, словами (ПРФ-13). */
    private val bannerText: String get() = targetContext.getString(UiR.string.profile_banner, wardName)

    private companion object {
        const val OWNER_MEDICATION_ID = "journey-profiles-owner"
        const val WARD_PROFILE_ID = "journey-profiles-ward"
        const val WARD_MEDICATION_ID = "journey-profiles-ward-med"
    }
}

/**
 * Точка входа похода к репозиториям профилей (SKL-140, SKL-121).
 *
 * Мастер добавления профиля в продукте есть (SKL-121), но чужой профиль поход всё равно
 * заводит этой точкой входа: подопечному нужны свои лекарство и история, которых мастер
 * не несёт. Здесь же читается активный профиль — то, чем поход утверждает, что правка
 * и создание его не трогают. Каркас и продукт не трогаются: поход не имеет права менять
 * общий сид ради одного своего шага.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface JourneyProfileGraph {

    fun profiles(): ProfileRepository

    /**
     * Активный профиль похода: им проверяется, что правка имени и создание нового профиля
     * активный контекст не меняют (ПРФ-18, ПРФ-19). Вопрос адресован хранилищу, а не виду
     * экрана: отметка активного — это утверждение строки списка, а не состояние устройства.
     */
    fun activeProfile(): ActiveProfileRepository
}
