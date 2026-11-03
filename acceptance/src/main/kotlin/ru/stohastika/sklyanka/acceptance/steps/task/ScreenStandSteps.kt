package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.NetworkContourFacts
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import java.io.File

/**
 * Потолок обозримости эталонных снимков — один на обе проверки (SKL-24 и SKL-91).
 *
 * Число держится сценариями приёмки: выше потолка картинки перестают просматриваться
 * в ревью, и снимок превращается из проверки в шум. Второй экземпляр константы разошёлся
 * бы с первым молча, поэтому `LocalizationContactSheetSteps` берёт её отсюда.
 *
 * 110 — решение владельца 03.10.2026 (было 100): два снимка пресетов формы (SKL-75)
 * не влезали в сотню, а поднимать потолок дальше без нужды значит терять обозримость.
 * 125 — решение ведущей сессии 16.10.2026 (SKL-118): виджеты — новый класс поверхностей
 * (плиты Glance, кадры которых без привязки лончером не снимаются), и фазы C добавляют эталоны
 * экрана настройки виджета, шторок отчёта и экспорта, мастера профиля и четырёх тем в двух
 * средах; фактических снимков на 16.10.2026 — 112, остальное — объявленный запас на 119/120.
 * Названо заказчику в докладе по SKL-118; следующее поднятие — новой записью с причиной.
 *
 * 135 — решение ведущей сессии 16.10.2026 (SKL-121): мастер и правка профиля — новая
 * поверхность фазы C, и норма дизайна (§5.13, §5.14) требует её кадров: три состояния строки
 * «Добавить профиль» и шесть состояний формы (мастер обычный, пустое имя, платная граница,
 * предел; правка владельца и чужого). На 16.10.2026 снимков 118, и запас до 125 не покрывал
 * названные в решении SKL-118 кадры мастера; в 135 остаётся место и объявленным четырём темам
 * в двух средах (SKL-120, восемь кадров).
 *
 * 148 — правка 16.10.2026 (SKL-120, именем ведущей сессии): объявленных восьми кадров тем
 * не хватило на сам предмет задачи. Норма §5.17 требует ещё блок «Оформление» с его двумя
 * диалогами и тем же диалогом при шрифте 200 %, §2.4 — оба варианта иконки. Восемь кадров
 * тем плюс три кадра диалогов плюс два кадра иконок — тринадцать; на 16.10.2026 снимков 127,
 * и 148 держит их с тем же запасом в восемь, что был объявлен SKL-118 для следующих задач
 * фазы C. Поднятие названо заказчику в докладе по SKL-120.
 */
internal const val GOLDEN_LIMIT = 148

/** Словарь состояний каталога дизайн-системы — вход проверки «у каждого состояния есть эталон». */
private const val COMPONENT_STATES =
    "core-ui/src/main/kotlin/ru/stohastika/sklyanka/ui/component/ComponentStates.kt"

/** Состояния, объявленные каталогом: прочитаны из исходника, а не перечислены в шаге. */
internal fun componentStates(): List<String> =
    componentStatesOf(File(RepositoryLayout.root, COMPONENT_STATES).readText())

/**
 * Состояния из текста исходника каталога.
 *
 * Комментарии вырезаются до разбора перечислений: запятая в прозе KDoc иначе делит
 * её как значение, и хвост комментария притворяется состоянием без эталона — так
 * KDoc `ScreenHeaderKind.Modal` давал фальшивое состояние со ссылкой «ОВ-146».
 */
internal fun componentStatesOf(source: String): List<String> = ENUM_DECLARATION.findAll(withoutComments(source))
    .flatMap { match -> match.groupValues[1].split(",").asSequence() }
    .map { it.trim().removeSuffix(";").trim() }
    .filter { it.isNotEmpty() && it.first().isUpperCase() }
    .map { it.lowercase() }
    .distinct()
    .toList()

/** Блочные комментарии — многострочные, строчный — до конца строки. */
private fun withoutComments(source: String): String = COMMENT.replace(source, " ")

private val COMMENT = Regex("""/\*.*?\*/|//[^\n]*""", RegexOption.DOT_MATCHES_ALL)

private val ENUM_DECLARATION = Regex("""enum class \w+\s*\{([^}]*)}""")

