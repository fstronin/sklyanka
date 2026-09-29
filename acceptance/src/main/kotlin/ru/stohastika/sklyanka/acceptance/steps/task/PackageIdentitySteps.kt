package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import java.io.File

/**
 * Шаги приёмки SKL-38: имя пакета собранного приложения.
 *
 * Шаги говорят об устройстве сборки, а не о человеке с лекарствами, поэтому мир
 * сценария параметром конструктора не принимается. Выражения объявлены здесь один
 * раз на весь прогон: те же строки использует обновлённый сценарий `@composition`
 * SKL-02, и второе объявление уронило бы `:acceptance:verifyAcceptanceSteps`.
 *
 * Источник истины — `ProjectDefaults.APPLICATION_ID`: из той же константы конвейер
 * берёт `applicationId` варианта и ожидаемое имя слитого манифеста. Проверку самого
 * манифеста выполняет `verifyManifest*` (`ArtifactChecksConventionPlugin`), поэтому
 * связь конвейера с константами сверяется по его коду.
 */
class PackageIdentitySteps {

    private var applicationId: String? = null
    private var packageNameLimit: String? = null
    private var appNamespace: String? = null
    private var releaseHappened = true

    @Given("выпуск ещё не состоялся: «Склянка» не публиковалась в магазинах")
    fun theReleaseHasNotHappened() {
        // Контекст Предыстории: записи в магазинах ещё нет, поэтому имя пакета можно менять,
        // а данные сборок под прежним именем нечего переносить. Проверять в прогоне на JVM
        // нечего — шаг фиксирует состояние продукта, как контекст эталонного устройства.
        releaseHappened = false
    }

    @When("проверяют собранное приложение")
    fun inspectBuiltApplication() {
        applicationId = RepositoryLayout.projectDefault(APPLICATION_ID_DEFAULT)
        packageNameLimit = RepositoryLayout.projectDefault(PACKAGE_NAME_LIMIT_DEFAULT)
        appNamespace = RepositoryLayout.moduleNamespace(APP_MODULE)
    }

    @Then("^имя его пакета — «([^»]+)»$")
    fun thePackageNameIs(expected: String) {
        assertEquals(
            expected,
            applicationId,
            "applicationId must come from ProjectDefaults.APPLICATION_ID",
        )
        assertEquals(expected, appNamespace, "app namespace must match the application id")
    }

    @Then("конвейер подтверждает, что длина имени пакета не превышает 28 символов")
    fun thePipelineChecksThePackageNameLength() {
        val name = applicationId ?: error("the application has not been inspected")
        val limit = packageNameLimit?.toIntOrNull()
            ?: error("$PACKAGE_NAME_LIMIT_DEFAULT is not a number")
        assertTrue(
            name.length <= limit,
            "the application id is ${name.length} characters, the Wear Engine limit is $limit: $name",
        )
        val pipeline = File(RepositoryLayout.root, ARTIFACT_CHECKS_PLUGIN).readText()
        assertTrue(
            pipeline.contains("expectedPackageName.set(ProjectDefaults.APPLICATION_ID)"),
            "the merged manifest check must take the expected package name from ProjectDefaults",
        )
        assertTrue(
            pipeline.contains("packageNameLimit.set(ProjectDefaults.PACKAGE_NAME_LIMIT)"),
            "the merged manifest check must take the package name limit from ProjectDefaults",
        )
    }

    private companion object {
        const val APPLICATION_ID_DEFAULT = "APPLICATION_ID"
        const val PACKAGE_NAME_LIMIT_DEFAULT = "PACKAGE_NAME_LIMIT"
        const val APP_MODULE = "app"
        const val ARTIFACT_CHECKS_PLUGIN =
            "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/" +
                "ArtifactChecksConventionPlugin.kt"
    }
}
