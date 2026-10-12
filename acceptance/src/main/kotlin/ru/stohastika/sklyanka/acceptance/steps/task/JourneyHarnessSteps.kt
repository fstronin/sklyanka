package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import java.io.File

/**
 * Стражи набора сквозных походов (SKL-140, Р-58, ADR 0014).
 *
 * Поход — сквозной проход человека по приложению на настоящей платформе; сам он живёт
 * в `app/src/androidTest` и исполняется группой `journey` оснастки эмулятора. Здесь
 * держится то, что походом не проверяется: **честность реестра**. Три утверждения,
 * каждое из которых без стража превратилось бы в обещание:
 *
 * 1. **Ни один поход не потерян и не выдуман.** Таблица реестра (`docs/tasks/SKL-140/TDD.md`
 *    §6) и каталог классов сверяются в обе стороны, и на каждую строку реестра приходится
 *    свой сценарий в приёмке.
 * 2. **Ни одна поверхность не осталась без ответа.** Перечень поверхностей собирается
 *    обходом перечислений навигации и манифеста — то есть из кода, а не из документа, —
 *    и каждая поверхность обязана быть названа строкой реестра или строкой «вне набора»
 *    с причиной. Новый экран, заведённый без строки, валит прогон.
 * 3. **Походы исполняются, а не лежат текстом.** Группа `journey` объявлена в оснастке,
 *    походы исключены из экранной и общей групп, у группы свой отчёт, каждая проверка
 *    набора оставляет кадр и не пользуется запрещёнными приёмами.
 *
 * Сценарий SKL-00 про перечень экранов заказчика реализуется здесь же: карта экранов
 * `docs/norm/04-behavior.md` §5.1 — действующий норматив, на который ссылается реестр,
 * и проверять её отдельным списком значило бы держать два ответа на один вопрос.
 *
 * Разбор текста намеренно простой (строки и отметки «`»): стражи читают документы, которые
 * ведёт человек, и обязаны падать с понятным сообщением, а не угадывать разметку.
 */
@Suppress("TooManyFunctions", "LargeClass")
class JourneyHarnessSteps {

    private var registry = ""
    private var journeySources: List<File> = emptyList()
    private var configuration = ""
    private var runTests = ""
    private var runJourneys = ""
    private var screenMap: List<List<String>> = emptyList()
    private var originalScreens: List<String> = emptyList()

    // --- Реестр походов ---------------------------------------------------------

    @Когда("участник открывает реестр походов рабочей спецификации")
    fun theReaderOpensTheJourneyRegistry() {
        loadRegistry()
        loadJourneySources()
    }

    @Тогда("каждый поход реестра назван хотя бы одним классом инструментальной проверки")
    fun everyJourneyNamesAClass() {
        val named = registryClasses()
        assertTrue(named.isNotEmpty(), "the journey registry must name check classes")
        val missing = named.filterNot { name -> journeySources.any { it.nameWithoutExtension == name } }
        assertEquals(
            emptyList<String>(),
            missing,
            "these journeys name a class that does not exist under $JOURNEY_DIR",
        )
    }

    @Тогда("каждый класс похода назван в реестре инструментальных проверок")
    fun everyClassIsNamedInTheInstrumentalRegistry() {
        val design = text(SKL24_DESIGN)
        val missing = journeySources.map { it.name }.filterNot { design.contains(it) }
        assertEquals(
            emptyList<String>(),
            missing,
            "every file under $JOURNEY_DIR must be named in $SKL24_DESIGN",
        )
    }

    @Тогда("в каталоге походов нет класса, не названного ни одной строкой реестра")
    fun noClassStaysOutOfTheRegistry() {
        val named = registryClasses().toSet()
        val stray = journeySources.map { it.nameWithoutExtension }.filterNot { it in named }
        assertEquals(emptyList<String>(), stray, "these classes are not named by any journey row")
    }

    @Когда("участник открывает исходники классов походов")
    fun theReaderOpensJourneySources() {
        loadJourneySources()
        loadRegistry()
    }