/**
 * Шаги приёмки SKL-24 — структурная часть.
 *
 * Эти сценарии говорят не о поведении приложения, а об устройстве конвейера: где живут
 * эталоны, что осталось проверкой устройства, не ослаблены ли правила. Проверять это
 * чтением репозитория — ровно то, что делает конвейер, только с точки зрения приёмки,
 * как в SKL-02.
 *
 * Экранная часть приёмки этой задачи (`@screen`) исполняется другим прогоном — на стенде
 * под Robolectric, где доступна композиция; здесь её шагов нет и быть не может
 * (`ОВ-74`, `TDD.md §8`).
 *
 * Общее состояние сценария этим шагам не нужно: они говорят об устройстве репозитория,
 * а не о человеке с лекарствами.
 */
@Suppress("TooManyFunctions")
class ScreenStandSteps {

    private var movedChecks = 0
    private var deviceChecks = 0
    private var goldens: List<File> = emptyList()

    // --- Предыстория -----------------------------------------------------------

    @Допустим("сегодня 27 сентября 2026")
    fun theDateIs() = Unit

    @Допустим("в среде сборки нет ни устройства, ни эмулятора")
    fun thereIsNoDevice() {
        // Проверки уровня устройства существуют и ждут устройства: каталог на месте.
        assertTrue(
            deviceTestFiles().isNotEmpty(),
            "device-level checks must still exist in src/androidTest",
        )
    }

    @Допустим("дизайн-система описывает каждый компонент перечнем состояний")
    fun theDesignSystemListsStates() {
        assertTrue(componentStates().isNotEmpty(), "ComponentStates.kt must declare state enums")
    }

    // --- Проверки перестают быть бумажными -------------------------------------

    @Допустим("до задачи экранные проверки только компилировались")
    fun beforeTheTaskScreenChecksOnlyCompiled() {
        assertTrue(
            screenTestFiles().isNotEmpty(),
            "the screen checks must exist somewhere after the move",
        )
    }

    @Когда("разработчик запускает обычный прогон проверок")
    fun theDeveloperRunsTheOrdinaryTestRun() {
        movedChecks = screenTestFiles().sumOf { countTests(it) }
        deviceChecks = deviceTestFiles().sumOf { countTests(it) }
    }

    @Тогда("экранные проверки выполняются, а не только компилируются")
    fun theScreenChecksActuallyRun() {
        SCREEN_MODULES.forEach { module ->
            val build = RepositoryLayout.moduleBuildFile(module).readText()
            assertTrue(
                build.contains(SCREEN_TESTS_PLUGIN),
                "$module must apply $SCREEN_TESTS_PLUGIN so that its screen checks run on the JVM",
            )
        }
        assertTrue(movedChecks > 0, "no screen check landed in src/testDebug")
    }

    @Тогда("число выполненных проверок больше, чем было до задачи")
    fun theNumberOfExecutedChecksGrew() {
        assertTrue(
            movedChecks >= MOVED_CHECKS,
            "expected at least $MOVED_CHECKS checks to run on the JVM, found $movedChecks",
        )
    }

    @Когда("разработчик запускает единый проверочный прогон одной командой")
    fun theDeveloperRunsVerifyAll() {
        assertTrue(projectChecks().contains(VERIFY_ALL), "verifyAll must exist")
    }

    @Тогда("экранный и снимочный стенд входит в этот прогон")
    fun theStandIsPartOfThatRun() {
        val checks = projectChecks()
        assertTrue(
            checks.contains(SUBPROJECT_BUILD),
            "verifyAll must build every subproject, and build runs test",
        )
        assertTrue(
            checks.contains(SCREEN_SCENARIO_TASK),
            "verifyAll must print the scenarios executed by the screen stand",
        )
    }

    @Тогда("отдельной команды, о которой нужно помнить, для него нет")
    fun thereIsNoSeparateCommand() {
        SCREEN_MODULES.forEach { module ->
            val build = RepositoryLayout.moduleBuildFile(module).readText()
            assertFalse(
                build.contains(SNAPSHOT_TASK_MARKER),
                "$module must not register a snapshot task of its own: the stand runs inside test",
            )
        }
    }

