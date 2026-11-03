package ru.stohastika.sklyanka.acceptance

import java.io.File

/**
 * Сетевые контуры сборки и то, что о них говорят манифесты модулей.
 *
 * Сценарии о сети (`SKL-02`, `SKL-22`, `SKL-18`, `SKL-19`, `SKL-33`, `SKL-65`, `SKL-119`,
 * `SKL-24`) утверждают свойство **сборки**, а не поведение одного экрана. До SKL-165 они
 * читали `app/src/main/AndroidManifest.xml` и потому проходили при ложном тексте: после
 * Р-93 `INTERNET` приходит из манифеста контура, и в манифесте приложения его нет никогда —
 * проверка «разрешения нет» стала бы вечной и пустой.
 *
 * Здесь читается **то же, что читает гейт состава** (`VerifyMergedManifestTask`
 * и `NetworkContourRules`, `ADR-0017`):
 *
 * - список сетевых контуров — из `AnalyticsContourRules.kt` (модуль, разрешения, координаты);
 * - объявления разрешений — из манифестов **всех** модулей и **всех** наборов исходников
 *   (гейт получает их как `moduleManifests`), а не из одного манифеста приложения;
 * - состав поставляемой сборки — из продуктовых зависимостей `app` без `pro`-варианта
 *   (`free` — единственная поставляемая сборка, Р-86, Р-94).
 *
 * Разбор исходника правил намеренно строгий: если список не нашёлся или не разобрался,
 * это отказ с названным местом, а не «правил нет — нарушений нет». Проверка, которая
 * не может определить факт, обязана падать (TODO SKL-165 п. 38).
 */
@Suppress("TooManyFunctions")
object NetworkContourFacts {

    /** Один сетевой контур: модуль, его разрешения и координаты, без которых он не работает. */
    data class Contour(
        val module: String,
        val project: String,
        val permissions: List<String>,
        val coordinates: List<String>,
    )

    /** Разрешение доступа к сети: единственное, которое бывает у контура. */
    const val NETWORK_PERMISSION = "android.permission.INTERNET"

    /** Рекламный идентификатор: запрещён безусловно (ПМ-26, НФТ-15). */
    const val ADVERTISING_ID = "com.google.android.gms.permission.AD_ID"

    /** Имена списков правил: по ним читаются данные гейта, а не их пересказ. */
    private const val FORBIDDEN_PERMISSIONS_LIST = "FORBIDDEN_PERMISSIONS"
    private const val FORBIDDEN_GROUPS_LIST = "FORBIDDEN_GROUPS"
    private const val GUARDED_GROUPS_LIST = "GUARDED_GROUPS"

    /** Правила контуров: единственный источник имён модулей и разрешений. */
    private const val CONTOUR_RULES =
        "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/AnalyticsContourRules.kt"

    /** Каталог версий: по нему видно, какие координаты вообще могут попасть в граф. */
    private const val VERSION_CATALOG = "gradle/libs.versions.toml"

    private val CONTOUR = Regex(
        "NetworkContour\\(\\s*module\\s*=\\s*\"([^\"]+)\"\\s*,\\s*project\\s*=\\s*\"([^\"]+)\"\\s*," +
            "\\s*permissions\\s*=\\s*listOf\\(([^)]*)\\)\\s*,\\s*requiredCoordinates\\s*=\\s*listOf\\(([^)]*)\\)",
    )
    private val STRING_LITERAL = Regex("\"([^\"]+)\"")
    private const val LIST_OF = "val\\s+%s\\s*=\\s*listOf\\(([^)]*)\\)"
    private val USES_PERMISSION = Regex("<uses-permission[^>]*>")
    private val TAG_NAME = Regex("android:name\\s*=\\s*\"([^\"]+)\"")
    private val PROJECT_DEPENDENCY = Regex("project\\(\"(:[A-Za-z0-9\\-_]+)\"\\)")
    private const val REMOVE_DIRECTIVE = "tools:node=\"remove\""
    private val TEST_CONFIGURATIONS =
        listOf("testImplementation", "testDebugImplementation", "androidTestImplementation")
    private val CACHE = mutableMapOf<String, Any>()

