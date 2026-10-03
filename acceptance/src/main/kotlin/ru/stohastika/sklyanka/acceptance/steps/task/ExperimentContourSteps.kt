package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentDecision
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentStore
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentVersion
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsLog
import ru.stohastika.sklyanka.domain.analytics.AnalyticsSink
import ru.stohastika.sklyanka.domain.analytics.ConsentedAnalytics
import ru.stohastika.sklyanka.domain.analytics.ExperimentExposed
import ru.stohastika.sklyanka.domain.experiment.ConsentedExperiments
import ru.stohastika.sklyanka.domain.experiment.ExperimentCacheStore
import ru.stohastika.sklyanka.domain.experiment.ExperimentId
import ru.stohastika.sklyanka.domain.experiment.ExperimentVendor
import ru.stohastika.sklyanka.domain.experiment.VariantKey
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/**
 * Шаги сценарной приёмки A/B-контура (SKL-83, группа без `@manual`/`@device`).
 *
 * **Поведенческие сценарии** (согласие, fail-safe, кэш, debug, выключение) исполняются
 * настоящим доменным гейтом [ConsentedExperiments] поверх подменённых вендора, кэша
 * и канала аналитики: «до согласия — ноль запросов» доказывается числом вызовов вендора,
 * «ошибка не выходит наружу» — отсутствием исключения и сохранённым снимком.
 *
 * **Каталог, поверхности, таргетинг, событие, кэш-файл и гейты сборки** проверяются
 * структурно по файлам — тем же способом, что у SKL-24/SKL-82: читаются каталог
 * экспериментов, адаптер, хранилище и правила контура. Сценарий, читающий наличие
 * строки, честнее сценария, «проверяющего» несуществующий вызов.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions", "LargeClass")
class ExperimentContourSteps {

    private var debugBuild = false
    private var contour = Contour()
    private var catalogProblems: List<String> = emptyList()
    private var surfaceProblems: List<String> = emptyList()
    private var reliabilityProblems: List<String> = emptyList()
    private var targetingProblems: List<String> = emptyList()
    private var eventProblems: List<String> = emptyList()
    private var cacheProblems: List<String> = emptyList()
    private var requiredBlock: String = ""

    // =======================================================================================
    // Согласие, отказ и выключение (сценарии 1–4)
    // =======================================================================================

    @Допустим("^решение по аналитике ещё не принято$")
    fun theAnalyticsDecisionIsNotMade() {
        contour = Contour(debuggable = debugBuild)
    }

    @Допустим("^согласие на аналитику получено$")
    fun theConsentIsGranted() {
        runBlocking { contour.grant() }
    }

    @Допустим("^согласие на аналитику получено и вариант «([^»]+)» уже действует$")
    fun theConsentIsGrantedAndTheVariantIsActive(variant: String) {
        runBlocking {
            contour.grant()
            contour.vendor.response = { mapOf(ExperimentId.TODAY_EMPTY_STATE to variantKey(variant)) }
            contour.experiments.refresh()
            contour.experiments.variantOf(ExperimentId.TODAY_EMPTY_STATE)
        }
    }

    @Допустим("^вариант «([^»]+)» эксперимента «([^»]+)» получен и сохранён$")
    fun theVariantIsReceivedAndStored(variant: String, experiment: String) {
        runBlocking {
            contour.grant()
            contour.vendor.response = { mapOf(experimentId(experiment) to variantKey(variant)) }
            contour.experiments.refresh()
        }
    }

    @Допустим("^сборка приложения отладочная$")
    fun theBuildIsDebug() {
        debugBuild = true
        contour = Contour(debuggable = true)
    }

    @Когда("^контур экспериментов запускается вместе с приложением$")
    fun theContourStartsWithTheApp() {
        runBlocking { contour.start() }
    }

    @Когда("^контур экспериментов запускается$")
    fun theContourStarts() {
        runBlocking { contour.start() }
    }

    @Когда("^контур экспериментов получает вариант «([^»]+)» эксперимента «([^»]+)»$")
    fun theContourReceivesTheVariant(variant: String, experiment: String) {
        runBlocking {
            contour.vendor.response = { mapOf(experimentId(experiment) to variantKey(variant)) }
            contour.experiments.refresh()
        }
    }

    @Когда("^человек выключает аналитику в настройках$")
    fun thePersonTurnsAnalyticsOff() {
        runBlocking { contour.disable() }
    }

    @Когда("^решение по аналитике сменено на отказ$")
    fun theDecisionIsChangedToRefusal() {
        runBlocking { contour.disable() }
    }

    @Тогда("^поставщик вариантов не вызывается ни разу$")
    fun theVendorIsNeverCalled() {
        assertEquals(0, contour.vendor.calls.get(), "вендор вариантов не должен вызываться")
    }

    @Тогда("^любой эксперимент читается как базовый$")
    fun everyExperimentReadsAsBase() {
        ExperimentId.entries.forEach { experiment ->
            val variant = contour.experiments.variantOf(experiment)
            assertEquals(VariantKey.BASE, variant, "${experiment.name} должен быть базовым")
        }
    }

    @Тогда("^экраны остаются базовыми$")
    fun theScreensStayBase() {
        // Экраны читают вариант через тот же гейт: базовый ответ гейта — базовый экран.
        everyExperimentReadsAsBase()
    }

    @Тогда("^эксперимент «([^»]+)» читается как вариант «([^»]+)»$")
    fun theExperimentReadsAsTheVariant(experiment: String, variant: String) {
        assertEquals(
            variantKey(variant),
            contour.experiments.variantOf(experimentId(experiment)),
        )
    }

    @Тогда("^эксперимент «([^»]+)» читается как базовый$")
    fun theExperimentReadsAsBase(experiment: String) {
        assertEquals(VariantKey.BASE, contour.experiments.variantOf(experimentId(experiment)))
    }

    @Тогда("^эксперимент читается как базовый$")
    fun theExperimentReadsAsBaseByName() {
        // Выражение без имени: проверяются все эксперименты каталога.
        everyExperimentReadsAsBase()
    }

    @Тогда("^событие экспонирования называет эксперимент «([^»]+)» и вариант «([^»]+)»$")
    fun theExposureEventNamesTheExperimentAndTheVariant(experiment: String, variant: String) {
        assertTrue(
            contour.sink.events.contains(ExperimentExposed(experimentId(experiment), variantKey(variant))),
            "экспонирование обязано называть пару: ${contour.sink.events}",
        )
    }

    @Тогда("^после выключения поставщик вариантов не вызывается$")
    fun theVendorIsNotCalledAfterDisabling() {
        val before = contour.vendor.calls.get()
        runBlocking { contour.experiments.refresh() }

        assertEquals(before, contour.vendor.calls.get(), "после выключения запросов вариантов нет")
    }

    @Тогда("^сохранённый вариант не применяется$")
    fun theStoredVariantIsNotApplied() {
        assertTrue(contour.cacheSnapshot().isNotEmpty(), "снимок обязан остаться на диске")
        everyExperimentReadsAsBase()
    }

    // =======================================================================================
    // Fail-safe (сценарии 5–9)
    // =======================================================================================

    @Когда("^получение вариантов срывается ошибкой сети$")
    fun receivingTheVariantsFailsWithANetworkError() {
        runBlocking {
            contour.vendor.failure = IOException("offline")
            contour.experiments.refresh()
        }
    }

    @Когда("^сервис вариантов не отвечает дольше отведённого времени$")
    fun theServiceDoesNotRespond() {
        runBlocking {
            contour.vendor.hang = true
            contour.experiments.refresh()
        }
    }

    @Когда("^контур экспериментов стартует заново$")
    fun theContourStartsAgain() {
        val restarted = Contour(cache = contour.sharedCache(), debuggable = debugBuild)
        contour = restarted
        runBlocking { restarted.grant() }
    }

    @Когда("^сервис отдаёт значение вне каталога экспериментов$")
    fun theServiceReturnsAValueOutsideTheCatalog() {
        runBlocking {
            // Каталог закрыт: «вне каталога» на языке порта — вариант, которого эксперимент
            // не допускает; чужая строка из консоли отсекается адаптером (ExperimentCodesTest).
            contour.vendor.response = { mapOf(ExperimentId.SETTINGS_TRANSFER_HINT to VariantKey.B) }
            contour.experiments.refresh()
        }
    }

    @Тогда("^сбой не выходит наружу$")
    fun theFailureDoesNotEscape() {
        // Исключение не вылетело бы наружу ни из одного шага выше; здесь фиксируется
        // наблюдаемое следствие: гейт отвечает и логирует сбой, а не падает.
        assertTrue(contour.vendor.calls.get() >= 1, "попытка получения вариантов обязана состояться")
    }

    @Тогда("^функции приложения остаются доступными$")
    fun theAppFunctionsStayAvailable() {
        everyExperimentReadsAsBase()
        runBlocking { contour.experiments.refresh() }
    }

    @Тогда("^ожидание прекращается само$")
    fun theWaitStopsByItself() {
        assertEquals(1, contour.vendor.calls.get(), "запрос был один и завершился таймаутом")
    }

    @Тогда("^применяется базовый вариант$")
    fun theBaseVariantApplies() {
        everyExperimentReadsAsBase()
    }

    @Тогда("^эксперимент «([^»]+)» остаётся вариантом «([^»]+)»$")
    fun theExperimentStaysTheVariant(experiment: String, variant: String) {
        assertEquals(
            variantKey(variant),
            contour.experiments.variantOf(experimentId(experiment)),
        )
    }

    @Тогда("^такое значение не применяется$")
    fun suchValueIsNotApplied() {
        assertEquals(VariantKey.BASE, contour.experiments.variantOf(ExperimentId.SETTINGS_TRANSFER_HINT))
    }

    @Тогда("^запрос вариантов для этого не потребовался$")
    fun noRequestWasNeededForThis() {
        assertEquals(0, contour.vendor.calls.get(), "снимок прошлого запуска применяется без запроса")
    }

    // =======================================================================================
    // Каталог (сценарий 11)
    // =======================================================================================

    @Когда("^разработчик читает каталог экспериментов$")
    fun theDeveloperReadsTheExperimentCatalog() {
        val catalog = read(EXPERIMENT_CATALOG)
        val design = read(TDD)
        val problems = mutableListOf<String>()
        ExperimentId.entries.forEach { experiment ->
            if (!catalog.contains(experiment.name)) problems += "нет объявления ${experiment.name}"
            if (!design.contains(experiment.name)) problems += "нет описания ${experiment.name}"
            if (experiment.base != VariantKey.BASE) problems += "${experiment.name} без базового варианта"
            if (experiment.variants.isEmpty()) problems += "${experiment.name} без вариантов"
        }
        if (!catalog.contains("enum class VariantKey")) problems += "варианты не закрытое перечисление"
        if (DOMAIN_WORDS.containsMatchIn(catalog)) problems += "каталог знает доменные типы"
        catalogProblems = problems
    }

    @Тогда("^у каждого эксперимента есть имя, область, варианты и базовый вариант$")
    fun everyExperimentHasANameAreaVariantsAndBase() {
        assertTrue(catalogProblems.isEmpty(), "каталог нарушен: $catalogProblems")
    }

    @Тогда("^значения каталога — закрытые перечисления без доменных полей$")
    fun theCatalogValuesAreClosedEnumerations() {
        val catalog = read(EXPERIMENT_CATALOG)
        assertTrue(catalog.contains("enum class ExperimentId"))
        assertFalse(DOMAIN_TYPED_FIELD.containsMatchIn(catalog), "в каталоге нет строк, чисел и времён")
    }

    @Тогда("^состав каталога меняется только решением продакт-менеджера$")
    fun theCatalogCompositionIsAProductDecision() {
        assertTrue(
            read(EXPERIMENT_CATALOG).contains("продакт-менеджера"),
            "владение составом обязано быть названо",
        )
    }

    // =======================================================================================
    // Поверхности и надёжность (сценарий 12)
    // =======================================================================================

    @Когда("^разработчик смотрит, какие поверхности читают каталог экспериментов$")
    fun theDeveloperLooksAtTheReadersOfTheCatalog() {
        val readers = productionSources().filter { it.readText().contains(EXPERIMENT_PACKAGE) }
        surfaceProblems = readers
            .filterNot { source -> ALLOWED_READERS.any { source.path.contains(it) } }
            .map { it.relativeTo(RepositoryLayout.root).path }
        reliabilityProblems = readers
            .filter { source -> FORBIDDEN_READERS.any { source.path.contains(it) } }
            .map { it.relativeTo(RepositoryLayout.root).path }
    }

    @Тогда("^варианты читают только разрешённые поверхности$")
    fun onlyAllowedSurfacesReadVariants() {
        assertTrue(surfaceProblems.isEmpty(), "варианты читает запрещённая поверхность: $surfaceProblems")
    }

    @Тогда("^планирование, срабатывание и каналы напоминаний вариантов не читают$")
    fun planningAndChannelsDoNotReadVariants() {
        assertTrue(reliabilityProblems.isEmpty(), "путь надёжности читает варианты: $reliabilityProblems")
    }

    @Тогда("^онбординг разрешений вариантов не читает$")
    fun permissionsOnboardingDoesNotReadVariants() {
        val onboarding = productionSources()
            .filter { it.path.contains("/feature-onboarding/") }
            .filter { it.readText().contains(EXPERIMENT_PACKAGE) }
            .map { it.name }
        assertTrue(onboarding.isEmpty(), "онбординг разрешений читает варианты: $onboarding")
    }

    // =======================================================================================
    // Таргетинг и событие (сценарии 13–14)
    // =======================================================================================

    @Когда("^разработчик читает правила таргетинга экспериментов$")
    fun theDeveloperReadsTheTargetingRules() {
        val vendor = read(EXPERIMENT_VENDOR)
        // Только исходники A/B-контура: общий маппинг событий и DI знают домен по своему делу.
        val contour = productionSources().filter { source ->
            source.path.contains("/domain/experiment/") ||
                source.path.contains("/data/experiment/") ||
                source.name in EXPERIMENT_CONTOUR_FILES
        }
        val problems = mutableListOf<String>()
        if (vendor.contains("setCustomAttributes")) problems += "приложение отправляет свои атрибуты"
        if (!vendor.contains("getValueAsString")) problems += "адаптер не читает параметры конфигурации"
        if (contour.any { DOMAIN_WORDS.containsMatchIn(it.readText()) }) {
            problems += "доменные слова в исходниках контура"
        }
        targetingProblems = problems
    }

    @Тогда("^сегменты — локаль, версия, класс устройства и канал магазина$")
    fun theSegmentsAreGeneralOnly() {
        assertTrue(targetingProblems.isEmpty(), "таргетинг нарушен: $targetingProblems")
        val design = read(TDD)
        listOf("локаль", "версия", "класс устройства", "канал магазина").forEach { segment ->
            assertTrue(design.contains(segment), "сегмент «$segment» обязан быть назван")
        }
    }

    @Тогда("^приложение не отправляет собственных атрибутов таргетинга$")
    fun theAppSendsNoCustomAttributes() {
        val offenders = productionSources()
            .filter { it.readText().contains("setCustomAttributes") }
            .map { it.name }
        assertTrue(offenders.isEmpty(), "свои атрибуты таргетинга запрещены: $offenders")
    }

    @Тогда("^медицинские данные в таргетинге не участвуют$")
    fun noMedicalDataIsInvolved() {
        assertTrue(targetingProblems.isEmpty(), "доменные данные в таргетинге: $targetingProblems")
    }

    @Когда("^разработчик читает каталог событий экспериментов$")
    fun theDeveloperReadsTheExperimentEventCatalog() {
        val event = read(EXPERIMENT_EXPOSED)
        val mapping = read(HUAWEI_MAPPING)
        val problems = mutableListOf<String>()
        if (!event.contains("data class ExperimentExposed")) problems += "событие не объявлено"
        if (!event.contains("experiment: ExperimentId")) problems += "нет закрытого experiment_id"
        if (!event.contains("variant: VariantKey")) problems += "нет закрытого variant"
        if (DOMAIN_WORDS.containsMatchIn(event)) problems += "доменные слова в событии"
        if (!mapping.contains("\"experiment_exposed\"")) problems += "маппинг вендора не знает события"
        if (!mapping.contains("\"experiment_id\"") || !mapping.contains("\"variant\"")) {
            problems += "маппинг не называет свойства"
        }
        eventProblems = problems
    }

    @Тогда("^событие экспонирования называет эксперимент и вариант закрытыми значениями$")
    fun theExposureEventUsesClosedValues() {
        assertTrue(eventProblems.isEmpty(), "событие нарушено: $eventProblems")
    }

    @Тогда("^доменных полей в событии нет$")
    fun theEventHasNoDomainFields() {
        assertFalse(DOMAIN_TYPED_FIELD.containsMatchIn(read(EXPERIMENT_EXPOSED)))
        assertTrue(eventProblems.isEmpty(), "событие нарушено: $eventProblems")
    }

    // =======================================================================================
    // Хранилище и гейты сборки (сценарии 15–17)
    // =======================================================================================

    @Когда("^разработчик проверяет хранилище вариантов$")
    fun theDeveloperChecksTheVariantStore() {
        val store = read(EXPERIMENT_STORE)
        val module = read(DATA_MODULE)
        val bindings = read(DATA_BINDINGS)
        val problems = mutableListOf<String>()
        if (!module.contains("sklyanka_experiment_cache")) problems += "файл кэша не отдельный"
        if (store.contains("transfer") || store.contains("settings")) problems += "кэш знает чужие ключи"
        if (!bindings.contains("ExperimentCacheStore")) problems += "кэш не привязан к порту"
        if (!store.contains("\"experiment_\"")) problems += "ключи кэша не названы экспериментом"
        cacheProblems = problems
    }

    @Тогда("^варианты лежат в отдельном файле вне переноса данных$")
    fun theVariantsLiveInASeparateFile() {
        assertTrue(cacheProblems.isEmpty(), "хранилище нарушено: $cacheProblems")
    }

    @Тогда("^ключи переноса и ключи вариантов не пересекаются$")
    fun theTransferKeysAndVariantKeysDoNotIntersect() {
        val transfer = File(RepositoryLayout.root, TRANSFER_PACKAGE).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("\"experiment_\"") || it.readText().contains("ExperimentId") }
            .map { it.name }
            .toList()
        assertTrue(transfer.isEmpty(), "перенос знает ключи вариантов: $transfer")
        assertTrue(cacheProblems.isEmpty(), "хранилище нарушено: $cacheProblems")
    }

    @Когда("^разработчик сверяет белый список сетевых координат$")
    fun theDeveloperChecksTheNetworkCoordinateWhitelist() {
        val rules = read(CONTOUR_RULES)
        assertTrue(
            rules.contains("\"com.huawei.agconnect:agconnect-remoteconfig\""),
            "нет координаты Remote Configuration",
        )
        assertTrue(rules.contains("\"com.huawei.agconnect:agconnect-abtest\""), "нет координаты A/B Testing")
        assertTrue(rules.contains("ALLOWED_COORDINATES"), "белый список не объявлен")
        requiredBlock = Regex("REQUIRED_COORDINATES[^\\n]*\\)").find(rules)?.value.orEmpty()
        assertTrue(requiredBlock.isNotEmpty(), "обязательные координаты не читаются")
    }

    @Тогда("^координаты A/B Testing и Remote Configuration перечислены в белом списке$")
    fun theAbCoordinatesAreWhitelisted() {
        val rules = read(CONTOUR_RULES)
        assertTrue(rules.contains("agconnect-remoteconfig") && rules.contains("agconnect-abtest"))
    }

    @Тогда("^любой сетевой артефакт вне белого списка валит сборку$")
    fun anyNetworkArtifactOutsideTheListFails() {
        val task = read(DEPENDENCY_TASK)
        assertTrue(task.contains("outside the analytics contour white list"))
        assertTrue(task.contains("AnalyticsContourRules::allows"))
    }

    @Тогда("^запреты GMS, Firebase, рекламы и сторонней аналитики сохранены$")
    fun theForbiddenGroupsRemain() {
        val rules = read(CONTOUR_RULES)
        listOf(
            "com.google.android.gms",
            "com.google.firebase",
            "com.google.android.play",
            "com.crashlytics",
            "io.fabric",
            "com.google.mlkit",
        ).forEach { group ->
            assertTrue(rules.contains(group), "запрет группы $group потерян")
        }
    }

    @Когда("^разработчик читает обязательные координаты контура$")
    fun theDeveloperReadsTheRequiredCoordinates() {
        requiredBlock = Regex("REQUIRED_COORDINATES[^\\n]*\\)")
            .find(read(CONTOUR_RULES))?.value.orEmpty()
        assertTrue(requiredBlock.isNotEmpty(), "обязательные координаты не читаются")
    }

    @Тогда("^обязательной координатой остаётся только аналитика$")
    fun onlyAnalyticsStaysRequired() {
        assertTrue(requiredBlock.contains("hianalytics"), "аналитика обязана остаться обязательной")
        assertFalse(requiredBlock.contains("agconnect"), "A/B не должен стать обязательным")
    }

    @Тогда("^отсутствие A/B-сервиса не валит сборку и не мешает приложению$")
    fun theMissingAbServiceDoesNotBreakTheApp() {
        assertFalse(requiredBlock.contains("agconnect"))
        runBlocking {
            contour.vendor.failure = IllegalStateException("no service")
            contour.experiments.refresh()
            contour.experiments.refresh()
        }
        everyExperimentReadsAsBase()
    }

    // =======================================================================================
    // Окружение и вспомогательные проверки
    // =======================================================================================

    private class Contour(
        private val cache: InMemoryExperimentCache = InMemoryExperimentCache(),
        debuggable: Boolean = false,
    ) {
        val consent = MutableStateFlow<AnalyticsConsent>(AnalyticsConsent.NOT_ASKED)
        val sink = RecordingAnalyticsSink()
        val vendor = RecordingExperimentVendor()
        private val analytics = ConsentedAnalytics(sink = sink, debuggable = debuggable, log = SilentLog)
        val experiments = ConsentedExperiments(
            vendor = vendor,
            cache = cache,
            analytics = analytics,
            debuggable = debuggable,
            log = SilentLog,
            timeout = 200.milliseconds,
        )

        private val store = object : AnalyticsConsentStore {
            override fun observe(): Flow<AnalyticsConsent> = consent

            override suspend fun read(): AnalyticsConsent = consent.value

            override suspend fun record(decision: AnalyticsConsentDecision, materialVersion: Int) {
                consent.value = AnalyticsConsent(decision, materialVersion)
            }
        }

        /** Старт приложения: согласие применяется к обоим гейтам, обновление идёт следом. */
        suspend fun start() {
            analytics.applyConsent(consent.value)
            experiments.applyConsent(consent.value)
            experiments.refresh()
        }

        /** Решение принято: запись и применение к обоим гейтам, как в use cases SKL-82. */
        suspend fun grant() {
            store.record(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)
            analytics.applyConsent(consent.value)
            experiments.applyConsent(consent.value)
        }

        /** Решение отозвано: применение и сброс снимка. */
        suspend fun disable() {
            store.record(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL)
            analytics.applyConsent(consent.value)
            experiments.applyConsent(consent.value)
        }

        fun cacheSnapshot(): Map<ExperimentId, VariantKey> = runBlocking { cache.read() }

        fun sharedCache(): InMemoryExperimentCache = cache
    }

    private class RecordingExperimentVendor : ExperimentVendor {
        val calls = AtomicInteger()
        var response: () -> Map<ExperimentId, VariantKey> = { emptyMap() }
        var failure: Throwable? = null
        var hang = false

        override suspend fun fetchVariants(): Map<ExperimentId, VariantKey> {
            calls.incrementAndGet()
            if (hang) delay(Long.MAX_VALUE)
            failure?.let { throw it }
            return response()
        }
    }

    private class InMemoryExperimentCache : ExperimentCacheStore {
        private var stored = emptyMap<ExperimentId, VariantKey>()

        override suspend fun read(): Map<ExperimentId, VariantKey> = stored

        override suspend fun write(variants: Map<ExperimentId, VariantKey>) {
            stored = variants
        }
    }

    private class RecordingAnalyticsSink : AnalyticsSink {
        val events = mutableListOf<AnalyticsEvent>()

        override suspend fun activate() = Unit

        override suspend fun deactivate() = Unit

        override fun send(event: AnalyticsEvent) {
            events += event
        }
    }

    private object SilentLog : AnalyticsLog {
        override fun activated() = Unit

        override fun deactivated() = Unit

        override fun unavailable(reason: String) = Unit

        override fun debugSuppressed() = Unit

        override fun sendFailed(reason: String) = Unit

        override fun experimentsUpdated(count: Int) = Unit

        override fun experimentsUnavailable(reason: String) = Unit
    }

    private fun experimentId(name: String): ExperimentId = ExperimentId.entries
        .firstOrNull { it.name == name }
        ?: error("неизвестный эксперимент «$name»")

    private fun variantKey(name: String): VariantKey = VariantKey.entries
        .firstOrNull { it.name == name }
        ?: error("неизвестный вариант «$name»")

    private fun read(path: String): String = File(RepositoryLayout.root, path).readText()

    private fun productionSources(): List<File> = PRODUCTION_MODULES.flatMap { module ->
        File(RepositoryLayout.root, "$module/src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    private companion object {
        const val CORE_DOMAIN = "core-domain/src/main/kotlin/ru/stohastika/sklyanka/domain"
        const val EXPERIMENT_PACKAGE = "ru.stohastika.sklyanka.domain.experiment"
        const val EXPERIMENT_CATALOG = "$CORE_DOMAIN/experiment/ExperimentCatalog.kt"
        const val EXPERIMENT_EXPOSED = "$CORE_DOMAIN/analytics/ExperimentExposed.kt"

        const val CORE_ANALYTICS = "core-analytics/src/main/kotlin/ru/stohastika/sklyanka/analytics"
        const val EXPERIMENT_VENDOR = "$CORE_ANALYTICS/HuaweiExperimentVendor.kt"
        const val HUAWEI_MAPPING = "$CORE_ANALYTICS/HuaweiEventMapping.kt"

        const val DATA = "core-data/src/main/kotlin/ru/stohastika/sklyanka/data"
        const val EXPERIMENT_STORE = "$DATA/experiment/ExperimentCacheDataStore.kt"
        const val DATA_MODULE = "$DATA/di/DataModule.kt"
        const val DATA_BINDINGS = "$DATA/di/DataBindings.kt"
        const val TRANSFER_PACKAGE = "$DATA/transfer"

        const val BUILD_LOGIC = "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle"
        const val CONTOUR_RULES = "$BUILD_LOGIC/AnalyticsContourRules.kt"
        const val DEPENDENCY_TASK = "$BUILD_LOGIC/VerifyDependencyCompositionTask.kt"

        const val TDD = "docs/tasks/SKL-83/TDD.md"

        /** Исходники A/B-контура в `core-analytics`: адаптер, запуск и Hilt. */
        val EXPERIMENT_CONTOUR_FILES = listOf(
            "ExperimentCodes.kt",
            "HuaweiExperimentVendor.kt",
            "ExperimentsBootstrap.kt",
            "ExperimentsModule.kt",
        )

        val PRODUCTION_MODULES = listOf(
            "app",
            "core-domain",
            "core-data",
            "core-analytics",
            "core-ui",
            "feature-today",
            "feature-meds",
            "feature-reminder",
            "feature-onboarding",
            "feature-history",
            "feature-settings",
        )

        /** Кто имеет право читать каталог: поверхности, контур, событие и кэш. */
        val ALLOWED_READERS = listOf(
            "/core-domain/src/main/kotlin/ru/stohastika/sklyanka/domain/experiment/",
            "/core-domain/src/main/kotlin/ru/stohastika/sklyanka/domain/analytics/ExperimentExposed.kt",
            "/core-data/src/main/kotlin/ru/stohastika/sklyanka/data/experiment/",
            "/core-data/src/main/kotlin/ru/stohastika/sklyanka/data/di/DataBindings.kt",
            "/core-analytics/",
            "/feature-today/",
            "/feature-settings/",
            "/app/src/main/",
        )

        /** Пути надёжности: планирование, срабатывание, каналы, онбординг (П-1). */
        val FORBIDDEN_READERS = listOf(
            "/core-domain/src/main/kotlin/ru/stohastika/sklyanka/domain/reminder/",
            "/core-domain/src/main/kotlin/ru/stohastika/sklyanka/domain/today/",
            "/feature-reminder/",
            "/feature-onboarding/",
            "/feature-meds/",
            "/feature-history/",
        )

        // «Приёмка» и «приёмочный» — не доменные данные: отсекается только слово «приём».
        val DOMAIN_WORDS = Regex(
            """(?i)(medication|intake|profile|reminder|dosage|stock|приверженност|лекарств|приём(?![као]))""",
        )

        /** Поле каталога или события, несущее доменный тип вместо перечисления. */
        val DOMAIN_TYPED_FIELD = Regex(
            """:\s*(String|Int|Long|Float|Double|Boolean|LocalDate|LocalTime|Instant|UUID)""",
        )
    }
}