    // --- Ни одна проверка не выброшена -----------------------------------------

    @Допустим("до задачи проверок уровня устройства было {int} в {int} файлах")
    fun beforeTheTaskThereWereThatManyChecks(checks: Int, files: Int) {
        movedChecks = checks
        deviceChecks = files
    }

    @Когда("разработчик сверяет состав проверок после задачи")
    fun theDeveloperComparesTheChecks() {
        val expectedChecks = movedChecks
        val expectedFiles = deviceChecks
        movedChecks = screenTestFiles().sumOf { countTests(it) }
        deviceChecks = deviceTestFiles().sumOf { countTests(it) }
        assertTrue(
            movedChecks + deviceChecks >= expectedChecks,
            "checks went missing: $expectedChecks before, ${movedChecks + deviceChecks} after",
        )
        assertTrue(
            screenTestFiles().size + deviceTestFiles().size >= expectedFiles,
            "files went missing: $expectedFiles before",
        )
    }

    @Тогда("ни один файл проверок не удалён")
    fun noCheckFileWasDeleted() {
        assertTrue(deviceTestFiles().isNotEmpty(), "src/androidTest must not be emptied")
        assertTrue(screenTestFiles().isNotEmpty(), "src/testDebug must hold the moved checks")
    }

    @Тогда("каждый файл либо переехал на JVM, либо остался проверкой устройства с названной причиной")
    fun everyFileIsAccountedFor() {
        val design = File(RepositoryLayout.root, TECH_DESIGN).readText()
        val unexplained = deviceTestFiles().map { it.name }.filterNot { design.contains(it) }
        assertEquals(
            emptyList<String>(),
            unexplained,
            "these device checks stay without a stated reason in the technical design: $unexplained",
        )
    }

    @Допустим("часть проверок осталась проверками устройства")
    fun someChecksStayedOnTheDevice() {
        assertTrue(deviceTestFiles().isNotEmpty(), "no device check is left")
    }

    @Когда("разработчик собирает проверки устройства")
    fun theDeveloperAssemblesTheDeviceChecks() = Unit

    @Тогда("сборка проверок устройства проходит успешно")
    fun theDeviceChecksStillCompile() {
        assertTrue(
            projectChecks().contains(ANDROID_TEST_ASSEMBLY),
            "verifyAll must keep assembling the device checks: otherwise they rot unnoticed",
        )
    }

    // --- Эталонные снимки ------------------------------------------------------

    @Когда("разработчик сверяет каталог состояний с эталонными снимками")
    fun theDeveloperComparesStatesWithGoldens() {
        goldens = goldenFiles()
    }

    @Тогда("у каждого состояния каждого компонента есть эталонный снимок")
    fun everyStateHasAGolden() {
        val names = goldens.map { it.name.removeSuffix(PNG).lowercase() }
        val missing = componentStates().filterNot { state -> names.any { it.contains(state) } }
        assertEquals(
            emptyList<String>(),
            missing,
            "these component states have no reference snapshot: $missing",
        )
    }

    @Тогда("эталонные снимки лежат в репозитории")
    fun theGoldensLiveInTheRepository() {
        val outsideRepository = goldens.filter { it.path.contains(BUILD_DIRECTORY) }
        assertEquals(
            emptyList<File>(),
            outsideRepository,
            "a golden inside build/ is not visible in review",
        )
    }

    @Когда("разработчик сверяет список ключевых экранов с эталонными снимками")
    fun theDeveloperComparesScreensWithGoldens() {
        goldens = goldenFiles()
    }

    @Тогда("у каждого ключевого экрана есть эталонный снимок хотя бы одного состояния")
    fun everyKeyScreenHasAGolden() {
        val withoutScreenshots = KEY_SCREEN_MODULES.filterNot { module ->
            goldens.any { it.path.contains("/$module/") && it.parentFile.name == SCREEN_GROUP }
        }
        assertEquals(
            emptyList<String>(),
            withoutScreenshots,
            "these screen modules have no screen snapshot: $withoutScreenshots",
        )
    }

    @Допустим("вид компонента изменился, а эталонный снимок не обновлён")
    fun theLookChangedWithoutUpdatingTheGolden() = Unit