    /** Контуры из правил сборки. Пустой список — ошибка разбора, а не «контуров нет». */
    @Suppress("MagicNumber")
    fun contours(): List<Contour> = cached("contours") {
        val found = CONTOUR.findAll(rules().readText()).map { match ->
            Contour(
                module = match.groupValues[1],
                project = match.groupValues[2],
                permissions = literals(match.groupValues[3]),
                coordinates = literals(match.groupValues[4]),
            )
        }.toList()
        require(found.isNotEmpty()) {
            "no network contour is readable in ${rules().path}: the composition gate reads this " +
                "list, so a scenario about the network cannot be decided without it"
        }
        found
    }

    /** Модули контуров: только им закон разрешает сетевое разрешение (ADR-0017 п. 2). */
    fun contourModules(): Set<String> = contours().map { it.module }.toSet()

    /** Разрешения, запрещённые безусловно, — по тем же правилам, что у гейта. */
    fun forbiddenPermissions(): List<String> = literalsOf(FORBIDDEN_PERMISSIONS_LIST)

    /** Группы координат, запрещённые в сборке целиком (GMS, Firebase, реклама, чужая аналитика). */
    fun forbiddenGroups(): List<String> = literalsOf(FORBIDDEN_GROUPS_LIST)

    /** Группы, чьи координаты обязаны быть в белом списке (вендор магазина и провайдер). */
    fun guardedGroups(): List<String> = literalsOf(GUARDED_GROUPS_LIST)

    /**
     * Что объявляет каждый модуль: имя модуля → разрешения из всех его манифестов.
     *
     * Снятие разрешения (`tools:node="remove"`) объявлением не считается: это как раз
     * обратное — манифест `app` так убирает `ACCESS_NETWORK_STATE` и `AD_ID`, пришедшие
     * из библиотек.
     */
    fun declaredPermissions(): Map<String, Set<String>> = cached("declared") {
        moduleManifests().mapValues { (_, texts) -> texts.flatMapTo(mutableSetOf()) { permissionsIn(it) } }
    }

    /** Модули, объявившие разрешение: пустой список означает «никто». */
    fun declarationsOf(permission: String): List<String> =
        declaredPermissions().filterValues { permission in it }.keys.sorted()

    /**
     * Модули, входящие в поставляемую сборку: продуктовые зависимости `app` без `pro`.
     *
     * `free` — единственная поставляемая сборка (Р-86, Р-94), поэтому `"proImplementation"`
     * не в счёт: платёжный контур существует в графе, но в артефакт не едет.
     */
    fun shippedModules(): List<String> = cached("shipped") {
        val lines = RepositoryLayout.moduleBuildFile("app").readLines()
            .filterNot { line -> line.trimStart().startsWith("\"pro") }
            .filterNot { line -> TEST_CONFIGURATIONS.any { line.trimStart().startsWith(it) } }
        PROJECT_DEPENDENCY.findAll(lines.joinToString("\n"))
            .map { it.groupValues[1].removePrefix(":") }
            .distinct()
            .sorted()
            .toList()
    }

    /** Контуры, которые действительно попадают в поставляемую сборку. */
    fun shippedContours(): List<Contour> = contours().filter { it.module in shippedModules() }

    /** Текст каталога версий: там встречаются запрещённые группы, даже если не резолвятся. */
    fun versionCatalog(): String = cached("catalog") { File(RepositoryLayout.root, VERSION_CATALOG).readText() }

    /** Строковые литералы списка правил: `val NAME = listOf("…", "…")`. */
    fun literalsOf(name: String): List<String> = cached("list-$name") {
        val pattern = Regex(LIST_OF.format(name))
        val match = pattern.find(rules().readText())
            ?: error("$name is not declared in ${rules().path}: the composition gate reads it by this name")
        literals(match.groupValues[1])
    }

    private fun rules(): File = File(RepositoryLayout.root, CONTOUR_RULES)

    private fun literals(body: String): List<String> = STRING_LITERAL.findAll(body).map { it.groupValues[1] }.toList()