    /** Рабочая спецификация с реестром походов: читается один раз на сценарий. */
    private fun loadRegistry() {
        registry = text(JOURNEY_DESIGN)
        assertTrue(
            registry.contains(JOURNEY_SECTION),
            "$JOURNEY_DESIGN must keep the journey registry section",
        )
    }

    /** Классы походов: читаются один раз на сценарий, вместе с реестром (§6 и §7). */
    private fun loadJourneySources() {
        journeySources = journeyFiles()
        assertTrue(journeySources.isNotEmpty(), "$JOURNEY_DIR must hold the journey classes")
    }

    @Тогда("каждый класс похода оставляет кадр состояния")
    fun everyJourneyLeavesAFrame() {
        val withoutFrame = journeySources.filterNot { it.readText().contains(CAPTURE_CALL) }.map { it.name }
        assertEquals(emptyList<String>(), withoutFrame, "these journeys leave no frame")
    }

    @Тогда("ни один класс похода не спит и не читает системные часы")
    fun noJourneySleepsOrReadsTheClock() {
        journeySources.forEach { file ->
            val body = file.readText()
            FORBIDDEN_IN_JOURNEYS.forEach { forbidden ->
                assertFalse(
                    body.contains(forbidden),
                    "${file.name} must not use $forbidden: journeys run on managed time",
                )
            }
        }
    }

    @Тогда("ни один класс похода не запускает экран через ActivityScenario")
    fun noJourneyUsesActivityScenario() {
        val users = journeySources.filter { it.readText().contains(ACTIVITY_SCENARIO) }.map { it.name }
        assertEquals(emptyList<String>(), users, "journeys must launch the screen the product way")
    }

    // --- Группа прогона ---------------------------------------------------------

    @Когда("участник открывает оснастку эмулятора")
    fun theReaderOpensTheHarness() {
        configuration = text(CONFIG_SCRIPT)
        runTests = text(RUN_TESTS_SCRIPT)
        runJourneys = text(RUN_JOURNEYS_SCRIPT)
    }

    @Тогда("группа походов названа в ней отдельно от экранной группы")
    fun theJourneyGroupIsNamed() {
        val journeyPackage = valueOf(configuration, JOURNEY_PACKAGES_KEY)
        assertTrue(journeyPackage.isNotBlank(), "$JOURNEY_PACKAGES_KEY must declare the journey package")
        assertTrue(
            journeyPackage.startsWith(valueOf(configuration, UI_PACKAGES_KEY)),
            "journey files live inside the screen checks package: приёмка SKL-96 требует этого",
        )
        assertTrue(runTests.contains(JOURNEY_GROUP_CASE), "$RUN_TESTS_SCRIPT must know the journey group")
        assertTrue(
            runTests.contains(JOURNEY_PACKAGES_KEY),
            "the journey group must take its package from $CONFIG_SCRIPT",
        )
    }

    @Тогда("у группы походов есть свой отчёт и свой список кадров")
    fun theJourneyGroupHasItsOwnReport() {
        assertTrue(runJourneys.contains(SUMMARY_FILE), "$RUN_JOURNEYS_SCRIPT must write its own summary")
        assertTrue(
            runJourneys.contains(JOURNEY_STORAGE_ARGUMENT_KEY),
            "every journey run must pass the storage argument: memory proves nothing about survival",
        )
        assertTrue(
            runJourneys.contains(SURVIVAL_WRITE_KEY) && runJourneys.contains(SURVIVAL_CHECK_KEY),
            "the survival relay must run its two phases as separate instrumentation runs",
        )
        assertTrue(
            runJourneys.contains(NOT_GRANTED_KEY) && runJourneys.contains(SETTING_KEY),
            "the reliability states must have their own runs: the script sets their environment",
        )
        assertTrue(
            text(JOURNEY_DESIGN).contains(SKYLANKA_STORAGE_SUPPORT),
            "the storage choice must be stated in the technical design",
        )
    }