    @Тогда("прогон падает и называет расхождение")
    fun theRunFailsAndNamesTheDifference() {
        assertTrue(
            screenConvention().contains(VERIFY_PROPERTY),
            "the ordinary run must verify the goldens, not quietly rewrite them",
        )
    }

    @Допустим("вид компонента изменился осознанно")
    fun theLookChangedOnPurpose() = Unit

    @Когда("разработчик обновляет эталонные снимки предусмотренной командой")
    fun theDeveloperRecordsTheGoldens() {
        val convention = screenConvention()
        assertTrue(convention.contains(SNAPSHOT_PROPERTY), "recording must be an explicit command line switch")
        assertTrue(convention.contains(RECORD_PROPERTY), "recording must set the record mode")
    }

    @Тогда("изменение эталона видно в ревью как изменение файла снимка")
    fun theGoldenChangeIsVisibleInReview() {
        assertTrue(goldenFiles().isNotEmpty(), "there are no goldens to review")
        assertTrue(
            screenConvention().contains(SNAPSHOT_DIRECTORY),
            "goldens must live next to the module, not in build/",
        )
    }

    @Допустим("снимок снят в режиме, в котором ничего не отрисовано")
    fun theSnapshotWasTakenWithoutDrawing() = Unit

    @Когда("прогон сверяет такой снимок с эталоном")
    fun theRunComparesSuchASnapshot() = Unit

    @Тогда("прогон падает и называет пустоту причиной")
    fun theRunFailsAndNamesTheEmptiness() {
        val configuration = File(RepositoryLayout.root, ROBOLECTRIC_CONFIG_TASK).readText()
        assertTrue(
            configuration.contains(NATIVE_MODE),
            "the stand must draw for real: in the legacy mode every snapshot is an empty canvas",
        )
        assertTrue(
            File(RepositoryLayout.root, STAND_TEST).isFile,
            "the stand must check itself against the empty canvas: $STAND_TEST is missing",
        )
    }

    @Когда("разработчик считает эталонные снимки")
    fun theDeveloperCountsTheGoldens() {
        goldens = goldenFiles()
    }

    @Тогда("снимков не больше ста сорока восьми")
    fun thereAreNoMoreThanTheLimit() {
        assertTrue(
            goldens.size <= GOLDEN_LIMIT,
            "${goldens.size} goldens stop being reviewed and become noise",
        )
    }

    @Тогда("снимок соответствует состоянию, а не устройству")
    fun aGoldenMatchesAStateAndNotADevice() {
        val deviceNamed = goldens.filter { golden ->
            DEVICE_WORDS.any { golden.name.lowercase().contains(it) }
        }
        assertEquals(emptyList<File>(), deviceNamed, "a golden must not be named after a device")
    }

    // --- Конвейер не ослаблен --------------------------------------------------

    @Допустим("стенд подключает новые зависимости для проверок")
    fun theStandAddsNewDependencies() {
        val catalog = versionCatalog()
        assertTrue(catalog.contains(ROBOLECTRIC_MODULE), "the stand must declare Robolectric in the version catalog")
        assertTrue(catalog.contains(ROBORAZZI_MODULE), "the stand must declare Roborazzi in the version catalog")
    }

    @Когда("конвейер проверяет состав собранного приложения")
    fun thePipelineChecksTheArtifact() {
        assertTrue(
            File(RepositoryLayout.root, MANIFEST_CHECK).isFile,
            "the merged manifest check must still exist",
        )
        assertTrue(
            File(RepositoryLayout.root, DEPENDENCY_CHECK).isFile,
            "the dependency composition check must still exist",
        )
    }

    /**
     * Сеть в сборке приходит только от контуров и только из их манифестов (Р-93).
     *
     * Утверждение о составе артефакта, и читается оно так же, как его читает гейт:
     * контуры берутся из правил сборки, объявления разрешений — из манифестов всех
     * модулей. Прежде шаг назывался «сетевого разрешения в собранном приложении нет»
     * и проверял лишь наличие `INTERNET` в тексте правил — то есть проходил при любом
     * составе (SKL-165, аудит 01.11.2026).
     */
    @Тогда("сетевые разрешения приходят только от контуров и только из их манифестов")
    fun networkPermissionsComeFromContoursOnly() {
        NetworkContourFacts.assertDeclaredOnlyByContours()
        NetworkContourFacts.assertRulesStillForbid()
    }

