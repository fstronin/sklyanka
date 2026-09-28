package ru.heuri.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.acceptance.RepositoryLayout
import java.time.LocalDate

/**
 * Шаги приёмки SKL-02.
 *
 * Русский текст в аннотациях — это ключевые слова Gherkin, а не текст интерфейса:
 * шаг обязан совпадать с документом дословно, иначе прогон перестаёт быть контрактом
 * задачи. Правило RussianTextInCode разрешает пакет ru.heuri.sklyanka.acceptance
 * вместе с подпакетами именно по этой причине (config/detekt/detekt.yml).
 *
 * Общее состояние сценария этим шагам не нужно: они говорят об устройстве репозитория,
 * а не о человеке с лекарствами, — поэтому мир параметром конструктора не принимается.
 */
class ProjectStructureSteps {

    private var today: LocalDate? = null
    private var packageName: String? = null
    private var referenceDevice: String? = null
    private var inspectedModule: String? = null
    private var inspectedDependencies: List<String> = emptyList()

    @Given("сегодня 3 октября 2026")
    fun theDateIs() {
        today = SCENARIO_DATE
    }

    @Given("репозиторий содержит скелет проекта без продуктового кода")
    fun theRepositoryHoldsTheSkeleton() {
        assertTrue(
            RepositoryLayout.moduleBuildFile("core-domain").isFile,
            "core-domain module is missing",
        )
        assertTrue(RepositoryLayout.moduleBuildFile("app").isFile, "app module is missing")
    }

    @Given("эталонное устройство — Huawei с EMUI 15")
    fun theReferenceDeviceIsKnown() {
        // Устройства в контейнере нет: шаг лишь фиксирует контекст сценария,
        // проверка на эталоне идёт по чек-листу @manual.
        referenceDevice = "Huawei EMUI 15"
    }

    @Given("имя пакета приложения — «ru.heuri.sklyanka»")
    fun theApplicationPackageIs() {
        packageName = "ru.heuri.sklyanka"
    }

    @When("проверяют зависимости доменного модуля")
    fun inspectDomainDependencies() {
        inspectedModule = "core-domain"
        inspectedDependencies = RepositoryLayout.projectDependenciesOf("core-domain")
    }

    @Then("он не зависит ни от платформы Android, ни от других модулей проекта")
    fun theDomainDependsOnNothing() {
        assertEquals(
            emptyList<String>(),
            inspectedDependencies,
            "core-domain must depend on no project module",
        )
        val buildFile = RepositoryLayout.moduleBuildFile("core-domain").readText()
        assertFalse(
            buildFile.contains("com.android."),
            "core-domain must not apply an Android plugin",
        )
        assertTrue(
            buildFile.contains("sklyanka.jvm.library"),
            "core-domain must stay a plain Kotlin/JVM module",
        )
    }

    @Given("в проекте есть хотя бы один модуль экрана")
    fun thereIsAScreenModule() {
        assertTrue(RepositoryLayout.screenModules().isNotEmpty(), "no feature-* module found")
    }

    @When("проверяют его зависимости")
    fun inspectScreenDependencies() {
        val module = RepositoryLayout.screenModules().first()
        inspectedModule = module
        // Продуктовые зависимости: экранный прогон приёмки подключает общий артефакт
        // `:acceptance` тестовой конфигурацией, и правилом графа модулей это разрешено
        // (SKL-26, `TDD.md §4.4`). В продуктовый артефакт такая зависимость не попадает.
        inspectedDependencies = RepositoryLayout.projectDependenciesOf(module, includeTest = false)
    }

    @Then("он зависит только от общих модулей")
    fun theScreenDependsOnSharedModulesOnly() {
        val allowed = setOf(":core-domain", ":core-ui", ":core-data")
        val unexpected = inspectedDependencies - allowed
        assertEquals(
            emptyList<String>(),
            unexpected,
            "$inspectedModule depends on modules outside core-*: $unexpected",
        )
    }

    @Then("не зависит от других модулей экранов")
    fun theScreenDoesNotDependOnOtherScreens() {
        val screens = inspectedDependencies.filter { it.startsWith(":feature-") }
        assertEquals(
            emptyList<String>(),
            screens,
            "$inspectedModule depends on other screen modules: $screens",
        )
    }

    private companion object {
        /** Дата из «Предыстории» сценариев SKL-02. */
        val SCENARIO_DATE: LocalDate = LocalDate.parse("2026-10-03")
    }
}