    /** Разрешения, объявленные одним манифестом, без снятий через `tools:node="remove"`. */
    private fun permissionsIn(manifest: String): List<String> = USES_PERMISSION.findAll(manifest)
        .filterNot { it.value.contains(REMOVE_DIRECTIVE) }
        .mapNotNull { TAG_NAME.find(it.value)?.groupValues?.get(1) }
        .toList()

    /**
     * Манифесты модулей сборки: все наборы исходников, как их видит гейт.
     *
     * Модуль — каталог верхнего уровня с build-файлом; `build` и `build-logic` модулями
     * не являются. Наборы исходников перечисляются по каталогам `src/<набор>`.
     */
    private fun moduleManifests(): Map<String, List<String>> = RepositoryLayout.root.listFiles()
        ?.filter { it.isDirectory && it.name != "build-logic" && it.name != "build" }
        ?.filter { File(it, "build.gradle.kts").isFile }
        ?.sortedBy { it.name }
        ?.associate { module -> module.name to manifestTexts(module) }
        ?.filterValues { it.isNotEmpty() }
        .orEmpty()

    private fun manifestTexts(module: File): List<String> = File(module, "src").listFiles()
        ?.filter { it.isDirectory }
        ?.sortedBy { it.name }
        ?.map { File(it, "AndroidManifest.xml") }
        ?.filter { it.isFile }
        ?.map { it.readText() }
        .orEmpty()

    /**
     * Сетевое разрешение объявляют только модули контуров, и каждый — своё.
     *
     * Обе стороны правила `ADR-0017` п. 2: разрешение вне контура — утечка, контур без
     * своего разрешения — исчезнувший сетевой путь (контур в артефакте без сети был бы
     * обетом, а не контуром).
     */
    fun assertDeclaredOnlyByContours() {
        val declaring = declarationsOf(NETWORK_PERMISSION).toSet()
        val contours = contourModules()
        require(declaring == contours) {
            "$NETWORK_PERMISSION must be declared by the network contours and by nothing else " +
                "(ADR-0017 §2). Contours: ${contours.sorted()}, declaring modules: ${declaring.sorted()}"
        }
        contours.forEach { module ->
            val contour = requireNotNull(contours().firstOrNull { it.module == module }) { "unknown contour $module" }
            require(contour.permissions.contains(NETWORK_PERMISSION)) {
                "the $module contour is in the build but its manifest does not declare $NETWORK_PERMISSION"
            }
        }
    }

    /**
     * Поставляемая сборка несёт контур аналитики и не несёт платёжный (Р-93, Р-94).
     *
     * Модуль берётся из продуктовых зависимостей `app` без `pro`: это состав сборки,
     * а не пожелание документа.
     */
    fun assertShippedContourIsAnalyticsOnly() {
        val shipped = shippedContours().map { it.module }
        require(shipped == listOf(ANALYTICS_CONTOUR_MODULE)) {
            "the shipped build must carry only the analytics network contour " +
                "($ANALYTICS_CONTOUR_MODULE), found: $shipped"
        }
    }

    /** Разрешения состояния сети и рекламный идентификатор не объявлены ни одним манифестом. */
    fun assertNoForbiddenDeclarations() {
        forbiddenPermissions().forEach { permission ->
            val declaring = declarationsOf(permission)
            require(declaring.isEmpty()) {
                "$permission is forbidden (НФТ-15) but declared by $declaring"
            }
        }
    }

    /** Запрет остался в правилах конвейера: иначе «разрешения нет» никто не удержит. */
    fun assertRulesStillForbid() {
        val rules = rules().readText()
        forbiddenPermissions().filterNot { it == ADVERTISING_ID }.forEach { permission ->
            require(rules.contains(permission)) {
                "the contour rules must still forbid $permission, otherwise its absence is not guarded"
            }
        }
        require(rules.contains(NETWORK_PERMISSION)) {
            "the contour rules must still tie $NETWORK_PERMISSION to a contour (ADR-0017 §2)"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> cached(key: String, load: () -> T): T = CACHE.getOrPut(key) { load() } as T

    /** Модуль контура аналитики: единственный сетевой контур поставляемой сборки (Р-94). */
    private const val ANALYTICS_CONTOUR_MODULE = "core-analytics"
}