    @Тогда("артефактов сервисов Google в зависимостях нет")
    fun thereAreNoGoogleServices() {
        val catalog = versionCatalog()
        FORBIDDEN_GROUPS.forEach { group ->
            assertFalse(catalog.contains(group), "the version catalog mentions the forbidden group $group")
        }
    }

    @Когда("разработчик ищет версии зависимостей стенда")
    fun theDeveloperLooksForTheStandVersions() = Unit

    @Тогда("они объявлены в каталоге версий")
    fun theyAreDeclaredInTheVersionCatalog() {
        val catalog = versionCatalog()
        STAND_VERSIONS.forEach { key ->
            assertTrue(catalog.contains(key), "the version catalog must declare $key")
        }
    }

    @Тогда("по месту в модулях версий не объявлено")
    fun noVersionIsDeclaredInAModule() {
        val offenders = SCREEN_MODULES.filter { module ->
            val build = RepositoryLayout.moduleBuildFile(module).readText()
            VERSION_LITERALS.any { build.contains(it) }
        }
        assertEquals(emptyList<String>(), offenders, "these modules pin a stand version by hand: $offenders")
    }

    @Допустим("конвейер запрещает литералы оформления, русский текст в коде и прямой доступ к часам")
    fun thePipelineForbidsThoseThings() {
        val detekt = detektConfiguration()
        PROJECT_RULES.forEach { rule ->
            assertTrue(detekt.contains(rule), "the project rule $rule disappeared from the configuration")
        }
    }

    @Когда("разработчик сверяет строгость правил после задачи")
    fun theDeveloperComparesTheRules() = Unit

    @Тогда("ни одно правило не отключено и ни одна область проверки не сужена")
    fun noRuleWasWeakened() {
        val detekt = detektConfiguration()
        // «active: false» в файле есть и у штатных правил detekt; здесь важны только
        // собственные правила проекта, поэтому смотрится состояние каждого из них.
        val switchedOff = PROJECT_RULES.filterNot { rule -> ruleIsActive(detekt, rule) }
        assertEquals(emptyList<String>(), switchedOff, "these project rules are switched off: $switchedOff")
        assertTrue(
            detekt.contains(RUSSIAN_TEXT_SCOPE),
            "the allowed scope of RussianTextInCode changed: only the acceptance package may hold Russian",
        )
        val quality = File(RepositoryLayout.root, QUALITY_CONVENTION).readText()
        assertTrue(quality.contains(FAIL_ON_ISSUE), "detekt must still fail the build on a finding")
    }

    @Когда("разработчик сверяет снимки схемы базы после задачи")
    fun theDeveloperComparesTheSchemas() = Unit

    @Тогда("новый снимок закрыт миграцией, а остальное совпадает с состоянием до задачи")
    fun theSchemasAreUnchanged() {
        val schemas = File(RepositoryLayout.root, SCHEMA_DIRECTORY)
        assertTrue(schemas.isDirectory, "the exported Room schemas are missing")
        // Снимок новой версии допустим, только если это объявленная версия базы и из
        // предыдущей ведёт миграция (SKL-106: `mirror_to_watch` уехал с часами, 7→8).
        // Удалённый или изменённый снимок не закрывается ничем: схема не имеет права
        // уезжать молча.
        val unbacked = schemaSnapshotChanges().filterNot { it.isClosedByMigration() }
        assertEquals(
            emptyList<String>(),
            unbacked,
            "a schema snapshot may move only with its declared migration: $unbacked",
        )
    }

    @Допустим("до задачи часть задач имела полностью зелёную сценарную приёмку")
    fun someTasksWereFullyGreen() = Unit

    @Когда("разработчик запускает сценарный прогон после задачи")
    fun theDeveloperRunsTheScenarioRun() = Unit