    @Тогда("походы не входят в экранную группу")
    fun journeysStayOutOfTheScreenGroup() {
        listOf(UI_GROUP_BLOCK, DATA_GROUP_BLOCK).forEach { arm ->
            val body = caseBody(arm)
            // В скрипте подстановка оболочки остаётся литералом: ищем `notPackage=$ИМЯ`,
            // а не развёрнутое значение — иначе проверка не находила бы ничего.
            assertTrue(
                body.contains("notPackage=\$$JOURNEY_PACKAGES_KEY"),
                "$RUN_TESTS_SCRIPT must exclude the journey package inside the $arm group",
            )
        }
    }

    /**
     * Тело ветки `case`: от строки с меткой до строки её закрытия.
     *
     * Поиск ведётся по строкам, а не по подстроке: `ui)` и `data)` встречаются и в тексте
     * комментариев, и поиск подстроки читал бы проверку не в той ветке.
     */
    private fun caseBody(label: String): String {
        val lines = runTests.lines()
        val start = lines.indexOfFirst { it.trim() == label }
        if (start < 0) return ""
        val body = lines.drop(start)
        val end = body.indexOfFirst { it.trim() == CASE_END }
        return (if (end < 0) body else body.take(end)).joinToString("\n")
    }

    // --- Реестр поверхностей ----------------------------------------------------

    @Когда("участник открывает граф навигации приложения и его манифест")
    fun theReaderOpensTheNavigationGraph() {
        assertTrue(navigationSurfaces().isNotEmpty(), "the navigation graph must name its surfaces")
    }

    @Тогда("каждая пользовательская поверхность названа строкой реестра походов")
    fun everySurfaceIsNamedInTheRegistry() {
        // Сверяется с таблицей §7, а не со всем документом: имена поверхностей есть ещё
        // и в колонке «Поверхности» таблицы походов, и чтение всего документа пропустило бы
        // потерянную строку реестра поверхностей.
        val registryRows = surfaceRows().joinToString("\n")
        val missing = navigationSurfaces().map { it.second }.filterNot { registryRows.contains(it) }
        assertEquals(
            emptyList<String>(),
            missing,
            "these user-facing surfaces have no journey row and no out-of-scope reason",
        )
    }

    @Тогда("каждая строка реестра называет поход либо причину «вне набора»")
    fun everyRegistryRowNamesAJourneyOrAReason() {
        val rows = surfaceRows()
        assertTrue(rows.isNotEmpty(), "the surface registry must have rows")
        val silent = rows.filterNot { row -> row[JOURNEY_OR_REASON_COLUMN].any { it.isDigit() } }
            .filterNot { row -> row[JOURNEY_OR_REASON_COLUMN].contains(OUT_OF_SCOPE) }
        assertEquals(
            emptyList<String>(),
            silent.map { it.first() },
            "these surface rows name neither a journey nor a reason",
        )
    }

    @Тогда("шторки и диалоги названы строками второго порядка внутри своего похода")
    fun sheetsAndDialogsAreNamed() {
        val secondOrder = surfaceRows().filter { it.joinToString(" ").contains(SECOND_ORDER) }
        assertTrue(
            secondOrder.size >= MIN_SECOND_ORDER_ROWS,
            "sheets and dialogs must be named as second-order rows inside their journey",
        )
        secondOrder.forEach { row ->
            assertTrue(
                row.joinToString(" ").any { it.isDigit() },
                "a second-order row must name its journey: ${row.first()}",
            )
        }
    }

    @Когда("участник открывает карту экранов рабочей спецификации")
    fun theReaderOpensTheScreenMap() {
        screenMap = tableRows(
            section(text(BEHAVIOR_DOC), SCREEN_MAP_SECTION, SUBSECTION_MARK),
            cells = MAP_CELLS,
            header = SCREEN_HEADER,
        )
        assertTrue(screenMap.isNotEmpty(), "$BEHAVIOR_DOC must keep the screen map")
    }

    @Тогда("каждый экран карты, отмеченный существующим, назван хотя бы одной строкой реестра походов")
    fun everyExistingScreenIsNamed() {
        val registryText = text(JOURNEY_DESIGN)
        val existing = screenMap.filter { it[STATE_COLUMN].contains(EXISTS) }
        assertTrue(existing.isNotEmpty(), "the screen map must mark existing screens")
        val missing = existing.map { screenName(it) }.filterNot { registryText.contains(it) }
        assertEquals(emptyList<String>(), missing, "these existing screens are named by no registry row")
    }

