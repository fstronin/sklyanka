package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import ru.stohastika.sklyanka.acceptance.RepositoryLayout

/**
 * Шаги приёмки SKL-38: имя пакета собранного приложения.
 *
 * Шаги говорят об устройстве сборки, а не о человеке с лекарствами, поэтому мир
 * сценария параметром конструктора не принимается. Выражения объявлены здесь один раз
 * на весь прогон: те же строки использует обновлённый сценарий `@composition` SKL-02,
 * и второе объявление уронило бы `:acceptance:verifyAcceptanceSteps`. Ограничение длины
 * имени пакета принадлежало часовому шву и снято вместе с ним (Р-3, SKL-106).
 *
 * Источник истины — `ProjectDefaults.APPLICATION_ID`: из той же константы конвейер
 * берёт `applicationId` варианта и ожидаемое имя слитого манифеста. Проверку самого
 * манифеста выполняет `verifyManifest*` (`ArtifactChecksConventionPlugin`), поэтому
 * связь конвейера с константами сверяется по его коду.
 */
class PackageIdentitySteps {

    private var applicationId: String? = null
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

    private companion object {
        const val APPLICATION_ID_DEFAULT = "APPLICATION_ID"
        const val APP_MODULE = "app"
        const val ARTIFACT_CHECKS_PLUGIN =
            "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/" +
                "ArtifactChecksConventionPlugin.kt"
    }
}
