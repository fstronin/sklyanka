package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.analytics.ActionSource
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentSource
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.domain.analytics.AnalyticsLimits
import ru.stohastika.sklyanka.domain.analytics.AnalyticsProperty
import ru.stohastika.sklyanka.domain.analytics.AnalyticsScreen
import ru.stohastika.sklyanka.domain.analytics.ConsentAnswer
import ru.stohastika.sklyanka.domain.analytics.ConsentChoice
import ru.stohastika.sklyanka.domain.analytics.ConsentShown
import ru.stohastika.sklyanka.domain.analytics.ConsentShownSource
import ru.stohastika.sklyanka.domain.analytics.DecideAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.IntakeAction
import ru.stohastika.sklyanka.domain.analytics.IntakeActionKind
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Шаги группы `@analytics` и `@reminders` (SKL-165, TDD §11.4).
 *
 * Уровень — домен и его порты: согласие, очередь, поставщик и часы. Экрана здесь нет
 * (`ADR-0007`), поэтому «человек нажимает» — это вызов того же доменного сценария, который
 * зовёт экран, а «отправлено» — пачка, принятая поставщиком.
 *
 * Три правила, без которых шаги этой группы были бы самообманом:
 *
 * - **сеть считается журналом поставщика**, а не отсутствием записей в очереди: обращение —
 *   это `start` или `deliver`. Отказ доставки не стирает журнал, поэтому «ни одного
 *   обращения» проверяется числом, а не пустотой;
 * - **до согласия поставщик не инициализируется** (АН-4): шаг «нажимает «Разрешить»»
 *   запоминает число обращений на входе, и следующая проверка судит по нему, а не по
 *   состоянию после отправки;
 * - **очередь переживает перезапуск**: «следующий запуск» — новый рекордер над теми же
 *   хранилищами, как в `MainActivity`.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class Sk165AnalyticsSteps(private val world: AcceptanceWorld) {

    /** Обращения к сети на момент нажатия «Разрешить»: судится до, а не после. */
    private var callsBeforeConsent: Int? = null

    // --- Предыстория: установка, согласие, данные ------------------------------------------

    /**
     * Только что установленное приложение не имеет ни решения, ни очереди, ни обращений.
     *
     * Это проверка, а не сброс: мир создаётся на сценарий, и если в нём уже есть решение,
     * значит Предыстория сценария противоречит сама себе — молча продолжать нельзя.
     */
    @Допустим("^приложение только что установлено$")
    fun theApplicationWasJustInstalled() {
        assertEquals(
            AnalyticsConsentState.NOT_ASKED,
            world.analytics.consent,
            "a fresh installation has no analytics decision",
        )
        assertEquals(0, world.analytics.queue.size, "a fresh installation has an empty queue")
        assertEquals(0, world.analytics.networkCalls, "a fresh installation has not touched the network")
    }

    /** «Аналитика включена»: решение дано заранее, отправка открыта. */
    @Допустим("^аналитика включена$")
    fun analyticsIsOn() {
        world.analytics.consent = AnalyticsConsentState.ALLOWED
        world.analytics.consentGranted()
        assertEquals(AnalyticsConsentState.ALLOWED, world.analytics.consent)
    }

    /** Отказ, названный Предысторией: он не показывается и не спрашивается — он уже стоит. */
    @Допустим("^человек выбрал «Сейчас не надо» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonChoseNotNow(date: String, time: String) {
        world.clock.moveTo(moment(date, time))
        decide(AnalyticsConsentState.DECLINED)
        assertEquals(AnalyticsConsentState.DECLINED, world.analytics.consent)
    }

    /** Лекарство Предыстории у профиля «Я»: приём существует, событие — о нём. */
    @Допустим("^в профиле «Я» заведено лекарство «Магний B6» с приёмом в (\\d{2}:\\d{2})$")
    fun theProfileHasMagnesium(time: String) {
        addMedication("Я", MAGNESIUM, LocalTime.parse(time))
    }

    /** Лекарство без названного профиля: сценарий говорит о человеке, активный профиль — «Я». */
    @Допустим("^заведено лекарство с приёмом в (\\d{2}:\\d{2})$")
    fun aMedicationIsSetUp(time: String) {
        addMedication(world.profiles.active, MAGNESIUM, LocalTime.parse(time))
    }

    /** Доступность сети — состояние устройства, а не свойство отправки. */
    @Допустим("^у устройства нет доступа в сеть$")
    fun theDeviceIsOffline() {
        world.analytics.goOffline()
    }

    /**
     * Неделя без сети: часы идут, человек пользуется приложением, очередь растёт.
     *
     * Очередь наполняется записями о показах экрана — тем же событием, которое пишет
     * продукт; иначе «накопленное не растёт бесконечно» проверялось бы на пустой очереди.
     */
    @Допустим("^у устройства нет доступа в сеть семь суток$")
    fun theDeviceIsOfflineForAWeek() {
        world.analytics.goOffline()
        val start = world.clock.nowLocal()
        repeat(WEEK_DAYS) { day ->
            world.clock.moveTo(start.plusDays(day.toLong()))
            repeat(EVENTS_PER_DAY) {
                world.analytics.openScreen(AnalyticsScreen.TODAY)
            }
        }
        assertTrue(
            world.analytics.queue.recorded.size > AnalyticsLimits().maxEvents,
            "a week of use must queue more than the limit, otherwise retention has nothing to drop",
        )
    }

    // --- Согласие: показ, разрешение, отказ -------------------------------------------------

    /** Показ экрана согласия: событие копится локально и до решения не уходит (АН-4). */
    @Допустим("^человек видит экран про аналитику (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonSeesTheConsentScreen(date: String, time: String) {
        world.clock.moveTo(moment(date, time))
        assertEquals(
            AnalyticsConsentState.NOT_ASKED,
            world.analytics.consent,
            "the consent screen is shown only while there is no decision",
        )
        world.analytics.recordConsent(ConsentShown(ConsentShownSource.FIRST_LAUNCH))
        assertEquals(0, world.analytics.networkCalls, "a shown consent screen must not touch the network")
    }

    /**
     * «Разрешить»: решение, открытие передачи и отправка накопленного — порядком продукта.
     *
     * Число обращений до нажатия запоминается здесь, потому что следующая проверка
     * говорит именно о моменте до него.
     */
    @Когда("^он нажимает «Разрешить» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonAllows(date: String, time: String) {
        world.clock.moveTo(moment(date, time))
        callsBeforeConsent = world.analytics.networkCalls
        decide(AnalyticsConsentState.ALLOWED)
        assertEquals(AnalyticsConsentState.ALLOWED, world.analytics.consent)
    }

    /** «Сейчас не надо»: решение, закрытие передачи и очистка очереди (АН-2). */
    @Когда("^он нажимает «Сейчас не надо» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonDeclines(date: String, time: String) {
        world.clock.moveTo(moment(date, time))
        callsBeforeConsent = world.analytics.networkCalls
        decide(AnalyticsConsentState.DECLINED)
        assertEquals(AnalyticsConsentState.DECLINED, world.analytics.consent)
    }

    // --- Действия человека -------------------------------------------------------------------

    /**
     * Отметка приёма с экрана «Сегодня»: тот же доменный путь, что у кнопки.
     *
     * Слово в кавычках необязательно: один сценарий называет действие («принято»),
     * другой — просто «отмечает приём». Действие одно, и правило записи события одно.
     */
    @Когда("^человек отмечает приём(?: «принято»)? (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonMarksAnIntake(date: String, time: String) {
        ensureAnIntakeExists()
        world.markFromPhone(moment(date, time), MarkResult.TAKEN)
        assertTrue(
            world.intakes.outcomes.lastOrNull() is MarkOutcome.Applied,
            "the mark must be applied — a failed analytics delivery must not stop it (АН-5)",
        )
    }

    /** Показ экрана: маршрут сообщает о себе — событие строит домен, а не шаг. */
    @Когда("^человек открывает экран «История» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonOpensTheHistoryScreen(date: String, time: String) {
        world.clock.moveTo(moment(date, time))
        world.analytics.openScreen(AnalyticsScreen.HISTORY)
    }

    /** Связь вернулась: очередь доотправляется тем же проходом, что при запуске. */
    @Когда("^связь появляется (.+?) в (\\d{2}:\\d{2})$")
    fun theConnectionComesBack(date: String, time: String) {
        world.clock.moveTo(moment(date, time))
        world.analytics.goOnline()
        world.analytics.restart()
    }

    // --- Что именно уходит -------------------------------------------------------------------

    @Тогда("^отправлено событие «intake_action» со свойством «taken»$")
    fun theIntakeActionWasSent() {
        val sent = world.analytics.deliveredProperties(AnalyticsEventName.INTAKE_ACTION)
        assertTrue(sent.isNotEmpty(), "an intake action must reach the provider: ${world.analytics.deliveredNames()}")
        assertEquals(
            IntakeActionKind.TAKEN.wire,
            sent.last()[AnalyticsProperty.ACTION],
            "the event must name the action the person performed",
        )
    }

    /**
     * Медицинских данных в событии нет — по каталогу, а не по тексту.
     *
     * Событие отметки несёт ровно вид действия и источник; значения — проводные имена
     * закрытых перечислений. Названия лекарства, дозы, времени приёма и идентификаторов
     * в нём нет ни ключом, ни значением, и это же гарантирует конструктор события
     * (АН-3, НФТ-19).
     */
    @Тогда("^в событии нет названия лекарства, дозы, времени приёма и идентификаторов профиля и приёма$")
    fun theEventCarriesNoMedicalData() {
        val event = world.analytics.transport.delivered
            .lastOrNull { it.name == AnalyticsEventName.INTAKE_ACTION }
        assertNotNull(event, "the intake event must have been sent before its contents are checked")
        val allowed = IntakeAction(IntakeActionKind.TAKEN, ActionSource.PHONE)
        assertEquals(
            allowed.properties.keys,
            requireNotNull(event).properties.keys,
            "an intake event carries the action and the source, and nothing else",
        )
        val medication = world.medications.get(MAGNESIUM)
        val forbidden = listOf(
            medication.name,
            medication.id,
            medication.doseAmount.toPlainString(),
            medication.times.first().toString(),
            medication.profileId,
        )
        val values = event.properties.values.toList()
        forbidden.forEach { value ->
            assertTrue(
                values.none { it == value },
                "«$value» is medical data or an identifier and must not travel (АН-3)",
            )
        }
    }

    @Тогда("^отправлено событие «screen_view» со свойством «history»$")
    fun theScreenViewWasSent() {
        val sent = world.analytics.deliveredProperties(AnalyticsEventName.SCREEN_VIEW)
        assertTrue(sent.isNotEmpty(), "a screen view must reach the provider: ${world.analytics.deliveredNames()}")
        assertEquals(
            AnalyticsScreen.HISTORY.wire,
            sent.last()[AnalyticsProperty.SCREEN],
            "the event must name the screen the person opened",
        )
    }

    /**
     * Показ и выбор уходят вместе — и только вместе с согласием.
     *
     * Прежде всего проверяется, что отправлен **показ**, накопленный до решения: он был
     * в очереди без всякого согласия, а ушёл только с ним (АН-4).
     */
    @Тогда("^отправлены события о показе экрана согласия и о выборе «Разрешить»$")
    fun theConsentEventsWereSent() {
        val shown = world.analytics.deliveredProperties(AnalyticsEventName.CONSENT_SHOWN)
        val choice = world.analytics.deliveredProperties(AnalyticsEventName.CONSENT_CHOICE)
        assertTrue(shown.isNotEmpty(), "the consent screen show must travel once consent is given")
        assertEquals(
            ConsentShownSource.FIRST_LAUNCH.wire,
            shown.last()[AnalyticsProperty.SOURCE],
            "the show must name its source (Р-96)",
        )
        assertTrue(choice.isNotEmpty(), "the choice must travel with the consent")
        assertEquals(
            ConsentAnswer.ACCEPTED.wire,
            choice.last()[AnalyticsProperty.CHOICE],
            "only the accepted choice has a delivery path",
        )
        assertEquals(
            ConsentChoice(ConsentAnswer.ACCEPTED).properties,
            choice.last(),
            "the choice event carries exactly its closed value",
        )
    }

    /** До нажатия поставщик не инициализировался: журнал обращений пуст (АН-4). */
    @Тогда("^до этого нажатия ни одного сетевого обращения не было$")
    fun theProviderWasNotTouchedBeforeTheDecision() {
        val before = requireNotNull(callsBeforeConsent) {
            "the scenario must have pressed the consent button first: the claim is about the moment before it"
        }
        assertEquals(0, before, "the provider must not be initialised before consent (АН-4)")
    }

    @Тогда("^ни одного сетевого обращения не происходит$")
    fun nothingReachesTheNetwork() {
        assertEquals(
            0,
            world.analytics.networkCalls,
            "a refused consent means no network call at all: ${world.analytics.deliveredNames()}",
        )
    }

    /** Следующий запуск — новый процесс над теми же хранилищами, как в продукте. */
    @Тогда("^при последующих запусках приложение тоже не обращается в сеть$")
    fun theNextLaunchesKeepQuiet() {
        val callsBeforeRestart = world.analytics.networkCalls
        world.analytics.restart()
        assertEquals(
            callsBeforeRestart,
            world.analytics.networkCalls,
            "a launch after a refusal must not drain anything to the network",
        )
    }

    @Тогда("^накопленное о согласии не отправляется никогда$")
    fun theConsentBacklogIsNeverSent() {
        world.analytics.restart()
        val consentSent = world.analytics.deliveredNames().filter { name ->
            name == AnalyticsEventName.CONSENT_SHOWN || name == AnalyticsEventName.CONSENT_CHOICE
        }
        assertEquals(emptyList<AnalyticsEventName>(), consentSent, "a refusal sends nothing, ever (АН-2)")
        assertEquals(0, world.analytics.queue.size, "the refusal clears the queue instead of keeping it")
    }

    // --- Надёжность и пределы ----------------------------------------------------------------

    /** Отметка при отказе отправки: сохранена, видна и ничем человеку не угрожает (АН-5). */
    @Тогда("^отметка сохранена и видна на «Сегодня»$")
    fun theMarkIsSavedAndVisible() {
        val intake = requireNotNull(world.intakes.appliedMarks.values.lastOrNull()) {
            "the scenario never marked an intake"
        }
        assertEquals(
            IntakeStatus.TAKEN,
            intake.status,
            "the mark itself must be saved, whatever happened to the analytics",
        )
        assertEquals(world.clock.today(), intake.scheduledAt.toLocalDate(), "the marked intake is today's")
    }

    /**
     * Ошибки отправки человек не видит: единственный признак сбоя — отказ поставщика.
     *
     * Проверяются обе стороны: отправка действительно упала (иначе утверждение пустое),
     * и ни одного сообщения человеку не появилось.
     */
    @Тогда("^никакого сообщения об ошибке отправки человек не видит$")
    fun theDeliveryFailureIsInvisible() {
        assertTrue(
            world.analytics.transport.refusals > 0,
            "the delivery must have failed, otherwise there is nothing invisible to check",
        )
        assertEquals(
            emptyList<String>(),
            world.intakes.messagesToHuman.toList(),
            "a failed analytics delivery must not surface to the person (АН-5)",
        )
    }

    /** Объём отправленного ограничен пачкой, а не равен накопленному. */
    @Тогда("^отправляется не больше ограниченного объёма накопленных событий$")
    fun onlyABoundedVolumeIsSent() {
        val sent = world.analytics.transport.delivered.size
        assertTrue(sent > 0, "the backlog must be delivered once the connection is back")
        assertTrue(
            sent <= AnalyticsLimits().batchSize,
            "$sent events left in one go while the provider takes ${AnalyticsLimits().batchSize} at most",
        )
    }

    /**
     * Отбрасываются самые старые: срок и объём — по правилу [AnalyticsLimits].
     *
     * Сравниваются времена, а не количество: «старое выброшено» значит, что ни одно
     * отброшенное событие не моложе оставшегося.
     */
    @Тогда("^самые старые события отбрасываются$")
    fun theOldestEventsAreDropped() {
        val dropped = world.analytics.queue.dropped
        assertTrue(dropped.isNotEmpty(), "a week without network and a bounded queue must drop something")
        val kept = world.analytics.queue.pendingRecords
        val newestDropped = requireNotNull(dropped.map { it.occurredAt }.maxOrNull())
        val oldestKept = kept.map { it.occurredAt }.minOrNull()
        assertTrue(
            oldestKept == null || !newestDropped.isAfter(oldestKept),
            "the dropped events must be older than everything kept ($newestDropped > $oldestKept)",
        )
        assertTrue(
            world.analytics.queue.recorded.size - dropped.size <= AnalyticsLimits().maxEvents,
            "the queue must not exceed ${AnalyticsLimits().maxEvents} records (АН-6)",
        )
    }

    /** Напоминание не ждёт аналитики: согласие — отказ, а карточка пришла в свою минуту. */
    @Тогда("^напоминание о приёме показано в назначенную минуту$")
    fun theReminderWasShownOnTime() {
        assertEquals(
            AnalyticsConsentState.DECLINED,
            world.analytics.consent,
            "the reminder must fire without any analytics consent (П-3, АН-5)",
        )
        val card = world.reminders.shownCards.lastOrNull()
        assertNotNull(card, "the reminder must reach the shade: ${world.reminders.shownWithChannels()}")
        assertEquals(
            world.clock.nowLocal().withSecond(0).withNano(0),
            requireNotNull(card).scheduledAt,
            "the reminder is shown in its minute, not later",
        )
        assertEquals(
            MORNING,
            card.scheduledAt.toLocalTime(),
            "the card is the 08:00 intake the scenario set up",
        )
    }

    // --- Внутренние помощники ----------------------------------------------------------------

    private fun decide(decision: AnalyticsConsentState) {
        val useCase = DecideAnalyticsConsentUseCase(world.analytics.store, world.analytics.recorder())
        kotlinx.coroutines.runBlocking { useCase(decision, AnalyticsConsentSource.FIRST_LAUNCH) }
        world.analytics.settle()
    }

    private fun addMedication(profile: String, name: String, time: LocalTime) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = name,
            times = listOf(time),
        )
        world.plan()
        world.reminders.reschedule(RescheduleReason.MEDICATION_ADDED)
    }

    /**
     * У сценария, который говорит «человек отмечает приём», приём обязан быть.
     *
     * Предыстория «Без сети приложение работает как обычно» лекарства не называет: она
     * говорит о человеке, у которого приёмы уже есть. Стенд заводит обычное лекарство,
     * чтобы приём было на что отметить, — иначе шаг проверял бы отсутствие данных,
     * а не работу приложения.
     */
    private fun ensureAnIntakeExists() {
        if (world.medications.names().isEmpty()) {
            addMedication(world.profiles.active, MAGNESIUM, MORNING)
        }
    }

    private fun moment(date: String, time: String): LocalDateTime =
        RussianDates.date(date, world.clock.today()).atTime(LocalTime.parse(time))

    private companion object {
        const val MAGNESIUM = "Магний B6"
        val MORNING: LocalTime = LocalTime.of(8, 0)
        const val WEEK_DAYS = 7
        const val EVENTS_PER_DAY = 100
    }
}
