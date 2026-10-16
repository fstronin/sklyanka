package ru.stohastika.sklyanka.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import ru.stohastika.sklyanka.domain.pro.ProPoint
import ru.stohastika.sklyanka.domain.profile.ProfileCreationDecision
import ru.stohastika.sklyanka.domain.profile.SwitchProfileUseCase
import ru.stohastika.sklyanka.domain.profile.SwitchReason
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import ru.stohastika.sklyanka.domain.transfer.AppStamp
import java.time.Instant
import java.time.LocalDate

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

    /** Кэш подписки: доступ «Про» для гейта на создание профиля (ПРФ-33, Р-84). */
    private val proCache = FakeProCache()

    /** Постоянные настройки оформления в памяти (SKL-120, §5.17). */
    private val appearance = FakeAppearancePreferences()

    /** Доступ «Про» — один на все гейты экрана: и на профили, и на оформление. */
    private val proStatus = ObserveProStatusUseCase(cache = proCache, time = FixedTimeSource(Instant.EPOCH))

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

    /** ПРФ-33: второй профиль бесплатен — при одном заведённом создание открыто без подписки. */
    @Test
    fun theSecondProfileIsFreeWithoutASubscription() = runTest(dispatcher) {
        profiles.state.value = listOf(SettingsFixtures.self())
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProfileCreationDecision.Allowed, model.state.value.profileCreation)
    }

    /** Р-84 пп. 1–3: третий профиль без доступа закрыт платной границей. */
    @Test
    fun theThirdProfileWithoutAccessIsPaid() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProfileCreationDecision.BlockedByPro, model.state.value.profileCreation)
    }

    /** Р-84 п. 3: с действующим доступом тот же третий профиль открыт. */
    @Test
    fun anActiveSubscriptionOpensTheThirdProfile() = runTest(dispatcher) {
        proCache.grant(PRO_UNTIL)
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProfileCreationDecision.Allowed, model.state.value.profileCreation)
    }

    /** ПРФ-32, ПРФ-34: предел пяти не продаётся — закрыт при любой подписке. */
    @Test
    fun theLimitOfFiveIsClosedUnderAnySubscription() = runTest(dispatcher) {
        profiles.state.value = fiveProfiles()
        proCache.grant(PRO_UNTIL)
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProfileCreationDecision.BlockedByLimit, model.state.value.profileCreation)
    }

    /**
     * Р-84 п. 2: покупка в витрине пересчитывает исход, не пересоздавая экран.
     *
     * Человек возвращается из витрины на этот же экран, не покидая приложения, и метка
     * «Про» возле строки добавления обязана исчезнуть — иначе он не узнает, что доступ
     * уже есть, и снова пойдёт покупать.
     */
    @Test
    fun theGrantedAccessIsSeenAfterRefresh() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ProfileCreationDecision.BlockedByPro, model.state.value.profileCreation)

        proCache.grant(PRO_UNTIL)
        model.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProfileCreationDecision.Allowed, model.state.value.profileCreation)
    }

    /** Пять заведённых профилей: предел ПРФ-32. */
    private fun fiveProfiles(): List<Profile> = listOf(SettingsFixtures.self()) +
        (1..4).map { index -> SettingsFixtures.foreign("Guest $index").copy(id = "guest-$index") }

    // --- Оформление: дверь открыта, гейт на выборе (SKL-120, §5.17) -------------------------

    /**
     * §5.17, Р-67 п. 5: строка открывает диалог и **без** доступа.
     *
     * Иначе человек не увидел бы, что покупает, а «Про» стояло бы у поверхности вместо
     * платных строк набора. Витрина при нажатии строки не открывается ни в одном состоянии.
     */
    @Test
    fun theThemeRowOpensThePickerWithoutAccess() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val points = trackShowcase(model)

        model.onIntent(SettingsIntent.OpenThemePicker)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(model.state.value.themePickerVisible)
        assertEquals(emptyList<ProPoint>(), points)
    }

    /** Тем же правилом живёт вторая строка блока: диалог иконки тоже открыт без доступа. */
    @Test
    fun theIconRowOpensThePickerWithoutAccess() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val points = trackShowcase(model)

        model.onIntent(SettingsIntent.OpenIconPicker)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(model.state.value.iconPickerVisible)
        assertEquals(emptyList<ProPoint>(), points)
    }

    /** Р-20, Р-56: базовые «Олива» и «Ледяная» выбираются в любой подписке и без неё. */
    @Test
    fun theBaseThemeAndTheBaseIconAreChosenWithoutAccess() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val points = trackShowcase(model)

        model.onIntent(SettingsIntent.OpenThemePicker)
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(SettingsIntent.SelectTheme(AccentTheme.OLIVE))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AccentTheme.OLIVE, appearance.storedTheme)
        assertFalse(model.state.value.themePickerVisible)

        model.onIntent(SettingsIntent.SelectIconVariant(IconVariant.ICE))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(IconVariant.ICE, appearance.storedVariant)
        assertEquals(emptyList<ProPoint>(), points)
    }

    /**
     * Р-67 пп. 2–3, Р-71: платный выбор без доступа не применяется и открывает витрину.
     *
     * Выбор при этом не меняется, а диалог закрывается: человек остаётся с тем, что
     * применено сейчас, и после покупки нажимает строку ещё раз — скрытого продолжения нет.
     */
    @Test
    fun thePaidThemeWithoutAccessOpensTheShowcaseAndKeepsTheChoice() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val points = trackShowcase(model)

        model.onIntent(SettingsIntent.OpenThemePicker)
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(SettingsIntent.SelectTheme(AccentTheme.AMETHYST))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AccentTheme.OLIVE, appearance.storedTheme)
        assertEquals(AccentTheme.OLIVE, model.state.value.accentTheme)
        assertFalse(model.state.value.themePickerVisible)
        assertEquals(listOf(ProPoint.ACCENT), points)
    }

    /** Тем же правилом — «Тёплая» иконка: один гейт на оба выбора (Р-31). */
    @Test
    fun thePaidIconWithoutAccessOpensTheShowcase() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val points = trackShowcase(model)

        model.onIntent(SettingsIntent.SelectIconVariant(IconVariant.WARM))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(IconVariant.ICE, appearance.storedVariant)
        assertEquals(listOf(ProPoint.ACCENT), points)
    }

    /**
     * Р-22, В-120-3: после отмены подписки заведённое остаётся, выбор другой платной закрыт,
     * а вернуться к базовой можно без подписки.
     *
     * Асимметрия названа нормой честно: платят за создание нового, а не за то, что заведено;
     * запри закрытую дверь — «заведённое не запирается» превратилось бы в «заведённое
     * не отпускает».
     */
    @Test
    fun afterTheSubscriptionEndsTheChosenThemeStaysAndTheBaseOneIsReachable() = runTest(dispatcher) {
        proCache.grant(PRO_UNTIL)
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.onIntent(SettingsIntent.SelectTheme(AccentTheme.AMETHYST))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AccentTheme.AMETHYST, appearance.storedTheme)

        // Подписка кончилась: кэш отдаёт отменённое состояние, и экран перечитывает доступ.
        proCache.expire()
        model.refresh()
        dispatcher.scheduler.advanceUntilIdle()
        val points = trackShowcase(model)

        assertEquals(AccentTheme.AMETHYST, model.state.value.accentTheme)
        model.onIntent(SettingsIntent.SelectTheme(AccentTheme.PINE))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AccentTheme.AMETHYST, appearance.storedTheme)
        assertEquals(listOf(ProPoint.ACCENT), points)

        model.onIntent(SettingsIntent.SelectTheme(AccentTheme.OLIVE))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AccentTheme.OLIVE, appearance.storedTheme)
    }

    /**
     * Событие витрины слушается до нажатия: у потока нет повтора, и поздний слушатель его теряет.
     *
     * Сборщик поднимается на `Dispatchers.Unconfined` — так подписка случается в момент
     * запуска, а не когда дойдёт очередь диспетчера: у `SharedFlow` без повтора сигнал,
     * отправленный до подписки, пропадает молча.
     */
    private fun TestScope.trackShowcase(model: SettingsViewModel): List<ProPoint> {
        val points = mutableListOf<ProPoint>()
        backgroundScope.launch(Dispatchers.Unconfined) { model.showcase.collect { points += it } }
        return points
    }

    private fun viewModel() = SettingsViewModel(
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
            proStatus = proStatus,
        ),
        // Подписка по умолчанию не оформлена: строка блока «Склянка Про» показывает
        // «Не оформлена», и её значение не мешает проверкам списка профилей (SKL-122).
        // Оформление (SKL-120): те же швы, что у продукта, — «Олива» и «Ледяная» по умолчанию.
        appearance = SettingsAppearance(
            observeTheme = ObserveAccentThemeUseCase(appearance),
            observeIcon = ObserveIconVariantUseCase(appearance),
            setTheme = SetAccentThemeUseCase(appearance, proStatus),
            setIcon = SetIconVariantUseCase(appearance, proStatus),
        ),
    )

    private companion object {
        const val SELF_MEDICATIONS = 2
        const val FOREIGN_MEDICATIONS = 4
        const val SNOOZE = 30
        const val SNOOZE_OUTSIDE_THE_SET = 17
        const val VERSION = "0.1.0"
        const val VERSION_CODE = 1

        /** Дата доступа «Про»: часы экрана стоят на эпохе, и подписка заведомо не истекла. */
        val PRO_UNTIL: LocalDate = LocalDate.of(2027, 1, 1)
    }
}