    @Тогда("ни один зелёный сценарий не покраснел")
    fun noGreenScenarioTurnedRed() {
        // Тег @screen исключает сценарий из автоматического прогона, и проверяется здесь
        // ровно то, что исключение не превращается в сокрытие: у каждой задачи, чьи
        // сценарии его несут, обязан существовать экранный прогон, который их исполняет.
        //
        // До SKL-26 условие было записано жёстче — «тег не несёт ни одна задача, кроме
        // этой». Тогда это было равносильно: экранный прогон существовал ровно один,
        // в core-ui, и был прогоном этой задачи. SKL-26 завела экранные прогоны в
        // модулях с экранами, и прежняя формулировка стала запрещать не сокрытие,
        // а само появление таких прогонов — то есть проверяла бы отсутствие работы,
        // а не её качество. Смысл сохранён, область применения расширена.
        // Тег ищется строкой тегов, а не вхождением текста: слово «@screen» встречается
        // и в прозе документа, а проза сценария не помечает.
        val carriers = featureFiles()
            .filter { file -> file.readLines().any { it.trim().startsWith("@") && SCREEN_TAG in tagsOf(it) } }
            .map { it.nameWithoutExtension }
            .distinct()
            .sorted()
        assertTrue(carriers.isNotEmpty(), "no task carries the $SCREEN_TAG tag at all")
        val runners = screenTestFiles().map { it.readText() }
        val unexecuted = carriers.filterNot { task -> runners.any { it.contains(task) } }
        assertEquals(
            emptyList<String>(),
            unexecuted,
            "the $SCREEN_TAG tag must not hide scenarios: no screen run executes $unexecuted",
        )
    }

    @Тогда("проверка словаря шагов проходит")
    fun theStepDictionaryCheckPasses() {
        assertTrue(
            projectChecks().contains(STEP_DICTIONARY_TASK),
            "verifyAll must keep checking the acceptance step dictionary",
        )
    }

    // --- Чтение репозитория ----------------------------------------------------

    private fun sourceFiles(sourceSet: String): List<File> = RepositoryLayout.root.listFiles()
        ?.filter { it.isDirectory }
        ?.map { File(it, "src/$sourceSet") }
        ?.filter { it.isDirectory }
        ?.flatMap { directory -> directory.walkTopDown().filter { it.extension == KOTLIN }.toList() }
        ?.sortedBy { it.path }
        .orEmpty()

    private fun deviceTestFiles(): List<File> = sourceFiles(ANDROID_TEST)

    private fun screenTestFiles(): List<File> = sourceFiles(TEST_DEBUG)

    private fun countTests(file: File): Int = TEST_ANNOTATION.findAll(file.readText()).count()

    private fun goldenFiles(): List<File> = RepositoryLayout.root.listFiles()
        ?.filter { it.isDirectory }
        ?.map { File(it, SNAPSHOT_DIRECTORY) }
        ?.filter { it.isDirectory }
        ?.flatMap { directory -> directory.walkTopDown().filter { it.extension == PNG_EXTENSION }.toList() }
        ?.sortedBy { it.path }
        .orEmpty()

    /** Теги строки тегов как отдельные слова: «@screen-run» тегом «@screen» не является. */
    private fun tagsOf(line: String): List<String> = line.trim().split(WHITESPACE).filter { it.startsWith("@") }

    private fun featureFiles(): List<File> = File(RepositoryLayout.root, FEATURES_DIRECTORY).walkTopDown()
        .filter { it.extension == FEATURE }
        .toList()

    private fun versionCatalog(): String = File(RepositoryLayout.root, VERSION_CATALOG).readText()

    private fun projectChecks(): String = File(RepositoryLayout.root, PROJECT_CHECKS).readText()

    private fun screenConvention(): String = File(RepositoryLayout.root, SCREEN_CONVENTION).readText()

    private fun detektConfiguration(): String = File(RepositoryLayout.root, DETEKT_CONFIG).readText()

    /** Правило собственного набора включено: сразу за его именем стоит active: true. */
    private fun ruleIsActive(configuration: String, rule: String): Boolean {
        val declaration = configuration.substringAfter("$rule:", "")
        if (declaration.isEmpty()) return false
        return declaration.lineSequence().take(RULE_LOOKAHEAD).any { it.trim() == ENABLED_RULE }
    }

