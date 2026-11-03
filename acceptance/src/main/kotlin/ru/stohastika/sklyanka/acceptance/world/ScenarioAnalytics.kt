package ru.stohastika.sklyanka.acceptance.world

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.domain.analytics.AnalyticsProperty
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecord
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecorder
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRetention
import ru.stohastika.sklyanka.domain.analytics.AnalyticsScreen
import ru.stohastika.sklyanka.domain.analytics.QueuedAnalyticsRecord
import ru.stohastika.sklyanka.domain.analytics.ScreenView
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsConsentStore
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsQueue
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsTransport
import java.time.Instant

/**
 * Контур аналитики сценария приёмки (SKL-165, TDD §11.4).
 *
 * Подменены ровно порты домена: согласие, очередь и поставщик. Гейт согласия, удержание
 * очереди, отправка и очистка — настоящий [AnalyticsRecorder], поэтому сценарий проверяет
 * то, что работает в продукте, а не его пересказ (АН-2, АН-4, АН-6).
 *
 * Три состояния стенда, которые сценарий называет словами:
 *
 * - **состояние согласия** — то же, что у приложения: `NOT_ASKED`, `ALLOWED`, `DECLINED`;
 * - **доступность сети** — [goOffline]/[goOnline]: поставщик на месте, но пачка не уходит;
 * - **журнал поставщика** — [Transport.delivered] и [networkCalls]. Именно он отвечает
 *   на вопрос «было ли обращение к сети», а не отсутствие записей в очереди.
 *
 * Запись асинхронна (`AnalyticsRecorder.record` запускает корутину), поэтому после действия
 * шаг зовёт [settle]: он дожидается всех запущенных проходов. Без этого проверка читала бы
 * журнал раньше записи и зеленела бы на пустоте.
 */
class ScenarioAnalytics(private val clock: ScenarioClock) {

    val store = RememberedConsent()

    val queue = JournaledQueue()

    val transport = ContourTransport()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** Действующий рекордер: один на «процесс», как в продукте (TDD §2.2). */
    private var active: AnalyticsRecorder = newRecorder()

    /** Сколько раз поставщик вообще трогали: инициализация и пачки. */
    val networkCalls: Int get() = transport.calls

    /** Решение человека как факт: строки «выбрал …» и «аналитика включена» ставят его. */
    var consent: AnalyticsConsentState
        get() = store.consentNow()
        set(value) = runBlocking { store.decide(value) }

    /**
     * Записать продуктовое событие тем же путём, что продукт: через гейт согласия.
     *
     * Мир повторяет **единственную точку записи** продукта — не потому, что правила другие,
     * а потому, что у сценарного уровня нет экрана: вызов `AnalyticsRecorder.record`
     * в `TodayViewModel` и здесь — один и тот же вызов домена. Экранная группа проверяет
     * тот же путь от нажатия (TDD §11.5).
     */
    fun record(event: AnalyticsEvent) {
        active.record(event)
        settle()
    }

    /** Записать событие о согласии: они копятся до решения и уходят только с ним (АН-4). */
    fun recordConsent(event: AnalyticsEvent) {
        active.recordConsent(event)
        settle()
    }

    /**
     * Показ экрана — тем же приёмом, что `LocalScreenTracker` в продукте.
     *
     * Стенд не изобретает событие: он называет экран каталога, а событие строит домен.
     */
    fun openScreen(screen: AnalyticsScreen) = record(ScreenView(screen))

    /** Согласие получено: открыть передачу и отправить накопленное (ТДД §7.4). */
    fun consentGranted() {
        active.onConsentGranted()
        settle()
    }

    /** Отказ или выключение: передача закрыта, очередь пуста. */
    fun consentDeclined() {
        active.onConsentDeclined()
        settle()
    }

    /**
     * Запуск приложения: новый процесс над теми же хранилищами и доотправка очереди.
     *
     * Так же поступает `MainActivity` (`AnalyticsRecorder.drain()` в области запуска):
     * очередь переживает смерть процесса, а согласие — настройка устройства, а не сеанса.
     */
    fun restart() {
        active = newRecorder()
        active.drain()
        settle()
    }

    fun goOffline() {
        transport.online = false
    }

    fun goOnline() {
        transport.online = true
    }

    /** Дождаться запущенных записей: они короткие, но асинхронные по контракту порта. */
    fun settle() {
        runBlocking { scope.coroutineContext.job.children.toList().joinAll() }
    }

    /** Рекордер действующего «процесса»: им пользуются доменные сценарии согласия. */
    fun recorder(): AnalyticsRecorder = active

