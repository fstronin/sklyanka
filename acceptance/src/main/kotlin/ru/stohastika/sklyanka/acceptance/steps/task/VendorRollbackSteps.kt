package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.ConsentOutcome
import ru.stohastika.sklyanka.domain.analytics.NoOpAnalytics
import ru.stohastika.sklyanka.domain.analytics.ScreenName
import java.io.File

/**
 * Шаги автоматической приёмки сворачивания вендора (SKL-100, группа без `@screen`/`@manual`).
 *
 * Структурные сценарии читают файлы: отсутствие A/B и вендора, сохранённый механизм,
 * заглушку в графе, снятые входы согласия, гейты «релиз без сети», заготовки будущих
 * контуров и отложенные сценарии. Поведенческая проверка одна и тривиальная по замыслу:
 * заглушка не отправляет и не накапливает — у неё нет ни sink, ни хранилища.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions", "LargeClass")
class VendorRollbackSteps {

    private var contourText: String = ""
    private var catalog: String = ""
    private var rules: String = ""
    private var manifest: String = ""
    private var analyticsModule: String = ""

    // =======================================================================================
    // A/B убран (сценарий 1)
    // =======================================================================================

    @Когда("^разработчик ищет A/B-часть в исходниках и каталоге версий$")
    fun theDeveloperLooksForTheAbPart() {
        catalog = read(VERSION_CATALOG)
        contourText = productionSources().joinToString("\n") { it.readText() }
    }

    @Тогда("^домена экспериментов в коде нет$")
    fun theExperimentDomainIsGone() {
        assertFalse(File(RepositoryLayout.root, EXPERIMENT_PACKAGE).exists(), "домен экспериментов удалён")
    }

    @Тогда("^события экспонирования в каталоге нет$")
    fun theExposureEventIsGone() {
        assertFalse(File(RepositoryLayout.root, EXPERIMENT_EXPOSED).exists(), "событие экспонирования удалено")
        assertFalse(contourText.contains("ExperimentExposed"), "события нет и в исходниках")
    }

    @Тогда("^координат Remote Configuration и A/B Testing в каталоге версий нет$")
    fun theAbCoordinatesAreGone() {
        assertFalse(catalog.contains("agconnect-remoteconfig"), "координата Remote Configuration убрана")
        assertFalse(catalog.contains("agconnect-abtest"), "координата A/B Testing убрана")
    }

    @Тогда("^точек чтения вариантов на поверхностях нет$")
    fun theVariantReadsAreGone() {
        listOf(TODAY_SCREEN, SETTINGS_SCREEN).forEach { path ->
            val source = read(path)
            assertFalse(source.contains("variantOf"), "$path не читает варианты")
            assertFalse(source.contains("VariantKey"), "$path не знает вариантов")
        }
    }

    // =======================================================================================
    // Вендор аналитики убран (сценарий 2)
    // =======================================================================================

    @Когда("^разработчик ищет вендорскую аналитику в исходниках и каталоге версий$")
    fun theDeveloperLooksForTheVendorAnalytics() {
        catalog = read(VERSION_CATALOG)
        contourText = productionSources().joinToString("\n") { it.readText() }
        analyticsModule = read(ANALYTICS_MODULE)
    }

    @Тогда("^координаты Analytics Kit в каталоге версий нет$")
    fun theAnalyticsCoordinateIsGone() {
        assertFalse(catalog.contains("com.huawei.hms:hianalytics"), "координата Analytics Kit убрана")
    }

    @Тогда("^вендорского приёмника и маппинга событий в коде нет$")
    fun theVendorSinkAndMappingAreGone() {
        listOf(HUAWEI_SINK, HUAWEI_MAPPING, ANALYTICS_BOOTSTRAP).forEach { path ->
            assertFalse(File(RepositoryLayout.root, path).exists(), "$path удалён")
        }
    }

    @Тогда("^ни один продуктовый исходник не импортирует пакеты Huawei$")
    fun noProductSourceImportsHuawei() {
        val offenders = productionSources()
            .filter { it.readText().contains("import com.huawei.") }
            .map { it.name }
        assertEquals(emptyList<String>(), offenders, "пакеты Huawei в продукте")
    }

    @Тогда("^активации вендорского SDK в контуре нет$")
    fun theVendorActivationIsGone() {
        assertFalse(analyticsModule.contains("ConsentedAnalytics("), "граф не активирует вендорский гейт")
        assertFalse(analyticsModule.contains("provideAnalyticsSink"), "граф не биндит sink")
        assertFalse(contourText.contains("HiAnalytics"), "SDK вендора не вызывается")
    }

    // =======================================================================================
    // Механизм и заглушка (сценарии 3–5)
    // =======================================================================================

    @Когда("^разработчик читает домен аналитики$")
    fun theDeveloperReadsTheAnalyticsDomain() {
        contourText = productionSources().joinToString("\n") { it.readText() }
    }

    @Тогда("^порт отправки событий на месте$")
    fun theTrackingPortStays() {
        assertTrue(File(RepositoryLayout.root, ANALYTICS_PORT).isFile, "порт Analytics на месте")
        assertTrue(read(ANALYTICS_PORT).contains("fun track(event: AnalyticsEvent)"), "порт отправляет события")
    }

    @Тогда("^каталог событий на месте$")
    fun theEventCatalogStays() {
        val event = read(ANALYTICS_EVENT)
        assertTrue(event.contains("sealed interface AnalyticsEvent"), "каталог событий на месте")
        assertTrue(event.contains("PaywallShown"), "зарезервированные события платной зоны на месте")
    }

    @Тогда("^хранилище согласия на месте$")
    fun theConsentStoreStays() {
        assertTrue(File(RepositoryLayout.root, CONSENT_STORE).isFile, "хранилище согласия на месте")
        assertTrue(File(RepositoryLayout.root, CONSENT_STORE_IFACE).isFile, "порт хранилища на месте")
    }

    @Тогда("^гейт «до согласия — ни одного события» на месте$")
    fun theConsentGateStays() {
        val gate = read(CONSENTED_ANALYTICS)
        assertTrue(gate.contains("sendingAllowed"), "гейт читает правило согласия")
        assertTrue(gate.contains("if (!allowed) return"), "до разрешения события не уходят")
    }

    @Тогда("^у механизма есть тесты на JVM$")
    fun theMechanismHasJvmTests() {
        listOf(
            "core-domain/src/test/kotlin/ru/stohastika/sklyanka/domain/analytics/ConsentedAnalyticsTest.kt",
            "core-domain/src/test/kotlin/ru/stohastika/sklyanka/domain/analytics/AnalyticsConsentUseCasesTest.kt",
            "core-domain/src/test/kotlin/ru/stohastika/sklyanka/domain/analytics/AnalyticsEventTest.kt",
        ).forEach { path ->
            assertTrue(File(RepositoryLayout.root, path).isFile, "$path на месте")
        }
    }

    @Когда("^разработчик читает сборку аналитического контура$")
    fun theDeveloperReadsTheContourWiring() {
        analyticsModule = read(ANALYTICS_MODULE)
        contourText = productionSources().joinToString("\n") { it.readText() }
    }

    @Тогда("^аналитика отдаётся нулевой заглушкой$")
    fun theAnalyticsIsANoopStub() {
        assertTrue(analyticsModule.contains("NoOpAnalytics"), "граф отдаёт NoOpAnalytics")
    }

    @Тогда("^вендорского канала в графе нет$")
    fun thereIsNoVendorChannel() {
        assertFalse(analyticsModule.contains("Huawei"), "вендорского класса в графе нет")
        assertFalse(analyticsModule.contains("provideAnalyticsSink"), "канала sink в графе нет")
    }

    @Когда("^приложение работает и человек пользуется экранами$")
    fun theAppRunsAndThePersonUsesTheScreens() {
        // Заглушка — единственная реализация порта в релизе: события уходят «в никуда».
        NoOpAnalytics.track(AnalyticsEvent.ScreenView(ScreenName.TODAY))
        NoOpAnalytics.track(AnalyticsEvent.ConsentChoice(ConsentOutcome.ACCEPTED))
    }

    @Тогда("^ни одно событие не передаётся вендору$")
    fun noEventReachesTheVendor() {
        val noop = read(NOOP_ANALYTICS)
        assertFalse(noop.contains("AnalyticsSink"), "у заглушки нет канала")
        assertFalse(contourText.contains("HiAnalytics"), "вендорского канала в продукте нет")
    }

    @Тогда("^события нигде не накапливаются$")
    fun theEventsAreNotStored() {
        val noop = read(NOOP_ANALYTICS)
        listOf("mutableList", "CopyOnWriteArrayList", "DataStore", "preferences").forEach { storage ->
            assertFalse(noop.contains(storage), "заглушка не хранит события ($storage)")
        }
    }

    @Тогда("^сетевых запросов аналитического контура нет$")
    fun thereAreNoNetworkCalls() {
        listOf("java.net", "okhttp", "HttpURLConnection", "com.huawei").forEach { network ->
            assertFalse(contourText.contains(network), "сети в контуре нет ($network)")
        }
    }

    // =======================================================================================
    // Входы согласия сняты (сценарий 8)
    // =======================================================================================

    @Когда("^разработчик смотрит, кто показывает экран согласия$")
    fun theDeveloperLooksForTheConsentEntrances() {
        contourText = read(MAIN_ACTIVITY)
        rules = read(SETTINGS_SCREEN)
    }

    @Тогда("^запуск приложения экран согласия не открывает$")
    fun theLaunchDoesNotOpenTheConsentScreen() {
        assertFalse(contourText.contains("ConsentRoute"), "маршрут запуска не знает экран согласия")
        assertFalse(contourText.contains("consentPending"), "решение запуска не знает согласия")
    }

    @Тогда("^настройки экран согласия не открывают$")
    fun theSettingsDoNotOpenTheConsentScreen() {
        assertFalse(rules.contains("R.string.settings_analytics"), "строка «Аналитика» не рендерится")
        assertFalse(rules.contains("onCheckedChange = { onIntent(SettingsIntent.SetAnalytics"), "переключателя нет")
    }

    @Тогда("^экран согласия остаётся в коде на полке$")
    fun theConsentScreenStaysOnTheShelf() {
        assertTrue(File(RepositoryLayout.root, CONSENT_SCREEN).isFile, "экран согласия сохранён")
        assertTrue(File(RepositoryLayout.root, CONSENT_ROUTE).isFile, "маршрут согласия сохранён")
    }

    // =======================================================================================
    // Гейты и будущее (сценарии 9–10)
    // =======================================================================================

    @Когда("^разработчик сверяет правила состава артефакта$")
    fun theDeveloperChecksTheCompositionRules() {
        rules = read(CONTOUR_RULES)
        manifest = read(MANIFEST)
    }

    @Тогда("^проверка слитого манифеста запрещает INTERNET$")
    fun theManifestCheckForbidsInternet() {
        assertTrue(rules.contains("FORBIDDEN_PERMISSIONS"), "запрещённые разрешения объявлены")
        assertTrue(rules.contains("android.permission.INTERNET"), "INTERNET запрещён")
        assertFalse(manifest.contains("android.permission.INTERNET"), "INTERNET не объявляется")
    }

    @Тогда("^запрет разрешений состояния сети сохранён$")
    fun theNetworkStatePermissionsStayForbidden() {
        assertTrue(rules.contains("ACCESS_NETWORK_STATE"))
        assertTrue(rules.contains("ACCESS_WIFI_STATE"))
        assertTrue(rules.contains("AD_ID"))
    }

    @Тогда("^обязательных координат у контура нет$")
    fun thereAreNoRequiredCoordinates() {
        assertTrue(
            Regex("""REQUIRED_COORDINATES\s*=\s*emptyList""").containsMatchIn(rules),
            "обязательных координат нет",
        )
        assertTrue(
            Regex("""ALLOWED_COORDINATES\s*=\s*emptyList""").containsMatchIn(rules),
            "белый список пуст",
        )
    }

    @Тогда("^запреты GMS, Firebase, рекламы и сторонней аналитики остаются$")
    fun theForbiddenGroupsStay() {
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

    @Тогда("^гейты проверяются в обеих конфигурациях$")
    fun theChecksRunInBothConfigurations() {
        val plugin = read(ARTIFACT_PLUGIN)
        assertTrue(plugin.contains("onVariants"))
        assertTrue(plugin.contains("verifyManifest"))
        assertTrue(plugin.contains("verifyDependencies"))
        assertTrue(plugin.contains("\"debug\""))
        assertTrue(plugin.contains("\"check\""))
    }

    @Когда("^разработчик проверяет заготовки будущих сетевых контуров$")
    fun theDeveloperChecksFutureContours() {
        rules = read(CONTOUR_RULES)
    }

    @Тогда("^репозиторий Huawei в настройках сборки сохранён$")
    fun theHuaweiRepositoryStays() {
        val settings = read(SETTINGS_GRADLE)
        assertTrue(settings.contains("developer.huawei.com/repo/"), "репозиторий вендора сохранён")
        assertTrue(settings.contains("com.huawei.agconnect"), "группы репозитория сохранены")
    }

    @Тогда("^конфигурация вендора по-прежнему вне git$")
    fun theVendorConfigIsStillOutOfGit() {
        assertTrue(read(".gitignore").contains("agconnect-services.json"))
    }

    @Тогда("^путь конфигурации вендора в сборке приложения сохранён$")
    fun theVendorConfigPathStays() {
        val build = read(APP_BUILD)
        assertTrue(build.contains("sklyanka.agconnect.servicesDir"), "путь конфигурации сохранён")
        assertTrue(build.contains("vendor config is absent"), "отсутствие сообщается нейтрально")
    }

    @Тогда("^координаты будущих контуров заранее в белый список не внесены$")
    fun noFutureCoordinatesArePrelisted() {
        assertFalse(rules.contains("com.huawei.hms:"), "координаты HMS заранее не внесены")
        assertFalse(rules.contains("com.huawei.agconnect:"), "координаты AGC заранее не внесены")
    }

    // =======================================================================================
    // Отложенные сценарии (сценарий 11)
    // =======================================================================================

    @Когда("^разработчик запускает сценарную приёмку$")
    fun theDeveloperRunsTheAcceptance() {
        contourText = read(SKL83_FEATURE)
    }

    @Тогда("^сценарии A/B-задачи помечены отложенными$")
    fun theAbScenariosArePostponed() {
        assertTrue(contourText.contains("@postponed"), "сценарии A/B помечены отложенными")
    }

    @Тогда("^шагов A/B-задачи в словаре нет$")
    fun theAbStepsAreGone() {
        assertFalse(
            File(RepositoryLayout.root, EXPERIMENT_STEPS).exists(),
            "шаги A/B-задачи удалены из словаря",
        )
    }

    @Тогда("^отложенные сценарии в автоматический прогон не входят$")
    fun thePostponedScenariosAreExcluded() {
        val build = read(ACCEPTANCE_BUILD)
        assertTrue(build.contains("postponedTag"), "тег отложенных объявлен")
        assertTrue(build.contains("not \$postponedTag"), "автопрогон исключает отложенные")
    }

    // =======================================================================================
    // Окружение
    // =======================================================================================

    private fun read(path: String): String = File(RepositoryLayout.root, path).readText()

    private fun productionSources(): List<File> = PRODUCTION_MODULES.flatMap { module ->
        File(RepositoryLayout.root, "$module/src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    private companion object {
        const val CORE_DOMAIN = "core-domain/src/main/kotlin/ru/stohastika/sklyanka/domain"
        const val ANALYTICS = "$CORE_DOMAIN/analytics"
        const val ANALYTICS_PORT = "$ANALYTICS/Analytics.kt"
        const val ANALYTICS_EVENT = "$ANALYTICS/AnalyticsEvent.kt"
        const val CONSENTED_ANALYTICS = "$ANALYTICS/ConsentedAnalytics.kt"
        const val NOOP_ANALYTICS = "$ANALYTICS/NoOpAnalytics.kt"
        const val CONSENT_STORE_IFACE = "$ANALYTICS/AnalyticsConsentStore.kt"
        const val EXPERIMENT_PACKAGE = "$CORE_DOMAIN/experiment"
        const val EXPERIMENT_EXPOSED = "$ANALYTICS/ExperimentExposed.kt"

        const val CORE_DATA = "core-data/src/main/kotlin/ru/stohastika/sklyanka/data"
        const val CONSENT_STORE = "$CORE_DATA/analytics/AnalyticsConsentDataStore.kt"

        const val CORE_ANALYTICS = "core-analytics/src/main/kotlin/ru/stohastika/sklyanka/analytics"
        const val ANALYTICS_MODULE = "$CORE_ANALYTICS/di/AnalyticsModule.kt"
        const val HUAWEI_SINK = "$CORE_ANALYTICS/HuaweiAnalyticsSink.kt"
        const val HUAWEI_MAPPING = "$CORE_ANALYTICS/HuaweiEventMapping.kt"
        const val ANALYTICS_BOOTSTRAP = "$CORE_ANALYTICS/AnalyticsBootstrap.kt"

        const val APP = "app/src/main/kotlin/ru/stohastika/sklyanka"
        const val MAIN_ACTIVITY = "$APP/MainActivity.kt"
        const val MANIFEST = "app/src/main/AndroidManifest.xml"
        const val APP_BUILD = "app/build.gradle.kts"
        const val SETTINGS_GRADLE = "settings.gradle.kts"
        const val VERSION_CATALOG = "gradle/libs.versions.toml"

        const val SETTINGS = "feature-settings/src/main/kotlin/ru/stohastika/sklyanka/feature/settings"
        const val SETTINGS_SCREEN = "$SETTINGS/SettingsScreen.kt"
        const val TODAY_SCREEN =
            "feature-today/src/main/kotlin/ru/stohastika/sklyanka/feature/today/TodayScreen.kt"
        const val ONBOARDING_CONSENT =
            "feature-onboarding/src/main/kotlin/ru/stohastika/sklyanka/feature/onboarding/consent"
        const val CONSENT_SCREEN = "$ONBOARDING_CONSENT/ConsentScreen.kt"
        const val CONSENT_ROUTE = "$ONBOARDING_CONSENT/ConsentRoute.kt"

        const val BUILD_LOGIC = "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle"
        const val CONTOUR_RULES = "$BUILD_LOGIC/AnalyticsContourRules.kt"
        const val ARTIFACT_PLUGIN = "$BUILD_LOGIC/ArtifactChecksConventionPlugin.kt"

        const val ACCEPTANCE = "acceptance"
        const val ACCEPTANCE_BUILD = "$ACCEPTANCE/build.gradle.kts"
        const val EXPERIMENT_STEPS =
            "$ACCEPTANCE/src/main/kotlin/ru/stohastika/sklyanka/acceptance/steps/task/ExperimentContourSteps.kt"
        const val SKL83_FEATURE = "docs/tasks/SKL-83/acceptance.feature"

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
    }
}