    // --- Список походов ---------------------------------------------------------

    @Когда("участник просит список походов прогона")
    fun theReaderAsksForTheJourneyList() {
        // Реестр нужен и здесь: строка «каждый поход назван сценарием» сверяет список
        // с таблицей, а таблица читается из рабочей спецификации.
        registry = text(JOURNEY_DESIGN)
        assertTrue(text(FEATURE_FILE).contains(JOURNEY_TAG), "the feature must tag its journeys")
    }

    @Тогда("список называет каждый поход и его класс")
    fun theListNamesEveryJourneyAndClass() {
        val build = text(ACCEPTANCE_BUILD)
        assertTrue(build.contains(JOURNEY_LIST_TASK), "$ACCEPTANCE_BUILD must declare $JOURNEY_LIST_TASK")
        assertTrue(
            build.contains(JOURNEY_TAG_DECLARATION),
            "the journey tag must be declared with its reason, like $DEVICE_TAG_NAME",
        )
        assertTrue(build.contains(JOURNEY_LIST_REPORT), "$JOURNEY_LIST_TASK must write its own list file")
        assertEquals(
            journeyRows().size,
            scenariosOf(text(FEATURE_FILE), JOURNEY_TAG),
            "every journey row of the registry must be a tagged scenario of its own",
        )
    }

    @Тогда("список называет состояния надёжности отдельно от путей человека")
    fun theListSeparatesStates() {
        val feature = text(FEATURE_FILE)
        val states = scenariosOf(feature, RELIABILITY_TAG)
        assertTrue(
            states >= MIN_RELIABILITY_SCENARIOS,
            "reliability states must be their own scenarios: $states",
        )
        assertTrue(
            feature.contains("$JOURNEY_TAG $RELIABILITY_TAG"),
            "reliability scenarios must carry both tags: journey and reliability",
        )
    }

    // --- Карта экранов заказчика (SKL-00) ---------------------------------------

    @Допустим("исходная спецификация перечисляет шестнадцать экранов первой версии")
    fun theOriginalSpecListsSixteenScreens() {
        originalScreens = tableRows(
            section(text(ORIGINAL_SPEC), ORIGINAL_SCREENS_SECTION, SECTION_MARK),
            cells = ORIGINAL_CELLS,
            header = SCREEN_HEADER,
        ).map { it.first() }
        assertEquals(
            ORIGINAL_SCREEN_COUNT,
            originalScreens.size,
            "the customer specification must keep its sixteen screens: $originalScreens",
        )
    }

    @Тогда("для каждого из шестнадцати экранов названы его место в коде и состояние")
    fun everyOriginalScreenHasItsPlaceAndState() {
        assertTrue(screenMap.isNotEmpty(), "the working screen map must be open")
        originalScreens.forEach { screen ->
            val expected = requireNotNull(ORIGINAL_TO_MAP.firstOrNull { it.first == screen }?.second) {
                "исходный экран «$screen» не сопоставлен со строкой карты экранов: дополни $STEPS_FILE"
            }
            val mapped = requireNotNull(screenMap.firstOrNull { it.first().startsWith(expected) }) {
                "исходный экран «$screen» должен быть описан строкой «$expected» в $BEHAVIOR_DOC"
            }
            assertTrue(
                mapped[CODE_COLUMN].isNotBlank() && mapped[STATE_COLUMN].isNotBlank(),
                "строка «${mapped.first()}» должна называть место в коде и состояние",
            )
        }
    }

    @И("каждый экран сопоставлен со строкой реестра походов или назван вне набора")
    fun everyScreenIsMappedToTheJourneyRegistry() {
        val registryText = text(JOURNEY_DESIGN)
        screenMap
            .filter { it[STATE_COLUMN].contains(EXISTS) }
            .forEach { row ->
                val name = screenName(row)
                assertTrue(
                    registryText.contains(name),
                    "экран «$name» есть в продукте и обязан быть назван строкой реестра походов",
                )
            }
    }

    // --- Разбор -----------------------------------------------------------------

