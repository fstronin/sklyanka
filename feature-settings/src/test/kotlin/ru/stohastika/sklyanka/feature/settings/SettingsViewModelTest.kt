package ru.stohastika.sklyanka.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import ru.stohastika.sklyanka.domain.appearance.ObserveAccentThemeUseCase
import ru.stohastika.sklyanka.domain.appearance.ObserveIconVariantUseCase
import ru.stohastika.sklyanka.domain.appearance.SetAccentThemeUseCase
import ru.stohastika.sklyanka.domain.appearance.SetIconVariantUseCase
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.profile.ProfileCreationDecision
import ru.stohastika.sklyanka.domain.profile.SwitchProfileUseCase
import ru.stohastika.sklyanka.domain.profile.SwitchReason
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.domain.transfer.AppStamp

/**
 * Сборка состояния экрана «Настройки» (`TDD.md §4`).
 *
 * Проверяется не вёрстка, а то, откуда берётся каждая подпись: список профилей с числом
 * лекарств, сводка разрешений и её пересчёт без перезапуска, значения настроек напоминания.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val profiles = FakeProfiles(listOf(SettingsFixtures.foreign(), SettingsFixtures.self()))
    private val active = FakeActiveProfile(SettingsFixtures.SELF_ID)
    private val medications = FakeMedications(
        mapOf(SettingsFixtures.SELF_ID to SELF_MEDICATIONS, SettingsFixtures.FOREIGN_ID to FOREIGN_MEDICATIONS),
    )
    private val reminders = FakeReminderSettings()
    private val permissions = FakePermissions()

    /** Постоянные настройки оформления в памяти (SKL-120, §5.17). */
    private val appearance = FakeAppearancePreferences()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theOwnProfileComesFirstAndIsMarkedActive() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val rows = model.state.value.profiles
        assertEquals(listOf(SettingsFixtures.SELF_ID, SettingsFixtures.FOREIGN_ID), rows.map { it.id })
        assertTrue(rows.first().isSelf)
        assertTrue(rows.first().isActive)
        assertFalse(rows.last().isActive)
    }

    @Test
    fun theForeignRowCarriesTheNumberOfMedicationsAndTheMirroredState() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val foreign = model.state.value.profiles.last()
        assertEquals(FOREIGN_MEDICATIONS, foreign.medicationCount)
        assertTrue(foreign.mirrorReminders)
    }

    /** ПРФ-16: своему профилю индикация «чужого» не добавляется, даже если поле выставлено. */
    @Test
    fun theOwnRowNeverCarriesTheMirroredState() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(model.state.value.profiles.first().mirrorReminders)
    }

    @Test
    fun selectingAProfileSwitchesTheActiveOneWithTheReasonForTheJournal() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(SettingsIntent.SelectProfile(SettingsFixtures.FOREIGN_ID))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(SwitchReason.USER_SETTINGS), active.reasons)
        val rows = model.state.value.profiles
        assertTrue(rows.last().isActive)
        assertFalse(rows.first().isActive)
    }

    /** ОВ-7: строка удаления появляется у активного чужого профиля и называет его. */
    @Test
    fun theActiveForeignProfileOffersDeletion() = runTest(dispatcher) {
        active.switchTo(SettingsFixtures.FOREIGN_ID)
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SettingsFixtures.FOREIGN_ID, model.state.value.deletableProfile?.id)
    }

    /** ПРФ-28: у своего профиля строки удаления нет. */
    @Test
    fun theOwnProfileNeverOffersDeletion() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(model.state.value.deletableProfile)
    }

    @Test
    fun switchingBackToTheOwnProfileRemovesTheDeletionRow() = runTest(dispatcher) {
        active.switchTo(SettingsFixtures.FOREIGN_ID)
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(model.state.value.deletableProfile)

        model.onIntent(SettingsIntent.SelectProfile(SettingsFixtures.SELF_ID))
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(model.state.value.deletableProfile)
    }

    /** НАП-15, НФТ-8: отказы считаются отдельно от непроверяемого автозапуска (НАП-20). */
    @Test
    fun theDeniedPermissionsAreCountedApartFromTheOnesTheSystemDoesNotAnswerAbout() = runTest(dispatcher) {
        permissions.states = mapOf(
            ReminderPermission.NOTIFICATIONS to PermissionState.GRANTED,
            ReminderPermission.NOTIFICATION_CHANNEL to PermissionState.GRANTED,
            ReminderPermission.EXACT_ALARMS to PermissionState.DENIED,
            ReminderPermission.FULL_SCREEN_INTENT to PermissionState.GRANTED,
            ReminderPermission.DND_POLICY_ACCESS to PermissionState.GRANTED,
            ReminderPermission.BOOT_RECEIVER to PermissionState.GRANTED,
            ReminderPermission.AUTOSTART to PermissionState.UNKNOWN,
        )
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, model.state.value.permissions.denied)
        assertEquals(1, model.state.value.permissions.manual)
        assertTrue(model.state.value.permissions.hasDenied)
    }

    /**
     * НФТ-52: разрешение, выданное в системных настройках, видно без перезапуска.
     *
     * Состояние меняется мимо приложения, и приложение узнаёт о нём только тем, что
     * спрашивает заново. Проверка держит именно это: тот же экземпляр экрана, новое
     * состояние системы, обновлённая подпись.
     */
    @Test
    fun theGrantedPermissionIsSeenAfterRefreshWithoutRecreatingTheScreen() = runTest(dispatcher) {
        permissions.states = ReminderPermission.entries.associateWith { PermissionState.GRANTED } +
            (ReminderPermission.EXACT_ALARMS to PermissionState.DENIED)
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, model.state.value.permissions.denied)

        permissions.states = ReminderPermission.entries.associateWith { PermissionState.GRANTED }
        model.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, model.state.value.permissions.denied)
        assertFalse(model.state.value.permissions.hasDenied)
    }

    @Test
    fun theReminderSettingsAreShownAsStored() = runTest(dispatcher) {
        reminders.values = ReminderSettingsValues(persistentEnabled = true, snoozeMinutes = SNOOZE)
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(model.state.value.reminders.persistentEnabled)
        assertEquals(SNOOZE, model.state.value.reminders.snoozeMinutes)
    }

    /** Критерий приёмки: изменение настройки видно на экране сразу, без перезахода. */
    @Test
    fun switchingOffThePersistentReminderIsVisibleAtOnce() = runTest(dispatcher) {
        reminders.values = ReminderSettingsValues(persistentEnabled = true)
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(SettingsIntent.SetPersistentReminder(enabled = false))
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(model.state.value.reminders.persistentEnabled)
        assertFalse(reminders.values.persistentEnabled)
    }

    /** SKL-78, ОВ-98: «Отложить на» открывает выбор интервала. */
    @Test
    fun openingTheSnoozePickerShowsIt() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(SettingsIntent.OpenSnooze)

        assertTrue(model.state.value.snoozePickerVisible)
    }

    @Test
    fun dismissingTheSnoozePickerHidesIt() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(SettingsIntent.OpenSnooze)

        model.onIntent(SettingsIntent.DismissSnooze)

        assertFalse(model.state.value.snoozePickerVisible)
    }

    /** Выбор интервала записывается через порт, виден сразу и закрывает диалог. */
    @Test
    fun choosingAnIntervalStoresItAndClosesThePicker() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(SettingsIntent.OpenSnooze)

        model.onIntent(SettingsIntent.SetSnooze(SNOOZE))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SNOOZE, model.state.value.reminders.snoozeMinutes)
        assertEquals(SNOOZE, reminders.values.snoozeMinutes)
        assertFalse(model.state.value.snoozePickerVisible)
    }

    /** Значение вне набора отклоняется до записи: диалог его не порождает. */
    @Test
    fun anIntervalOutsideTheSetIsRefused() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(SettingsIntent.SetSnooze(SNOOZE_OUTSIDE_THE_SET))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ReminderSettingsValues.DEFAULT_SNOOZE_MINUTES, model.state.value.reminders.snoozeMinutes)
        assertEquals(ReminderSettingsValues.DEFAULT_SNOOZE_MINUTES, reminders.values.snoozeMinutes)
    }

    @Test
    fun theVersionShownIsTheOneOfTheBuiltPackage() = runTest(dispatcher) {
        val model = viewModel()

        assertEquals(VERSION, model.state.value.version)
    }

    /** ПРФ-32: пока профилей меньше пяти, добавление открыто. */
    @Test
    fun creationIsOpenUntilTheFifthProfile() = runTest(dispatcher) {
        profiles.state.value = listOf(SettingsFixtures.self())
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProfileCreationDecision.Allowed, model.state.value.profileCreation)
    }

    /** ПРФ-32, ПРФ-34: пятый профиль закрывает добавление — это предел продукта. */
    @Test
    fun theLimitOfFiveClosesCreation() = runTest(dispatcher) {
        profiles.state.value = fiveProfiles()
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProfileCreationDecision.BlockedByLimit, model.state.value.profileCreation)
    }

    /** Пять заведённых профилей: предел ПРФ-32. */
    private fun fiveProfiles(): List<Profile> = listOf(SettingsFixtures.self()) +
        (1..4).map { index -> SettingsFixtures.foreign("Guest $index").copy(id = "guest-$index") }

    // --- Оформление: выбор применяется сразу (SKL-120, §5.17) ------------------------------

    /** §5.17: строка открывает диалог — весь набор виден до нажатия. */
    @Test
    fun theThemeRowOpensThePicker() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(SettingsIntent.OpenThemePicker)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(model.state.value.themePickerVisible)
    }

    /** Тем же правилом живёт вторая строка блока: диалог иконки тоже открывается нажатием. */
    @Test
    fun theIconRowOpensThePicker() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(SettingsIntent.OpenIconPicker)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(model.state.value.iconPickerVisible)
    }

    /** Р-20, Р-56: тема и вариант иконки выбираются и сразу записываются. */
    @Test
    fun theThemeAndTheIconAreChosenAndStored() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(SettingsIntent.OpenThemePicker)
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(SettingsIntent.SelectTheme(AccentTheme.OLIVE))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AccentTheme.OLIVE, appearance.storedTheme)
        assertFalse(model.state.value.themePickerVisible)

        model.onIntent(SettingsIntent.SelectIconVariant(IconVariant.ICE))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(IconVariant.ICE, appearance.storedVariant)
    }

    /**
     * §5.17: любой вариант набора применяется и закрывает диалог — платных строк
     * в наборе нет, выбор всегда применяется.
     */
    @Test
    fun anyThemeIsAppliedAndClosesThePicker() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(SettingsIntent.OpenThemePicker)
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(SettingsIntent.SelectTheme(AccentTheme.AMETHYST))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AccentTheme.AMETHYST, appearance.storedTheme)
        assertEquals(AccentTheme.AMETHYST, model.state.value.accentTheme)
        assertFalse(model.state.value.themePickerVisible)
    }

    /** Тем же правилом — «Тёплая» иконка: оба варианта применяются. */
    @Test
    fun anyIconVariantIsApplied() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onIntent(SettingsIntent.SelectIconVariant(IconVariant.WARM))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(IconVariant.WARM, appearance.storedVariant)
        assertFalse(model.state.value.iconPickerVisible)
    }

    private fun viewModel(): SettingsViewModel = SettingsViewModel(
        profiles = profiles,
        context = FakeProfileContext(profiles, active),
        medications = medications,
        switchProfile = SwitchProfileUseCase(profiles = profiles, active = active),
        environment = SettingsEnvironment(
            system = SettingsSystemStateReader(
                reminders = reminders,
                permissions = permissions,
            ),
            stamp = AppStamp(versionName = VERSION, versionCode = VERSION_CODE),
        ),
        // Оформление (SKL-120): те же швы, что у продукта, — «Олива» и «Ледяная» по умолчанию.
        appearance = SettingsAppearance(
            observeTheme = ObserveAccentThemeUseCase(appearance),
            observeIcon = ObserveIconVariantUseCase(appearance),
            setTheme = SetAccentThemeUseCase(appearance),
            setIcon = SetIconVariantUseCase(appearance),
        ),
    )

    private companion object {
        const val SELF_MEDICATIONS = 2
        const val FOREIGN_MEDICATIONS = 4
        const val SNOOZE = 30
        const val SNOOZE_OUTSIDE_THE_SET = 17
        const val VERSION = "0.1.0"
        const val VERSION_CODE = 1
    }
}
