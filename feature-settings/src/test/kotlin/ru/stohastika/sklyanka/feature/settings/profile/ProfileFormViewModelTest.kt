package ru.stohastika.sklyanka.feature.settings.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import ru.stohastika.sklyanka.domain.profile.CreateProfileUseCase
import ru.stohastika.sklyanka.domain.profile.ProfileCreationDecision
import ru.stohastika.sklyanka.domain.profile.ProfileIdSource
import ru.stohastika.sklyanka.domain.profile.ProfileSetReplanner
import ru.stohastika.sklyanka.domain.profile.RenameProfileUseCase
import ru.stohastika.sklyanka.domain.profile.SetMirrorRemindersUseCase
import ru.stohastika.sklyanka.domain.profile.SetProfileColourUseCase
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import ru.stohastika.sklyanka.feature.settings.FakeProCache
import ru.stohastika.sklyanka.feature.settings.FakeProfiles
import ru.stohastika.sklyanka.feature.settings.transfer.RecordingLog
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Состояние формы профиля: мастер добавления и правка заведённого (SKL-121, §5.13–§5.14).
 *
 * Прогон на JVM: форма — это поля, граница «Про» и предел пяти над настоящими сценариями
 * домена, а не разметка. Что именно видно на экране (заголовок, подпись кнопки, слова
 * причины), проверяют экранные тесты.
 *
 * Хранилище здесь в памяти, а сценарии — настоящие: и «первый свободный цвет», и «владелец
 * не перекрашивается», и «предел не продаётся» — утверждения о связке формы с доменом,
 * и на подставных сценариях они доказывали бы только то, что подставлены.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileFormViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Часы формы: подписка «Про» считается по дате, и она должна быть управляемой (Р-22). */
    private val clock = FixedTimeSource(Instant.parse("2026-09-25T11:00:00Z"), ZoneId.of("Europe/Moscow"))

    private val profiles = FakeProfiles(listOf(self()))
    private val ids = SequentialProfileIds()
    private val planning = ReplanCounter()
    private val log = RecordingLog()

    /** Кэш подписки: доступ «Про» для гейта создания (ПРФ-33, Р-68 п. 7). */
    private val proCache = FakeProCache()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Мастер добавления -----------------------------------------------------------------

    /**
     * §5.13: мастер открывается пустым, с первым **свободным** цветом набора и включённым
     * дублированием (Р-29).
     *
     * Владелец здесь намеренно носит Терракоту: у настоящего владельца цвет — основной
     * акцент, и он в набор не входит (ПРФ-16). Занятый первый цвет — единственный способ
     * отличить «первый свободный» от «первый в наборе».
     */
    @Test
    fun `the add form starts empty with the first free colour and mirroring on`() = runTest(dispatcher) {
        profiles.state.value = listOf(self(color = ProfileColor.Terracotta.argb))
        val model = viewModel()

        open(model, ProfileFormMode.Add)

        val state = model.state.value!!
        assertEquals("", state.name)
        assertEquals(ProfileColor.Teal.argb, state.colour)
        assertTrue(state.mirrorReminders)
        assertEquals(ProfileCreationDecision.Allowed, state.creation)
        assertTrue(state.showsColour)
        assertTrue(state.showsMirror)
    }

    /**
     * ПРФ-33, Р-84 п. 2: третьего профиля без доступа нет — над кнопкой стоит платная
     * причина, а нажатие открывает витрину, а не заводит профиль.
     *
     * Причина показывается по набранному имени: пока имя пусто, форму закрывает она сама
     * (§5.13 — по одной причине за раз).
     */
    @Test
    fun `the pro border is named once the name is typed`() = runTest(dispatcher) {
        profiles.state.value = listOf(self(), foreign())
        val model = viewModel()
        open(model, ProfileFormMode.Add)

        model.onIntent(ProfileFormIntent.NameChanged("Grandma"))

        val state = model.state.value!!
        assertEquals(ProfileFormBlock.Pro, state.block)
        assertEquals(ProfileCreationDecision.BlockedByPro, state.creation)
    }

    /**
     * ПРФ-32, ПРФ-34: пять профилей — предел при **любой** подписке, и он называется первым.
     *
     * Действующий доступ здесь выдан намеренно: предел не продаётся, и докупленная подписка
     * не имеет права выглядеть открытой дверью в тупик (П-7).
     */
    @Test
    fun `the limit of five closes the add form even with an active subscription`() = runTest(dispatcher) {
        profiles.state.value = fiveProfiles()
        proCache.grant(PRO_UNTIL)
        val model = viewModel()

        open(model, ProfileFormMode.Add)

        assertEquals(ProfileFormBlock.Limit, model.state.value!!.block)

        model.onIntent(ProfileFormIntent.NameChanged("Grandma"))

        assertEquals(ProfileFormBlock.Limit, model.state.value!!.block)
        assertEquals(ProfileCreationDecision.BlockedByLimit, model.state.value!!.creation)
    }

    /**
     * §5.13, правило «по одной причине за раз»: пустое имя называется раньше платной границы.
     *
     * Порядок виден человеку: он открывает мастер и узнаёт, чего не хватает именно ему
     * сейчас, а не узнаёт о покупке вместо незаполненного имени.
     */
    @Test
    fun `a blank name is named before the pro border`() = runTest(dispatcher) {
        profiles.state.value = listOf(self(), foreign())
        val model = viewModel()

        open(model, ProfileFormMode.Add)

        assertEquals(ProfileFormBlock.Name, model.state.value!!.block)
    }

    /** ПРФ-9: с пустым именем создание не завершается и форма не закрывается. */
    @Test
    fun `submitting a blank name creates nothing and does not close`() = runTest(dispatcher) {
        val model = viewModel()
        open(model, ProfileFormMode.Add)
        val closed = eventsOf(model.closed)

        model.onIntent(ProfileFormIntent.Submit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(SELF_ID), profiles.state.value.map { it.id })
        assertTrue("a blank name must not close the form", closed.isEmpty())
    }

    /**
     * Р-84 п. 2, П-7: на платной границе то же нажатие просит витрину и не заводит профиль.
     *
     * Витрина — одна поверхность на все точки входа (Р-67): форма не рисует шторку сама.
     */
    @Test
    fun `submitting on the pro border asks for the showcase and creates nothing`() = runTest(dispatcher) {
        profiles.state.value = listOf(self(), foreign())
        val model = viewModel()
        open(model, ProfileFormMode.Add)
        val showcase = eventsOf(model.showcase)
        val closed = eventsOf(model.closed)
        model.onIntent(ProfileFormIntent.NameChanged("Grandma"))

        model.onIntent(ProfileFormIntent.Submit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, showcase.size)
        assertEquals(2, profiles.state.value.size)
        assertTrue("the showcase must not close the form", closed.isEmpty())
    }

    /**
     * Р-84 п. 2: покупка пересчитывает доступ, и создание завершает **та же** кнопка.
     *
     * Скрытого автопродолжения нет: человек возвращается из витрины в ту же форму с
     * набранным именем и нажимает её сам — иначе набранное имя потерялось бы.
     */
    @Test
    fun `a purchase after the showcase lets the same press create the profile`() = runTest(dispatcher) {
        profiles.state.value = listOf(self(), foreign())
        val model = viewModel()
        open(model, ProfileFormMode.Add)
        val closed = eventsOf(model.closed)
        model.onIntent(ProfileFormIntent.NameChanged("Grandma"))

        proCache.grant(PRO_UNTIL)
        model.refreshProAccess()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProfileCreationDecision.Allowed, model.state.value!!.creation)
        assertNull(model.state.value!!.block)

        model.onIntent(ProfileFormIntent.Submit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, closed.size)
        assertEquals(3, profiles.state.value.size)
        assertTrue(profiles.state.value.any { it.name == "Grandma" })
    }

    // --- Правка заведённого ----------------------------------------------------------------

    /**
     * ПРФ-26, ПРФ-27: у владельца правится только имя — ни цвета, ни дублирования
     * (цвет владельца не виден нигде, ПРФ-16).
     *
     * Отсутствие правок проверяется журналом, а не значением в хранилище: цвет и флаг
     * у владельца остались бы прежними и в том случае, если бы сценарий их отверг, — а
     * запись о смене сказала бы, что экран их всё-таки отправил.
     */
    @Test
    fun `editing the owner keeps only the name editable`() = runTest(dispatcher) {
        val model = viewModel()
        val closed = eventsOf(model.closed)

        open(model, ProfileFormMode.Edit(SELF_ID))

        val state = model.state.value!!
        assertTrue(state.isSelf)
        assertFalse(state.showsColour)
        assertFalse(state.showsMirror)
        assertEquals("Owner", state.name)

        model.onIntent(ProfileFormIntent.NameChanged("Owner Jr"))
        model.onIntent(ProfileFormIntent.Submit)
        dispatcher.scheduler.advanceUntilIdle()

        val stored = profiles.state.value.single { it.id == SELF_ID }
        assertEquals("Owner Jr", stored.name)
        assertEquals(ProfilePalette.ownerAccentArgb, stored.color)
        assertTrue(stored.mirrorReminders)
        assertEquals(1, closed.size)
        assertEquals(listOf(OpEvent.PROFILE_RENAMED), log.records.map { it.event })
    }

    /**
     * ПРФ-15, ПРФ-27: у чужого профиля сохраняются имя, цвет из набора и смена дублирования.
     *
     * Смена дублирования — событие перепланирования (ПРФ-6): без него будильники профиля
     * появляются не раньше следующего якорного, а человек, включивший дублирование, ждёт
     * звонка сейчас. Перепланирование проверяется числом.
     */
    @Test
    fun `editing a foreign profile saves the name the colour and the mirroring`() = runTest(dispatcher) {
        profiles.state.value = listOf(self(), foreign())
        val model = viewModel()
        val closed = eventsOf(model.closed)

        open(model, ProfileFormMode.Edit(FOREIGN_ID))

        val opened = model.state.value!!
        assertFalse(opened.isSelf)
        assertTrue(opened.showsColour)
        assertTrue(opened.showsMirror)
        assertEquals("Mother", opened.name)
        assertEquals(ProfileColor.Terracotta.argb, opened.colour)

        model.onIntent(ProfileFormIntent.NameChanged("Grandma"))
        model.onIntent(ProfileFormIntent.ColourChosen(ProfileColor.Olive.argb))
        model.onIntent(ProfileFormIntent.MirrorChanged(enabled = false))
        model.onIntent(ProfileFormIntent.Submit)
        dispatcher.scheduler.advanceUntilIdle()

        val stored = profiles.state.value.single { it.id == FOREIGN_ID }
        assertEquals("Grandma", stored.name)
        assertEquals(ProfileColor.Olive.argb, stored.color)
        assertFalse(stored.mirrorReminders)
        assertEquals(1, closed.size)
        assertEquals(
            listOf(OpEvent.PROFILE_RENAMED, OpEvent.PROFILE_COLOUR_CHANGED, OpEvent.PROFILE_MIRROR_CHANGED),
            log.records.map { it.event },
        )
        assertEquals(1, planning.calls)
    }

    /**
     * Профиль исчез, пока форма открывалась (удалён в другом месте или принесён переносом).
     *
     * Правку открывать не над чем: пустая форма чужого профиля показала бы человека,
     * которого на устройстве нет, и сохранила бы его «заново» при первом же нажатии.
     */
    @Test
    fun `editing a profile that has gone closes the form at once`() = runTest(dispatcher) {
        val model = viewModel()
        val closed = eventsOf(model.closed)

        open(model, ProfileFormMode.Edit(ABSENT_ID))

        assertEquals(1, closed.size)
        assertNull(model.state.value)
    }

    // --- Обвязка ---------------------------------------------------------------------------

    private fun viewModel() = ProfileFormViewModel(
        profiles = profiles,
        createProfile = CreateProfileUseCase(
            profiles = profiles,
            palette = ProfilePalette.allowedArgb,
            ids = ids,
            replan = planning,
            log = log,
        ),
        renameProfile = RenameProfileUseCase(profiles = profiles, log = log),
        setProfileColour = SetProfileColourUseCase(
            profiles = profiles,
            palette = ProfilePalette.allowedArgb,
            log = log,
        ),
        setMirrorReminders = SetMirrorRemindersUseCase(profiles = profiles, replan = planning, log = log),
        proStatus = ObserveProStatusUseCase(cache = proCache, time = clock),
    )

    private fun open(model: ProfileFormViewModel, mode: ProfileFormMode) {
        model.open(mode)
        dispatcher.scheduler.advanceUntilIdle()
    }

    /**
     * Подписка на событие формы.
     *
     * `Unconfined`: сборщик встаёт сразу, и сигнал доходит до него в момент отправки,
     * а не в следующий обход планировщика (образец формы лекарства, SKL-57).
     */
    private fun TestScope.eventsOf(flow: SharedFlow<Unit>): MutableList<Unit> {
        val events = mutableListOf<Unit>()
        backgroundScope.launch(Dispatchers.Unconfined) { flow.collect { events += it } }
        return events
    }

    /** Владелец: цвет по умолчанию — основной акцент приложения, он в набор не входит (ПРФ-16). */
    private fun self(color: Int = ProfilePalette.ownerAccentArgb) = Profile(
        id = SELF_ID,
        name = "Owner",
        color = color,
        isSelf = true,
        mirrorReminders = true,
    )

    private fun foreign(
        id: String = FOREIGN_ID,
        name: String = "Mother",
        color: Int = ProfileColor.Terracotta.argb,
        mirror: Boolean = true,
    ) = Profile(id = id, name = name, color = color, isSelf = false, mirrorReminders = mirror)

    /** Пять заведённых профилей: предел ПРФ-32 и различимая палитра Р-84. */
    private fun fiveProfiles(): List<Profile> =
        listOf(self()) + (1..4).map { index -> foreign(id = "guest-$index", name = "Guest $index") }

    private companion object {
        const val SELF_ID = "self"
        const val FOREIGN_ID = "mother"
        const val ABSENT_ID = "grandma"

        /** Дата доступа «Про»: часы формы стоят на 25 сентября 2026 — подписка не истекла. */
        val PRO_UNTIL: LocalDate = LocalDate.of(2027, 1, 1)
    }
}

/** Предсказуемые идентификаторы: иначе «профиль получил свой id» нечем доказать. */
private class SequentialProfileIds : ProfileIdSource {

    private var next = 0

    override fun newId(): String = "profile-${next++}"
}

/** Счётчик перепланирований: утверждения «вызвано» и «не вызвано» здесь одинаково важны (ПРФ-6). */
private class ReplanCounter : ProfileSetReplanner {

    var calls: Int = 0
        private set

    override suspend fun onProfileSetChanged() {
        calls++
    }
}