    private fun journeyFiles(): List<File> = File(RepositoryLayout.root, JOURNEY_DIR)
        .walkTopDown()
        .filter { it.isFile && it.extension == KOTLIN && it.name != BASE_CLASS_FILE }
        .sortedBy { it.name }
        .toList()

    /**
     * Пользовательские поверхности из кода.
     *
     * Пять `<activity>` манифеста и перечисления навигации: вкладки корня, экраны второго
     * уровня настроек, состояния раздела «Лекарства», входы истории. `RootLayer` сюда
     * не входит: это приоритет слоёв, а не поверхность. Шторки и диалоги — поверхности
     * второго порядка, их держит таблица реестра, а не этот перечень.
     */
    private fun navigationSurfaces(): List<Pair<String, String>> {
        val navigation = File(RepositoryLayout.root, NAVIGATION_DIR)
        val surfaces = mutableListOf<Pair<String, String>>()
        NAVIGATION_TYPES.forEach { (file, type) ->
            val body = File(navigation, file).readText()
            entriesOf(body, type).forEach { surfaces += type to "$type.$it" }
        }
        activities().forEach { surfaces += ACTIVITY_KIND to it }
        return surfaces
    }

    /**
     * Значения перечисления или части закрытого интерфейса.
     *
     * Перечисление — константы верхнего регистра, закрытый интерфейс — `data object`/`data class`:
     * для реестра разницы нет, оба вида называют место в навигации.
     */
    private fun entriesOf(body: String, type: String): List<String> {
        val entries = mutableListOf<String>()
        val enumBody = Regex("""(?s)enum class $type\s*\{(.*?)\n\}""").find(body)?.groupValues?.get(1).orEmpty()
        ENUM_ENTRY.findAll(enumBody).forEach { entries += it.groupValues[1] }
        val sealedBody = Regex("""(?s)sealed interface $type\s*\{(.*?)\n\}""").find(body)
            ?.groupValues?.get(1).orEmpty()
        SEALED_ENTRY.findAll(sealedBody).forEach { entries += it.groupValues[1] }
        return entries
    }

    private fun activities(): List<String> = ACTIVITY_ENTRY.findAll(text(MANIFEST))
        .map { it.groupValues[1].substringAfterLast('.') }
        .toList()

    /** Строки реестра походов (§6): те, что называют класс проверки. */
    private fun journeyRows(): List<List<String>> = tableRows(
        section(registry, JOURNEY_SECTION, SECTION_MARK),
        cells = JOURNEY_CELLS,
        header = JOURNEY_HEADER,
    ).filter { row -> row.any { REGISTRY_CLASS.containsMatchIn(it) } }

    /**
     * Классы походов из реестра — только из строк походов.
     *
     * Разбор идёт по строке таблицы, а не по всему разделу: в таблице «вне набора» тоже
     * называются классы проверок (`SettingsDeviceTest`), и чтение всего раздела выдало бы
     * их за походы.
     */
    private fun registryClasses(): List<String> = journeyRows()
        .flatMap { row -> REGISTRY_CLASS.findAll(row[JOURNEY_CLASSES_COLUMN]).map { it.groupValues[1] } }
        .distinct()
        .sorted()

    private fun surfaceRows(): List<List<String>> = tableRows(
        section(text(JOURNEY_DESIGN), SURFACE_SECTION, SECTION_MARK),
        cells = SURFACE_CELLS,
        header = SURFACE_HEADER,
    )

    private fun tableRows(body: String, cells: Int, header: String? = null): List<List<String>> = body.lines()
        .filter { it.startsWith(ROW_PREFIX) }
        .map { row ->
            row.trim().removePrefix(BAR).removeSuffix(BAR).split(BAR).map { it.trim() }
        }
        .filter { row -> row.size == cells && row.none { cell -> cell.startsWith(DASHES) } }
        .filter { row -> header == null || row.first() != header }