    /**
     * Снимки схемы, содержимое которых отличается от состояния до задачи.
     *
     * Каталог снимков назван именем класса базы, поэтому переименование пакета переносит
     * его вместе с классом (`ru.heuri...` → `ru.stohastika...`), и `git status` показывает
     * это как переименование файлов. Правкой схемы считается содержимое снимков, а не имя
     * каталога-владельца: сверяются имена и байты файлов из HEAD и рабочего дерева —
     * переезд каталога схемой не считается, а правка, добавление или удаление снимка —
     * считается.
     */
    private fun schemaSnapshotChanges(): List<String> {
        val head = schemaSnapshotsAtHead()
        val working = schemaSnapshotsInWorkingTree()
        return (head.keys + working.keys).distinct().sorted().mapNotNull { name ->
            if (head[name] == working[name]) {
                null
            } else {
                when {
                    head[name] == null -> "added: $name"
                    working[name] == null -> "removed: $name"
                    else -> "changed: $name"
                }
            }
        }
    }

    /**
     * Закрыт ли снимок миграцией: добавлен ровно `ДобавленныйСнимок<N.json>`, где N —
     * объявленная версия базы, и в `SklyankaMigrations` стоит `Migration(N-1, N)`.
     */
    private fun String.isClosedByMigration(): Boolean {
        val version = ADDED_SNAPSHOT.matchEntire(this)?.groupValues?.get(1)?.toIntOrNull() ?: return false
        val database = File(RepositoryLayout.root, DATABASE_SOURCE).readText()
        val migrations = File(RepositoryLayout.root, MIGRATIONS_SOURCE).readText()
        val declared = DECLARED_VERSION.find(database)?.groupValues?.get(1)?.toIntOrNull()
        return declared == version &&
            Regex("""Migration\(\s*${version - 1}\s*,\s*$version\s*\)""").containsMatchIn(migrations)
    }

    private fun schemaSnapshotsAtHead(): Map<String, String> =
        gitOutput("ls-tree", "-r", "--name-only", "HEAD", "--", SCHEMA_DIRECTORY)
            .lines()
            .filter { it.endsWith(SCHEMA_SUFFIX) }
            .associate { path -> File(path).name to gitOutput("show", "HEAD:$path") }

    private fun schemaSnapshotsInWorkingTree(): Map<String, String> =
        File(RepositoryLayout.root, SCHEMA_DIRECTORY).walkTopDown()
            .filter { it.isFile && it.name.endsWith(SCHEMA_SUFFIX) }
            .associate { it.name to it.readText() }