    /**
     * Рекордер стенда на чужой области: экранные прогоны `app` живут своей областью.
     *
     * Порты при этом общие со сценарием — иначе нажатие на экране записывало бы события
     * в одну очередь, а проверка читала бы другую.
     */
    fun recorder(scope: CoroutineScope): AnalyticsRecorder =
        AnalyticsRecorder(store, queue, transport, clock.time, scope)

    /** Всё, что было записано, в порядке записи: журнал очереди, а не только отправленное. */
    fun records(): List<AnalyticsRecord> = queue.recorded.toList()

    fun recordedNames(): List<AnalyticsEventName> = records().map { it.name }

    private fun newRecorder(): AnalyticsRecorder = recorder(scope)

    /** Согласие в памяти: поток отдаёт изменение сразу, решение считается один раз. */
    class RememberedConsent(initial: AnalyticsConsentState = AnalyticsConsentState.NOT_ASKED) :
        AnalyticsConsentStore {

        private val state = MutableStateFlow(initial)

        override val consent: Flow<AnalyticsConsentState> = state.asStateFlow()

        override suspend fun decide(state: AnalyticsConsentState) {
            this.state.value = state
        }

        fun consentNow(): AnalyticsConsentState = state.value
    }

    /**
     * Очередь в памяти: порядок появления, вытеснение — общим правилом [AnalyticsRetention].
     *
     * [recorded] хранит всё, что когда-либо ставили в очередь, а [dropped] — то, что она
     * выбросила. Второе и есть свидетельство «самые старые отбрасываются»: по журналу
     * записей отброшенное не отличить от отправленного.
     */
    class JournaledQueue : AnalyticsQueue {

        private var nextId: Long = 1
        private val rows = mutableListOf<QueuedAnalyticsRecord>()

        /** Всё, что когда-либо ставили в очередь, в порядке записи. */
        val recorded: MutableList<AnalyticsRecord> = mutableListOf()

        /** Что очередь выбросила по сроку или по объёму. */
        val dropped: MutableList<AnalyticsRecord> = mutableListOf()

        val size: Int get() = rows.size
        val pendingRecords: List<AnalyticsRecord> get() = rows.map { it.record }

        override suspend fun append(record: AnalyticsRecord) {
            recorded += record
            rows += QueuedAnalyticsRecord(nextId++, record)
            rows.sortWith(compareBy({ it.record.occurredAt }, { it.id }))
        }

        override suspend fun pending(limit: Int): List<QueuedAnalyticsRecord> = rows.take(limit)

        override suspend fun delete(ids: List<Long>) {
            rows.removeAll { it.id in ids }
        }

        override suspend fun prune(cutoff: Instant, keep: Int) {
            val doomed = AnalyticsRetention.drop(rows, cutoff, keep)
            dropped += rows.filter { it.id in doomed }.map { it.record }
            delete(doomed)
        }

        override suspend fun clear() {
            rows.clear()
        }

        override suspend fun contains(name: AnalyticsEventName, properties: Map<AnalyticsProperty, String>): Boolean =
            rows.any { it.record.name == name && it.record.properties == properties }
    }

    /**
     * Поставщик в памяти: считает обращения и держит пачки.
     *
     * Без доступа в сеть [deliver] отказывает — так ведёт себя SDK: вызов есть, отправки нет.
     * Записи при отказе остаются в очереди, поэтому «накопленное ждёт» проверяется журналом
     * очереди, а не догадкой.
     */
    class ContourTransport : AnalyticsTransport {

        var online: Boolean = true

        /** Записи принятых пачек — то, что действительно ушло. */
        val delivered: MutableList<AnalyticsRecord> = mutableListOf()

        var starts: Int = 0
            private set
        var stops: Int = 0
            private set
        var refusals: Int = 0
            private set

        val calls: Int get() = starts + deliveries

        var deliveries: Int = 0
            private set

        override val available: Boolean = true

        override fun deliver(records: List<AnalyticsRecord>): Boolean {
            deliveries += 1
            if (!online) {
                refusals += 1
                return false
            }
            delivered += records
            return true
        }

        override fun start() {
            starts += 1
        }

        override fun stop() {
            stops += 1
        }
    }

    /** Имена отправленного в порядке доставки. */
    fun deliveredNames(): List<AnalyticsEventName> = transport.delivered.map { it.name }

    /** Свойства отправленных событий одного имени. */
    fun deliveredProperties(name: AnalyticsEventName): List<Map<AnalyticsProperty, String>> =
        transport.delivered.filter { it.name == name }.map { it.properties }
}
