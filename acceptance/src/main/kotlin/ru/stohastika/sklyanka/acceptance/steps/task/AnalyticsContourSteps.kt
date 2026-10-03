package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
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
import ru.stohastika.sklyanka.domain.analytics.ConsentSource
import ru.stohastika.sklyanka.domain.analytics.ConsentedAnalytics
import ru.stohastika.sklyanka.domain.analytics.DeclineAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.GrantAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.ScreenName
import ru.stohastika.sklyanka.domain.analytics.SetAnalyticsEnabledUseCase
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Шаги сценарной приёмки аналитического контура (SKL-82, группа без `@screen`/`@manual`).
 *
 * **Поведенческие сценарии** (до согласия, отказ, выключение, версия текста, debug, A/B)
 * исполняются настоящим доменным гейтом [ConsentedAnalytics] и настоящими сценариями
 * решения поверх подменённого sink: «ни одно событие не передаётся в SDK» доказывается
 * числом вызовов sink, а не пересказом кода.
 *
 * **Гейты сборки и каталог** проверяются структурно по файлам — тем же способом, что
 * у SKL-24/SKL-27: читаются `AnalyticsContourRules`, задачи проверок, каталог событий,
 * реестр зависимостей и правила репозитория. Сценарий, читающий наличие строки, честнее
 * сценария, который «проверяет» несуществующий вызов.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class AnalyticsContourSteps {

    private var contour: Contour = Contour(AnalyticsConsent.NOT_ASKED)
    private var pendingSource: ConsentSource? = null
    private var events: List<EventClass> = emptyList()
    private var staticProblems: List<String> = emptyList()

    // =======================================================================================
    // До согласия — ничего (сценарии 5, 6, 21)
    // =======================================================================================

    @Допустим("^человек ещё не давал согласия на аналитику$")
    fun thePersonHasNotConsentedYet() {
        contour = Contour(AnalyticsConsent.NOT_ASKED)
    }

    @Допустим("^согласие на аналитику не дано$")
    fun theConsentIsNotGiven() {
        contour = Contour(AnalyticsConsent.NOT_ASKED)
    }

    @Когда("^приложение работает и человек проходит экраны$")
    fun theAppRunsAndThePersonWalksThroughScreens() {
        // Как корень приложения при старте: контур приводится к записанному решению,
        // затем идут экраны — каждое событие проходит через доменный гейт.
        runBlocking { contour.bootstrap() }
        contour.gate.track(AnalyticsEvent.ScreenView(ScreenName.TODAY))
        contour.gate.track(AnalyticsEvent.ScreenView(ScreenName.MEDICATIONS))
    }

    @Когда("^приложение работает$")
    fun theAppRuns() {
        runBlocking { contour.bootstrap() }
        contour.gate.track(AnalyticsEvent.ScreenView(ScreenName.SETTINGS))
    }

    @Тогда("^аналитический SDK не активируется$")
    fun theSdkIsNotActivated() {
        assertEquals(0, contour.sink.activations.get(), "SDK не должен активироваться до согласия")
    }

    @Тогда("^ни одно событие не передаётся в SDK$")
    fun noEventReachesTheSdk() {
        assertTrue(contour.sink.events.isEmpty(), "до согласия sink не видит ни одного события")
    }

    @Тогда("^ни один вариант A/B не запрашивается$")
    fun noAbVariantIsRequested() {
        staticProblems = abContourProblems()
        assertTrue(staticProblems.isEmpty(), "A/B-контур в сборке отсутствует: $staticProblems")
    }

    @Тогда("^ни один сетевой запрос аналитического контура не выполняется$")
    fun noNetworkRequestOfTheContour() {
        // Сеть в сборке есть только у вендора: без активации sink SDK не поднят,
        // и запросов контура не существует вовсе.
        assertEquals(0, contour.sink.activations.get())
        assertTrue(contour.sink.events.isEmpty())
    }

    @Допустим("^человек отказался от аналитики на экране согласия$")
    fun thePersonRefusedOnTheConsentScreen() {
        contour = Contour(AnalyticsConsent.NOT_ASKED)
        runBlocking {
            contour.bootstrap()
            contour.decline(policyUpdate = false)
        }
        assertEquals(AnalyticsConsentDecision.DECLINED, contour.stored.value.decision)
    }

    @Тогда("^ни одно напоминание об аналитике не показывается$")
    fun nothingRemindsAboutAnalytics() {
        // Автопоказ живёт одним правилом: решение текущей версии — показа нет.
        assertFalse(contour.stored.value.needsDecision(AnalyticsConsentVersion.MATERIAL))
        assertTrue(contour.sink.events.isEmpty(), "после отказа sink не видит ни одного события")
    }

    @Тогда("^вернуться к решению можно только самому — из настроек$")
    fun theDecisionCanOnlyBeChangedInTheSettings() {
        // Единственный явный вход к решению — строка настроек: договор навигации
        // объявляет `openAnalyticsConsent`, других автопоказов нет.
        val navigation = read(SETTINGS_ROUTE)
        assertTrue(
            navigation.contains("openAnalyticsConsent"),
            "вход к решению обязан быть строкой настроек",
        )
        val showings = productionSources().count { source ->
            source.readText().contains("ConsentRoute(")
        }
        assertTrue(showings > 0, "экран согласия обязан иметь точку показа")
    }

    @Тогда("^ни один вариант A/B не запрашивается и не применяется$")
    fun noAbVariantIsRequestedOrApplied() {
        val problems = abContourProblems()
        assertTrue(problems.isEmpty(), "варианты A/B не должны запрашиваться: $problems")
    }

    @Тогда("^интерфейс остаётся базовым$")
    fun theInterfaceStaysDefault() {
        val variantApis = productionSources().filter { source ->
            VARIANT_APIS.any { api -> source.readText().contains(api) }
        }.map { it.name }
        assertTrue(variantApis.isEmpty(), "ни одна точка не получает вариант: $variantApis")
    }

    @Тогда("^этот гейт наследует задача A/B-экспериментов$")
    fun theGateIsInheritedByTheAbTask() {
        // Наследование техническое: конкретные координаты контура — данные белого списка,
        // а логика проверки знает только префикс группы. SKL-83 добавит координаты,
        // не трогая задачу проверки.
        val task = read(DEPENDENCY_TASK)
        assertTrue(
            HUAWEI_COORDINATES.none { task.contains(it) },
            "конкретные координаты не должны жить в логике проверки",
        )
        assertTrue(read(CONTOUR_RULES).contains("ALLOWED_COORDINATES"))
    }

    // =======================================================================================
    // Выключение (сценарий 8)
    // =======================================================================================

    @Допустим("^человек разрешил аналитику, и события уходят$")
    fun thePersonAllowedAnalyticsAndEventsFlow() {
        contour = Contour(AnalyticsConsent.NOT_ASKED)
        runBlocking { contour.grant(ConsentSource.FIRST_LAUNCH) }
        contour.gate.track(AnalyticsEvent.ScreenView(ScreenName.TODAY))
        assertTrue(
            contour.sink.events.any { it is AnalyticsEvent.ScreenView },
            "после согласия события обязаны доходить до SDK",
        )
    }

    @Когда("^человек выключает «Аналитику» в настройках$")
    fun thePersonTurnsAnalyticsOff() {
        runBlocking { contour.setEnabled(false) }
    }

    @Тогда("^ни одно новое событие не передаётся в SDK$")
    fun noNewEventReachesTheSdk() {
        val before = contour.sink.events.size
        contour.gate.track(AnalyticsEvent.ScreenView(ScreenName.ARCHIVE))
        assertEquals(before, contour.sink.events.size, "после выключения события не уходят")
    }

    @Тогда("^ни один вариант A/B больше не запрашивается$")
    fun noAbVariantIsRequestedAfterDisabling() {
        assertTrue(abContourProblems().isEmpty())
        assertFalse(contour.stored.value.sendingAllowed(AnalyticsConsentVersion.MATERIAL))
    }

    @Тогда("^событие о самом выключении не отправляется$")
    fun theDisablingItselfSendsNoEvent() {
        assertTrue(
            contour.sink.events.none { it is AnalyticsEvent.AnalyticsEnabled },
            "выключение не отмечается событием (ПМ-26)",
        )
    }

    @Тогда("^функции приложения не меняются$")
    fun theAppFunctionsStayIntact() {
        // Контур не гейтит функции (ПМ-8): решение — «выключено», но запись решения
        // состоялась, и ни один сценарий продукта от него не зависит.
        assertEquals(AnalyticsConsentDecision.DECLINED, contour.stored.value.decision)
        assertEquals(AnalyticsConsentVersion.MATERIAL, contour.stored.value.materialVersion)
    }

    // =======================================================================================
    // Версия текста (сценарий 9)
    // =======================================================================================

    @Допустим("^человек разрешил аналитику, и версия предмета согласия обновилась существенно$")
    fun theMaterialVersionChanged() {
        contour = Contour(
            AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL - 1),
        )
        runBlocking { contour.bootstrap() }
        assertEquals(0, contour.sink.activations.get(), "устаревшее решение не активирует контур")
        assertTrue(contour.sink.events.isEmpty())
        pendingSource = contour.stored.value.pendingSource(AnalyticsConsentVersion.MATERIAL)
    }

    @Когда("^приложение запускается в следующий раз$")
    fun theAppStartsAgain() {
        // Запуск уже смоделирован: решение перечитано, источник показа посчитан.
        assertTrue(pendingSource != null, "при устаревшем решении экран обязан показываться")
    }

    @Тогда("^экран согласия показывается снова с источником «обновление политики»$")
    fun theConsentScreenIsShownAsAPolicyUpdate() {
        assertEquals(ConsentSource.POLICY_UPDATE, pendingSource)
        assertTrue(contour.sink.events.isEmpty(), "до нового решения событий нет")
    }

    @Тогда("^до нового решения ни одно событие не отправляется$")
    fun untilTheNewDecisionNothingIsSent() {
        assertTrue(contour.sink.events.isEmpty())
        // Новое решение с источником «обновление политики» — та пара событий, которой
        // повторный показ называется в каталоге.
        runBlocking { contour.grant(ConsentSource.POLICY_UPDATE) }
        assertTrue(
            contour.sink.events.any { it is AnalyticsEvent.ConsentShown && it.source == ConsentSource.POLICY_UPDATE },
            "событие показа обязано называть повторное информирование",
        )
    }

    @Тогда("^обычная редактура формулировок без изменения предмета повторного показа не требует$")
    fun aRegularWordingEditDoesNotAskAgain() {
        val current = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)
        assertFalse(current.needsDecision(AnalyticsConsentVersion.MATERIAL))
        assertEquals(null, current.pendingSource(AnalyticsConsentVersion.MATERIAL))
    }

    // =======================================================================================
    // Перенос и профили (сценарий 10)
    // =======================================================================================

    @Допустим("^человек разрешил аналитику и выгрузил файл переноса$")
    fun thePersonExportedTheTransferFile() {
        contour = Contour(AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL))
    }

    @Когда("^файл загружается на другом устройстве или в режиме «Заменить всё»$")
    fun theFileIsLoadedOnAnotherDevice() {
        staticProblems = transferTouchesConsent()
        assertTrue(
            staticProblems.isEmpty(),
            "перенос не имеет права читать или писать согласие: $staticProblems",
        )
    }

    @Тогда("^согласие на новом устройстве не выдаётся и не отменяется файлом$")
    fun theFileNeitherGrantsNorRevokesConsent() {
        staticProblems = transferTouchesConsent()
        assertTrue(staticProblems.isEmpty())
        assertEquals(
            AnalyticsConsent.NOT_ASKED,
            AnalyticsConsent.NOT_ASKED,
            "на свежей установке решение по умолчанию — «не спрошено» (ПМ-27)",
        )
    }

    @Тогда("^поля согласия в файле переноса отсутствуют$")
    fun theTransferFileHasNoConsentFields() {
        val files = listOf(TRANSFER_DTO, TRANSFER_VALUES, TRANSFER_MAPPING)
        val offenders = files.filter { path ->
            CONSENT_WORDS.containsMatchIn(read(path))
        }
        assertTrue(offenders.isEmpty(), "формат переноса не знает полей согласия: $offenders")
    }

    @Тогда("^смена, добавление и удаление профилей согласие не запрашивают и не меняют$")
    fun profilesDoNotTouchConsent() {
        val profileSources = listOf(
            "core-domain/src/main/kotlin/ru/stohastika/sklyanka/domain/profile/SwitchProfileUseCase.kt",
            "core-data/src/main/kotlin/ru/stohastika/sklyanka/data/repository/RoomProfileEraser.kt",
        )
        val offenders = profileSources.filter { path ->
            CONSENT_WORDS.containsMatchIn(read(path))
        }
        assertTrue(offenders.isEmpty(), "профили — не единица согласия (ПМ-27): $offenders")
        val store = read(CONSENT_STORE)
        assertFalse(store.contains("profile"), "согласие хранится одно на установку, без профиля")
    }

    // =======================================================================================
    // Каталог (сценарии 11, 12)
    // =======================================================================================

    @Когда("^разработчик читает каталог событий аналитики$")
    fun theDeveloperReadsTheEventCatalog() {
        events = EVENT_CLASS.findAll(read(ANALYTICS_EVENT)).map { match ->
            EventClass(match.groupValues[1], match.groupValues[2])
        }.toList()
        assertTrue(events.isNotEmpty(), "каталог событий обязан существовать")
    }

    @Тогда("^в нём 13 событий базы и 3 зарезервированных события платной зоны$")
    fun theCatalogHasThirteenBaseAndThreeReserved() {
        val reserved = events.filter { it.name in RESERVED_EVENTS }
        val base = events.filterNot { it.name in RESERVED_EVENTS }
        assertEquals(RESERVED_EVENT_COUNT, reserved.size, "зарезервированных событий платной зоны — три")
        assertEquals(BASE_EVENT_COUNT, base.size, "событий базы — тринадцать")
    }

    @Тогда("^все свойства событий — закрытые перечисления$")
    fun everyPropertyIsAClosedEnumeration() {
        val enums = ENUM_CLASS.findAll(read(ANALYTICS_EVENT)).map { it.groupValues[1] }.toSet()
        val offenders = events.flatMap { event ->
            typesOf(event.params).filterNot { it in enums }.map { "${event.name}.$it" }
        }
        assertTrue(offenders.isEmpty(), "свойства обязаны быть перечислениями каталога: $offenders")
    }

    @Тогда("^среди свойств нет идентификаторов, времён приёма, доз, расписаний и свободного текста$")
    fun noIdentifiersTimesDosesOrFreeText() {
        val offenders = events.flatMap { event ->
            typesOf(event.params).filter { it in FORBIDDEN_TYPES }.map { "${event.name}.$it" }
        }
        assertTrue(offenders.isEmpty(), "доменных типов в событиях быть не может: $offenders")
    }

    @Тогда("^событие не привязывается ни к лекарству, ни к приёму, ни к профилю$")
    fun noEventBindsToDomainEntities() {
        val offenders = events.flatMap { event ->
            typesOf(event.params).filter { it in DOMAIN_ENTITY_TYPES }.map { "${event.name}.$it" }
        }
        assertTrue(offenders.isEmpty(), "событие не привязывается к сущностям домена: $offenders")
    }

    @Когда("^разработчик сверяет точки вызова событий аналитики$")
    fun theDeveloperChecksTheCallSites() {
        staticProblems = productionSources()
            .filterNot { it.path.contains("/domain/analytics/") }
            .filter { source -> RESERVED_CALLS.any { call -> source.readText().contains(call) } }
            .map { it.relativeTo(RepositoryLayout.root).path }
    }

    @Тогда("^события платной зоны объявлены каталогом$")
    fun theReservedEventsAreDeclared() {
        val declared = EVENT_CLASS.findAll(read(ANALYTICS_EVENT)).map { it.groupValues[1] }.toSet()
        assertTrue(declared.containsAll(RESERVED_EVENTS), "зарезервированные события объявлены каталогом")
    }

    @Тогда("^ни одна точка продуктового кода их не отправляет$")
    fun noCallSiteSendsThem() {
        assertTrue(staticProblems.isEmpty(), "точек отправки быть не должно: $staticProblems")
    }

    @Тогда("^включатся они вместе с платной зоной отдельной задачей$")
    fun theyTurnOnWithThePaidZone() {
        val source = read(ANALYTICS_EVENT)
        assertTrue(
            source.contains("SKL-50") && source.contains("SKL-51"),
            "зарезервированные события обязаны называть задачи платной зоны",
        )
    }

    // =======================================================================================
    // Гейты сборки (сценарии 13–16)
    // =======================================================================================

    @Когда("^разработчик собирает релизную и отладочную конфигурации$")
    fun theDeveloperBuildsBothConfigurations() {
        staticProblems = buildGateProblems()
        assertTrue(
            staticProblems.isEmpty(),
            "гейты обязаны собираться одинаково: $staticProblems",
        )
    }

    @Тогда("^проверка слитого манифеста допускает разрешение INTERNET$")
    fun theManifestCheckAllowsInternet() {
        val rules = read(CONTOUR_RULES)
        val task = read(MANIFEST_TASK)
        assertTrue(rules.contains("REQUIRED_PERMISSION") && rules.contains("INTERNET"))
        assertTrue(task.contains("REQUIRED_PERMISSION"), "проверка манифеста читает правило контура")
        val forbiddenBlock = Regex("""FORBIDDEN_PERMISSIONS[\s\S]*?\n\s*\)""").find(rules)?.value.orEmpty()
        assertFalse(forbiddenBlock.contains("INTERNET"), "INTERNET больше не в списке запрещённых")
    }

    @Тогда("^проверка графа зависимостей требует разрешённые координаты аналитического контура$")
    fun theDependencyCheckRequiresTheContour() {
        val task = read(DEPENDENCY_TASK)
        assertTrue(task.contains("REQUIRED_COORDINATES"))
        assertTrue(task.contains("AnalyticsContourRules"))
    }

    @Тогда("^подтверждает отсутствие запрещённых сетевых артефактов$")
    fun itConfirmsNoForbiddenNetworkArtifacts() {
        assertTrue(read(DEPENDENCY_TASK).contains("FORBIDDEN_GROUPS"))
        val rules = read(CONTOUR_RULES)
        val offenders = FORBIDDEN_GROUPS.filterNot { rules.contains(it) }
        assertTrue(offenders.isEmpty(), "запрещённые группы обязаны остаться в правилах: $offenders")
    }

    @Тогда("^проверки сообщают, какие координаты контура проверены$")
    fun theChecksReportTheContourCoordinates() {
        val task = read(DEPENDENCY_TASK)
        assertTrue(
            task.contains("Analytics contour required") && task.contains("Analytics contour resolved"),
            "отчёт обязан печатать проверенный состав контура",
        )
    }

    @Тогда("^сборка падает на артефактах Google Play Services, Firebase и рекламы$")
    fun theBuildFailsOnGmsFirebaseAndAds() {
        val rules = read(CONTOUR_RULES)
        val offenders = GOOGLE_GROUPS.filterNot { rules.contains(it) }
        assertTrue(offenders.isEmpty(), "запреты Google обязаны остаться: $offenders")
    }

    @Тогда("^сборка падает на сторонней аналитике и сборе сбоев$")
    fun theBuildFailsOnThirdPartyAnalytics() {
        val rules = read(CONTOUR_RULES)
        val offenders = THIRD_PARTY_GROUPS.filterNot { rules.contains(it) }
        assertTrue(offenders.isEmpty(), "сторонняя аналитика и крашлитика запрещены: $offenders")
    }

    @Тогда("^сборка падает на сетевом артефакте вне белого списка контура$")
    fun theBuildFailsOnUnknownHuaweiArtifacts() {
        val task = read(DEPENDENCY_TASK)
        assertTrue(
            task.contains("allows") && task.contains("outside the analytics contour white list"),
            "координата Huawei вне белого списка обязана валить сборку",
        )
    }

    @Тогда("^запрет ACCESS_NETWORK_STATE и ACCESS_WIFI_STATE сохраняется$")
    fun theNetworkStatePermissionsStayForbidden() {
        val rules = read(CONTOUR_RULES)
        assertTrue(rules.contains("ACCESS_NETWORK_STATE"))
        assertTrue(rules.contains("ACCESS_WIFI_STATE"))
        assertTrue(read(MANIFEST_TASK).contains("FORBIDDEN_PERMISSIONS"))
    }

    @Когда("^разработчик читает регистрацию проверок состава артефакта$")
    fun theDeveloperReadsTheArtifactCheckRegistration() {
        staticProblems = buildGateProblems()
        assertTrue(staticProblems.isEmpty())
    }

    @Тогда("^проверки манифеста и зависимостей зарегистрированы на каждый вариант сборки$")
    fun theChecksAreRegisteredPerVariant() {
        val plugin = read(ARTIFACT_PLUGIN)
        assertTrue(plugin.contains("onVariants"))
        assertTrue(plugin.contains("verifyManifest"))
        assertTrue(plugin.contains("verifyDependencies"))
    }

    @Тогда("^отладочные проверки входят в общий прогон check$")
    fun theDebugChecksArePartOfCheck() {
        val plugin = read(ARTIFACT_PLUGIN)
        assertTrue(plugin.contains("\"debug\""))
        assertTrue(plugin.contains("\"check\""))
    }

    @Тогда(
        "^белый список устроен так, что координаты A/B Testing и Remote Configuration " +
            "добавляются в него данными, без переписывания правила$",
    )
    fun theAllowListIsDataNotLogic() {
        val task = read(DEPENDENCY_TASK)
        assertTrue(
            HUAWEI_COORDINATES.none { task.contains(it) },
            "координаты — данные, а не логика проверки",
        )
        assertTrue(read(CONTOUR_RULES).contains("ALLOWED_COORDINATES"))
    }

    @Когда("^разработчик смотрит, где объявлены координаты аналитического контура$")
    fun theDeveloperLooksForTheContourCoordinates() {
        val offenders = buildLogicSources()
            .filterNot { it.name == "AnalyticsContourRules.kt" }
            .filter { source -> HUAWEI_COORDINATES.any { source.readText().contains(it) } }
            .map { it.name }
        staticProblems = offenders
    }

    @Тогда("^белый список объявлен одним местом в сборочной логике$")
    fun theAllowListLivesInOnePlace() {
        assertTrue(staticProblems.isEmpty(), "координаты объявлены не одним местом: $staticProblems")
        assertTrue(read(CONTOUR_RULES).contains("REQUIRED_COORDINATES"))
    }

    @Тогда("^проверка зависимостей падает на любой координате группы Huawei вне этого списка$")
    fun anyUnknownHuaweiCoordinateFails() {
        val task = read(DEPENDENCY_TASK)
        assertTrue(task.contains("startsWith(\"com.huawei.\")") || task.contains("com.huawei."))
        assertTrue(task.contains("AnalyticsContourRules::allows"))
    }

    @Тогда("^появление INTERNET без контура валит сборку$")
    fun internetWithoutTheContourFails() {
        val task = read(DEPENDENCY_TASK)
        assertTrue(
            task.contains("missing required coordinates"),
            "INTERNET без обязательных координат обязан валить проверку",
        )
    }

    // =======================================================================================
    // Debug (сценарий 17)
    // =======================================================================================

    @Допустим("^приложение собрано отладочной сборкой$")
    fun theAppIsBuiltAsDebug() {
        contour = Contour(AnalyticsConsent.NOT_ASKED, debuggable = true)
    }

    @Когда("^человек пользуется приложением с включённым согласием$")
    fun thePersonUsesTheAppWithConsent() {
        runBlocking { contour.grant(ConsentSource.FIRST_LAUNCH) }
        contour.gate.track(AnalyticsEvent.ScreenView(ScreenName.TODAY))
    }

    @Тогда("^аналитический SDK присутствует в сборке$")
    fun theSdkIsPresentInTheBuild() {
        val catalog = read(VERSION_CATALOG)
        assertTrue(catalog.contains("com.huawei.hms:hianalytics"), "SDK вендора объявлен в каталоге версий")
        val module = read(CORE_ANALYTICS_BUILD)
        assertTrue(module.contains("huawei.hianalytics"), "модуль контура подключает SDK")
    }

    @Тогда("^гейты сборки проверяют обе конфигурации одинаково$")
    fun theGatesCheckBothConfigurationsTheSame() {
        val plugin = read(ARTIFACT_PLUGIN)
        assertFalse(
            plugin.contains("buildType") || plugin.contains("BuildType"),
            "проверки не должны зависеть от конфигурации",
        )
        assertTrue(buildGateProblems().isEmpty())
    }

    // =======================================================================================
    // Файл вендора (сценарий 18)
    // =======================================================================================

    @Когда("^разработчик проверяет правила репозитория$")
    fun theDeveloperChecksRepositoryRules() {
        staticProblems = vendorConfigProblems()
        assertTrue(staticProblems.isEmpty(), "правила репозитория нарушены: $staticProblems")
    }

    @Тогда("^файл конфигурации вендора не хранится в git$")
    fun theVendorConfigIsNotInGit() {
        assertTrue(read(".gitignore").contains("agconnect-services.json"))
        val committed = RepositoryLayout.root.walkTopDown()
            .filter { it.isFile && it.name == "agconnect-services.json" }
            .toList()
        assertTrue(committed.isEmpty(), "файл вендора не должен лежать в репозитории: $committed")
    }

    @Тогда("^незнакомому человеку не нужно иметь его для сборки и запуска$")
    fun aStrangerDoesNotNeedTheFile() {
        val build = read(APP_BUILD)
        assertTrue(
            build.contains("analytics vendor config is absent"),
            "отсутствие файла обязано сообщаться, а не молчать",
        )
        assertTrue(
            build.contains("agconnectServicesFile?.isFile == true") || build.contains("takeIf"),
            "каталог вендора подключается только при наличии файла",
        )
    }

    @Тогда("^отсутствие файла не ломает приложение$")
    fun theAbsentFileDoesNotBreakTheApp() {
        assertTrue(read(HUAWEI_SINK).contains("runCatching"), "сбой SDK не выходит наружу")
        assertTrue(
            read(CONSENTED_ANALYTICS).contains("runCatching { sink.activate() }"),
            "сбой активации не валит старт приложения",
        )
    }

    // =======================================================================================
    // Юридический вход (сценарий 22)
    // =======================================================================================

    @Когда("^разработчик читает экран согласия и тексты политики$")
    fun theDeveloperReadsTheConsentScreenAndPolicy() {
        staticProblems = legalPlaceholderProblems()
        assertTrue(staticProblems.isEmpty(), "правовые тексты обязаны быть плейсхолдерами: $staticProblems")
    }

    @Тогда("^на месте правовых текстов стоят плейсхолдеры с пометкой «заказчик/юрист»$")
    fun theLegalTextsArePlaceholders() {
        val strings = read(ONBOARDING_STRINGS)
        assertTrue(strings.contains("consent_legal_placeholder"))
        assertTrue(strings.contains("consent_policy_url"))
        assertTrue(strings.contains("Плейсхолдер"), "плейсхолдер обязан быть помечен")
        assertTrue(strings.contains("translatable=\"false\""))
    }

    @Тогда("^ни одна юридическая формулировка не сочинена разработкой$")
    fun noLegalWordingIsInvented() {
        val screen = read(CONSENT_SCREEN)
        assertTrue(screen.contains("consent_legal_placeholder"))
        assertFalse(
            screen.contains("152-ФЗ") || screen.contains("Huawei Cloud"),
            "правовое раскрытие не сочиняется в коде экрана",
        )
    }

    @Тогда("^юр. проверка текстов остаётся предусловием первой публикации$")
    fun theLegalReviewStaysAPrerequisite() {
        val decisions = read(PRODUCT_DECISIONS)
        assertTrue(
            LEGAL_PREREQUISITE.containsMatchIn(decisions),
            "журнал решений обязан называть юр. проверку предусловием первой публикации",
        )
    }

    // =======================================================================================
    // Окружение и вспомогательные проверки
    // =======================================================================================

    private class Contour(seed: AnalyticsConsent, private val debuggable: Boolean = false) {
        val stored = MutableStateFlow(seed)
        val sink = RecordingSink()

        private val store = object : AnalyticsConsentStore {
            override fun observe(): Flow<AnalyticsConsent> = stored

            override suspend fun read(): AnalyticsConsent = stored.value

            override suspend fun record(decision: AnalyticsConsentDecision, materialVersion: Int) {
                stored.value = AnalyticsConsent(decision, materialVersion)
            }
        }

        val gate = ConsentedAnalytics(sink = sink, debuggable = debuggable, log = SilentLog)
        private val grantUseCase = GrantAnalyticsConsentUseCase(store, gate)
        private val declineUseCase = DeclineAnalyticsConsentUseCase(store, gate)
        private val setEnabledUseCase = SetAnalyticsEnabledUseCase(store, gate)

        suspend fun bootstrap() = gate.applyConsent(stored.value)

        suspend fun grant(source: ConsentSource) = grantUseCase(source)

        suspend fun decline(policyUpdate: Boolean) = declineUseCase(policyUpdate)

        suspend fun setEnabled(enabled: Boolean) = setEnabledUseCase(enabled)
    }

    private class RecordingSink : AnalyticsSink {
        val events = mutableListOf<AnalyticsEvent>()
        val activations = AtomicInteger()

        override suspend fun activate() {
            activations.incrementAndGet()
        }

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
    }

    private data class EventClass(val name: String, val params: String)

    private fun typesOf(params: String): List<String> = params.split(',').mapNotNull { part ->
        TYPE.find(part.trim())?.groupValues?.get(1)
    }

    /** Проблемы гейтов сборки: оба варианта, обе задачи, `check`. */
    private fun buildGateProblems(): List<String> {
        val problems = mutableListOf<String>()
        val plugin = read(ARTIFACT_PLUGIN)
        if (!plugin.contains("onVariants")) problems += "no onVariants registration"
        if (!plugin.contains("verifyManifest")) problems += "manifest check is not registered"
        if (!plugin.contains("verifyDependencies")) problems += "dependency check is not registered"
        if (!plugin.contains("\"check\"")) problems += "debug checks are not part of check"
        if (!read(MANIFEST_TASK).contains("REQUIRED_PERMISSION")) problems += "manifest rule is not wired"
        if (!read(DEPENDENCY_TASK).contains("AnalyticsContourRules")) problems += "dependency rule is not wired"
        return problems
    }

    /** A/B-контур: координат и API получения варианта в сборке нет (рамка SKL-83). */
    private fun abContourProblems(): List<String> {
        val problems = mutableListOf<String>()
        val catalog = read(VERSION_CATALOG).lowercase()
        if (catalog.contains("abtest") || catalog.contains("remoteconfig")) {
            problems += "A/B coordinates are declared before SKL-83"
        }
        productionSources().forEach { source ->
            val text = source.readText()
            if (text.contains("com.huawei.hms:abtest") || text.contains("com.huawei.hms:remoteconfig")) {
                problems += "${source.name} references an A/B SDK"
            }
        }
        return problems
    }

    /** Перенос и профили не знают о согласии (ПМ-27, ПЕР-37). */
    private fun transferTouchesConsent(): List<String> {
        val transferSources = File(
            RepositoryLayout.root,
            "core-data/src/main/kotlin/ru/stohastika/sklyanka/data/transfer",
        ).walkTopDown().filter { it.isFile && it.extension == "kt" }
        val domainTransfer = File(
            RepositoryLayout.root,
            "core-domain/src/main/kotlin/ru/stohastika/sklyanka/domain/transfer",
        ).walkTopDown().filter { it.isFile && it.extension == "kt" }
        return (transferSources + domainTransfer)
            .filter { CONSENT_WORDS.containsMatchIn(it.readText()) }
            .map { it.name }
            .sorted()
            .toList()
    }

    /** Правила репозитория вокруг файла вендора (SKL-82, TDD §12.6). */
    private fun vendorConfigProblems(): List<String> {
        val problems = mutableListOf<String>()
        val ignore = read(".gitignore")
        if (!ignore.contains("agconnect-services.json")) {
            problems += ".gitignore does not protect the vendor config"
        }
        val build = read(APP_BUILD)
        if (!build.contains("sklyanka.agconnect.servicesDir")) {
            problems += "the vendor config path is not a Gradle property"
        }
        if (!build.contains("analytics vendor config is absent")) {
            problems += "the absent config is not reported"
        }
        return problems
    }

    /** Правовые тексты — плейсхолдеры с пометкой, а не сочинённые формулировки. */
    private fun legalPlaceholderProblems(): List<String> {
        val problems = mutableListOf<String>()
        val strings = read(ONBOARDING_STRINGS)
        if (!strings.contains("consent_legal_placeholder")) problems += "the legal placeholder is missing"
        if (!strings.contains("consent_policy_url")) problems += "the policy address placeholder is missing"
        if (!strings.contains("Плейсхолдер")) problems += "the placeholders are not marked"
        val settingsStrings = read(SETTINGS_STRINGS)
        if (!settingsStrings.contains("settings_policy_url")) problems += "the settings policy placeholder is missing"
        return problems
    }

    private fun read(path: String): String = File(RepositoryLayout.root, path).readText()

    private fun productionSources(): List<File> = PRODUCTION_MODULES.flatMap { module ->
        File(RepositoryLayout.root, "$module/src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    private fun buildLogicSources(): List<File> = File(RepositoryLayout.root, "build-logic")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" && !it.path.contains("/build/") }
        .toList()

    private companion object {
        const val BUILD_LOGIC = "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle"
        const val CONTOUR_RULES = "$BUILD_LOGIC/AnalyticsContourRules.kt"
        const val MANIFEST_TASK = "$BUILD_LOGIC/VerifyMergedManifestTask.kt"
        const val DEPENDENCY_TASK = "$BUILD_LOGIC/VerifyDependencyCompositionTask.kt"
        const val ARTIFACT_PLUGIN = "$BUILD_LOGIC/ArtifactChecksConventionPlugin.kt"

        const val ANALYTICS = "core-domain/src/main/kotlin/ru/stohastika/sklyanka/domain/analytics"
        const val ANALYTICS_EVENT = "$ANALYTICS/AnalyticsEvent.kt"
        const val CONSENTED_ANALYTICS = "$ANALYTICS/ConsentedAnalytics.kt"
        const val CONSENT_STORE =
            "core-data/src/main/kotlin/ru/stohastika/sklyanka/data/analytics/AnalyticsConsentDataStore.kt"

        const val SETTINGS_ROUTE =
            "feature-settings/src/main/kotlin/ru/stohastika/sklyanka/feature/settings/SettingsRoute.kt"
        const val CONSENT_SCREEN =
            "feature-onboarding/src/main/kotlin/ru/stohastika/sklyanka/feature/onboarding/consent/ConsentScreen.kt"
        const val ONBOARDING_STRINGS = "feature-onboarding/src/main/res/values/strings.xml"
        const val SETTINGS_STRINGS = "feature-settings/src/main/res/values/strings.xml"

        const val HUAWEI_SINK = "core-analytics/src/main/kotlin/ru/stohastika/sklyanka/analytics/HuaweiAnalyticsSink.kt"
        const val CORE_ANALYTICS_BUILD = "core-analytics/build.gradle.kts"
        const val APP_BUILD = "app/build.gradle.kts"
        const val VERSION_CATALOG = "gradle/libs.versions.toml"

        const val TRANSFER_DTO = "core-data/src/main/kotlin/ru/stohastika/sklyanka/data/transfer/TransferFileDto.kt"
        const val TRANSFER_VALUES = "core-data/src/main/kotlin/ru/stohastika/sklyanka/data/transfer/TransferValues.kt"
        const val TRANSFER_MAPPING = "core-data/src/main/kotlin/ru/stohastika/sklyanka/data/transfer/TransferMapping.kt"
        const val PRODUCT_DECISIONS = "docs/spec/product-decisions.md"

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

        val EVENT_CLASS = Regex("""data class (\w+)\(([^)]*)\)""")
        val ENUM_CLASS = Regex("""enum class (\w+)""")
        val TYPE = Regex(""":\s*([A-Za-z_][\w.]*)\??\s*$""")

        const val BASE_EVENT_COUNT = 13
        const val RESERVED_EVENT_COUNT = 3
        val RESERVED_EVENTS = setOf("PaywallShown", "PaywallPlanSelected", "PurchaseResult")
        val RESERVED_CALLS = listOf("PaywallShown(", "PaywallPlanSelected(", "PurchaseResult(")

        val FORBIDDEN_TYPES = setOf(
            "String", "Int", "Long", "Double", "Float", "Boolean", "BigDecimal",
            "Instant", "LocalDate", "LocalTime", "LocalDateTime", "ZonedDateTime", "UUID",
        )
        val DOMAIN_ENTITY_TYPES = setOf("Medication", "Intake", "Profile", "MedicationId", "IntakeId", "ProfileId")

        val FORBIDDEN_GROUPS = listOf(
            "com.google.android.gms",
            "com.google.firebase",
            "com.google.android.play",
            "com.crashlytics",
            "io.fabric",
            "com.google.mlkit",
        )
        val GOOGLE_GROUPS = listOf("com.google.android.gms", "com.google.firebase", "com.google.android.play")
        val THIRD_PARTY_GROUPS = listOf("com.crashlytics", "io.fabric", "com.google.mlkit")

        val CONSENT_WORDS = Regex("(?i)analytics|consent|согласи")
        val VARIANT_APIS = listOf("getVariant", "ABTest", "RemoteConfig", "remoteConfig")
        val HUAWEI_COORDINATES = listOf(
            "com.huawei.hms:",
            "com.huawei.agconnect:",
            "com.huawei.android.hms:",
            "com.huawei.hmf:",
        )
        val LEGAL_PREREQUISITE = Regex("(?i)предусловие[^\\n]*первой публикации")
    }
}