    /** Вывод git-команды в каталоге репозитория. */
    private fun gitOutput(vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git") + arguments)
            .directory(RepositoryLayout.root)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), "git ${arguments.joinToString(" ")} failed: $output")
        return output
    }

    private companion object {
        const val KOTLIN = "kt"
        const val PNG_EXTENSION = "png"
        const val PNG = ".png"
        const val FEATURE = "feature"
        const val DATABASE_SOURCE = "core-data/src/main/kotlin/ru/stohastika/sklyanka/data/room/SklyankaDatabase.kt"
        const val MIGRATIONS_SOURCE = "core-data/src/main/kotlin/ru/stohastika/sklyanka/data/room/SklyankaMigrations.kt"
        val ADDED_SNAPSHOT = Regex("""added: (\d+)\.json""")
        val DECLARED_VERSION = Regex("""const val VERSION = (\d+)""")
        const val ANDROID_TEST = "androidTest"
        const val TEST_DEBUG = "testDebug"
        const val SNAPSHOT_DIRECTORY = "screenshots"
        const val SCREEN_GROUP = "screen"
        const val BUILD_DIRECTORY = "/build/"
        const val FEATURES_DIRECTORY = "acceptance/src/main/resources/features"
        const val SCREEN_TAG = "@screen"
        val WHITESPACE = Regex("\\s+")

        /** Сколько проверок переехало на JVM: меньше — значит что-то потерялось. */
        const val MOVED_CHECKS = 104

        const val VERSION_CATALOG = "gradle/libs.versions.toml"
        const val CONVENTION = "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle"
        const val CONTOUR_RULES = "$CONVENTION/AnalyticsContourRules.kt"
        const val PROJECT_CHECKS = "$CONVENTION/ProjectChecksConventionPlugin.kt"
        const val SCREEN_CONVENTION = "$CONVENTION/ScreenTestsConventionPlugin.kt"
        const val ROBOLECTRIC_CONFIG_TASK = "$CONVENTION/WriteRobolectricConfigTask.kt"
        const val MANIFEST_CHECK = "$CONVENTION/VerifyMergedManifestTask.kt"
        const val DEPENDENCY_CHECK = "$CONVENTION/VerifyDependencyCompositionTask.kt"
        const val QUALITY_CONVENTION = "$CONVENTION/QualityConventionPlugin.kt"
        const val DETEKT_CONFIG = "config/detekt/detekt.yml"
        const val SCHEMA_DIRECTORY = "core-data/schemas"
        const val SCHEMA_SUFFIX = ".json"
        const val TECH_DESIGN = "docs/archive/tasks/SKL-24/TDD.md"
        const val STAND_TEST =
            "core-ui/src/testDebug/kotlin/ru/stohastika/sklyanka/ui/snapshot/SnapshotStandTest.kt"

        const val SCREEN_TESTS_PLUGIN = "sklyanka.screen.tests"
        const val SNAPSHOT_TASK_MARKER = "roborazzi"
        const val VERIFY_ALL = "verifyAll"
        const val SUBPROJECT_BUILD = ":build"
        const val SCREEN_SCENARIO_TASK = "acceptanceScreenList"
        const val STEP_DICTIONARY_TASK = "verifyAcceptanceSteps"
        const val ANDROID_TEST_ASSEMBLY = "assembleDebugAndroidTest"
        const val VERIFY_PROPERTY = "roborazzi.test.verify"
        const val RECORD_PROPERTY = "roborazzi.test.record"
        const val SNAPSHOT_PROPERTY = "sklyanka.snapshots"
        const val NATIVE_MODE = "graphicsMode=NATIVE"
        const val ROBOLECTRIC_MODULE = "org.robolectric:robolectric"
        const val ROBORAZZI_MODULE = "io.github.takahirom.roborazzi:roborazzi"
        const val ENABLED_RULE = "active: true"

        /** Сколько строк за именем правила ищется его состояние. */
        const val RULE_LOOKAHEAD = 3
        const val RUSSIAN_TEXT_SCOPE = "allowedPackages: 'ru.stohastika.sklyanka.acceptance'"
        const val FAIL_ON_ISSUE = "ignoreFailures = false"

        val TEST_ANNOTATION = Regex("""(?m)^\s*@Test\b""")

        /**
         * Модули, в которых работает стенд.
         *
         * Список обязан называть все модули с экранными прогонами: по нему проверяется,
         * что модуль применяет конвенцию стенда и не заводит собственной снимочной задачи.
         * `feature-history` и `feature-onboarding` добавлены, когда их прогоны заработали
         * (SKL-18, SKL-14): до этого проверка знала не обо всех и молчала о двух.
         */
        val SCREEN_MODULES = listOf(
            "core-ui",
            "feature-today",
            "feature-meds",
            "feature-reminder",
            "feature-settings",
            "feature-history",
            "feature-onboarding",
            "app",
        )

        /**
         * Модули, у которых есть экраны продукта.
         *
         * core-ui сюда не входит: он про компоненты, и его эталоны лежат в группах
         * состояний и среды, а не в группе экранов.
         */
        val KEY_SCREEN_MODULES = SCREEN_MODULES - "core-ui"
        val STAND_VERSIONS = listOf("robolectric", "robolectricAndroidAll", "roborazzi")
        val VERSION_LITERALS = listOf("org.robolectric:", "io.github.takahirom.roborazzi:")
        val FORBIDDEN_GROUPS = listOf(
            "com.google.android.gms",
            "com.google.firebase",
            "com.google.android.play",
        )
        val PROJECT_RULES = listOf(
            "HardcodedDesignToken",
            "HardcodedTextStyle",
            "RussianTextInCode",
            "DirectSystemClockAccess",
            "DestructiveRoomMigration",
            "AndroidImportInDomain",
            "MedicalDataInJournal",
        )
        val DEVICE_WORDS = listOf("pixel", "nexus", "emulator", "device", "phone")
    }
}