    /** Имена сценариев, помеченных тегом: тег стоит строкой выше своего сценария. */
    private fun scenariosOf(feature: String, tag: String): Int {
        var tagged = false
        var count = 0
        feature.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith(TAG_PREFIX) -> tagged = trimmed.split(WHITESPACE).contains(tag)
                trimmed.startsWith(SCENARIO_KEYWORD) || trimmed.startsWith(OUTLINE_KEYWORD) -> {
                    if (tagged) count++
                    tagged = false
                }
                else -> tagged = false
            }
        }
        return count
    }

    private fun screenName(row: List<String>): String = row.first().substringBefore(LEGEND_MARK).trim()

    private fun section(markdown: String, title: String, stop: String): String {
        val start = markdown.indexOf(title)
        if (start < 0) return ""
        val rest = markdown.substring(start + title.length)
        val end = rest.indexOf(stop)
        return if (end < 0) rest else rest.substring(0, end)
    }

    private fun text(relative: String): String = File(RepositoryLayout.root, relative).readText()

    private fun valueOf(configuration: String, key: String): String =
        Regex("""$key="([^"]*)"""").find(configuration)?.groupValues?.get(1).orEmpty()

    private companion object {
        const val JOURNEY_DIR = "app/src/androidTest/kotlin/ru/stohastika/sklyanka/screen/journey"
        const val BASE_CLASS_FILE = "JourneyTest.kt"
        const val NAVIGATION_DIR = "app/src/main/kotlin/ru/stohastika/sklyanka/navigation"
        const val MANIFEST = "app/src/main/AndroidManifest.xml"
        const val JOURNEY_DESIGN = "docs/tasks/SKL-140/TDD.md"
        const val SKL24_DESIGN = "docs/archive/tasks/SKL-24/TDD.md"
        const val BEHAVIOR_DOC = "docs/norm/04-behavior.md"
        const val ORIGINAL_SPEC = "design/SPEC-original.md"
        const val FEATURE_FILE = "acceptance/src/main/resources/features/SKL-140.feature"
        const val ACCEPTANCE_BUILD = "acceptance/build.gradle.kts"
        const val CONFIG_SCRIPT = "tools/emulator/config.sh"
        const val RUN_TESTS_SCRIPT = "tools/emulator/run-tests.sh"
        const val RUN_JOURNEYS_SCRIPT = "tools/emulator/run-journeys.sh"
        const val STEPS_FILE = "acceptance/.../steps/task/JourneyHarnessSteps.kt"

        const val JOURNEY_SECTION = "## 6. Реестр походов"
        const val SURFACE_SECTION = "## 7. Реестр поверхностей"
        const val SCREEN_MAP_SECTION = "### 5.1 Карта экранов и навигация"
        const val ORIGINAL_SCREENS_SECTION = "## 4. Экраны"

        const val JOURNEY_PACKAGES_KEY = "SKL_GROUP_JOURNEY_PACKAGES"
        const val UI_PACKAGES_KEY = "SKL_GROUP_UI_PACKAGES"
        const val JOURNEY_STORAGE_ARGUMENT_KEY = "SKL_JOURNEY_STORAGE_ARGUMENT"
        const val SURVIVAL_WRITE_KEY = "SKL_JOURNEY_SURVIVAL_WRITE_CLASSES"
        const val SURVIVAL_CHECK_KEY = "SKL_JOURNEY_SURVIVAL_CHECK_CLASSES"
        const val NOT_GRANTED_KEY = "SKL_JOURNEY_NOT_GRANTED_CLASSES"
        const val SETTING_KEY = "SKL_JOURNEY_SETTING_CLASSES"
        const val SKYLANKA_STORAGE_SUPPORT = "DeviceJourneyStorage"
        const val SUMMARY_FILE = "journey-summary.txt"
        const val JOURNEY_LIST_TASK = "acceptanceJourneyList"
        const val JOURNEY_TAG_DECLARATION = "val journeyTag = \"@journey\""
        const val JOURNEY_LIST_REPORT = "journey-scenarios.txt"
        const val DEVICE_TAG_NAME = "@device"

        const val JOURNEY_GROUP_CASE = "journey)"
        const val UI_GROUP_BLOCK = "ui)"
        const val DATA_GROUP_BLOCK = "data)"
        const val CASE_END = ";;"

        const val JOURNEY_TAG = "@journey"
        const val TAG_PREFIX = "@"
        const val RELIABILITY_TAG = "@reliability"
        const val SCENARIO_KEYWORD = "Сценарий:"
        const val OUTLINE_KEYWORD = "Структура сценария:"
        const val SECOND_ORDER = "2-го порядка"
        const val OUT_OF_SCOPE = "вне набора"
        const val EXISTS = "есть"
        const val KOTLIN = "kt"
        const val ACTIVITY_SCENARIO = "ActivityScenario"
        const val CAPTURE_CALL = "capture("
        const val ACTIVITY_KIND = "Activity"
        const val ROW_PREFIX = "| "
        const val BAR = "|"
        const val DASHES = "---"
        const val LEGEND_MARK = " ("
        const val SECTION_MARK = "\n## "
        const val SUBSECTION_MARK = "\n### "
        const val JOURNEY_HEADER = "№"
        const val SURFACE_HEADER = "Поверхность (источник в коде)"
        const val SCREEN_HEADER = "Экран"
        const val JOURNEY_CELLS = 4
        const val SURFACE_CELLS = 3
        const val MAP_CELLS = 3
        const val ORIGINAL_CELLS = 2
        const val CODE_COLUMN = 1
        const val STATE_COLUMN = 2
        const val JOURNEY_CLASSES_COLUMN = 2
        const val JOURNEY_OR_REASON_COLUMN = 2
        const val ORIGINAL_SCREEN_COUNT = 16
        const val MIN_SECOND_ORDER_ROWS = 4
        const val MIN_RELIABILITY_SCENARIOS = 2

        val WHITESPACE = Regex("""\s+""")

        val FORBIDDEN_IN_JOURNEYS = listOf(
            "Thread.sleep",
            "System.currentTimeMillis(",
            "Instant.now(",
            "LocalDate.now(",
            "LocalTime.now(",
            "LocalDateTime.now(",
            "ZoneId.systemDefault(",
        )

        val NAVIGATION_TYPES = listOf(
            "SettingsSection.kt" to "RootTab",
            "SettingsSection.kt" to "SettingsDestination",
            "MedsSection.kt" to "MedsDestination",
            "HistoryOrigin.kt" to "HistoryOrigin",
        )

        val ENUM_ENTRY = Regex("""^\s{4}([A-Z][A-Z0-9_]*)\s*[,;]?\s*$""", RegexOption.MULTILINE)
        val SEALED_ENTRY = Regex("""data (?:object|class) ([A-Z]\w*)""")
        val ACTIVITY_ENTRY = Regex("""<activity\s[^>]*android:name="([^"]+)"""")
        val REGISTRY_CLASS = Regex("""`([A-Za-z0-9]+Test)`""")

        /** Тег-строка вида `@journey @hostile @form`: по ней сценарий и опознаётся. */
        const val JOURNEY_TAG_SCENARIO = "@journey"

        /**
         * Сопоставление экранов заказчика со строками карты экранов (SKL-00).
         *
         * Таблица ручная, и в этом её смысл: исходные шестнадцать экранов считали до решений
         * Р-3 и Р-4, четыре вида «Сегодня» стали одним экраном, «Часы» и «Подключение часов»
         * сняты решением Р-3, а «Разрешения» называются теперь «Настроить надёжность».
         * Автоматическое сопоставление по имени здесь соврало бы: оно не знает этих решений.
         */
        val ORIGINAL_TO_MAP = listOf(
            "Заставка" to "Заставка",
            "Сегодня" to "Сегодня",
            "Сегодня — плотный день" to "Сегодня",
            "Сегодня — чужой профиль" to "Сегодня",
            "Сегодня — пусто" to "Сегодня",
            "Лекарства" to "Лекарства",
            "Архив" to "Архив",
            "Новое лекарство" to "Новое лекарство",
            "Карточка лекарства" to "Карточка лекарства",
            "Напоминание" to "Напоминание",
            "Часы" to "Часы",
            "Разрешения" to "Настроить надёжность",
            "Подключение часов" to "Часы",
            "История" to "История",
            "Настройки" to "Настройки",
            "Перенос данных" to "Перенос данных",
        )
    }
}
