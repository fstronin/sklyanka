package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.RepositoryLayout.ResourceValue

/**
 * Шаги приёмки SKL-38: подпись студии в строковых ресурсах.
 *
 * Проверяются ресурсы, а не экраны: сценарии SKL-38 не помечены `@screen` и идут
 * сценарным прогоном на JVM, где Android недоступен. Те же значения отрисуют заставка
 * и плашка «О приложении»; их рендер закреплён экранными сценариями SKL-21 и SKL-20.
 */
class BrandSignatureSteps {

    private var vendorSignature: List<ResourceValue> = emptyList()
    private var aboutLine: List<ResourceValue> = emptyList()
    private var productNames: List<ResourceValue> = emptyList()
    private var visibleStrings: List<ResourceValue> = emptyList()

    @When("проверяют подпись студии на заставке")
    fun inspectSplashSignature() {
        vendorSignature = RepositoryLayout.stringResources(VENDOR_RESOURCE)
    }

    @Then("^подпись — «([^»]+)»$")
    fun theSignatureIs(expected: String) {
        assertTrue(vendorSignature.isNotEmpty(), "$VENDOR_RESOURCE is not declared anywhere")
        vendorSignature.forEach { resource -> assertEquals(expected, resource.value, resource.describe()) }
    }

    @When("проверяют плашку «О приложении»")
    fun inspectAboutBlock() {
        aboutLine = RepositoryLayout.stringResources(ABOUT_VENDOR_RESOURCE)
    }

    @Then("^строка о студии в ней — «([^»]+)»$")
    fun theAboutLineIs(expected: String) {
        assertTrue(aboutLine.isNotEmpty(), "$ABOUT_VENDOR_RESOURCE is not declared anywhere")
        aboutLine.forEach { resource -> assertEquals(expected, resource.value, resource.describe()) }
    }

    @When("проверяют название продукта в собранном приложении")
    fun inspectProductName() {
        productNames = RepositoryLayout.stringResources(APP_NAME_RESOURCE)
    }

    @Then("^оно — «([^»]+)»$")
    fun theProductNameIs(expected: String) {
        assertTrue(productNames.isNotEmpty(), "$APP_NAME_RESOURCE is not declared anywhere")
        productNames.forEach { resource -> assertEquals(expected, resource.value, resource.describe()) }
    }

    @When("просматривают все надписи, которые приложение показывает человеку")
    fun inspectVisibleStrings() {
        visibleStrings = RepositoryLayout.userVisibleStrings()
    }

    @Then("^ни в одной из них нет «([^»]+)»$")
    fun noVisibleStringContains(forbidden: String) {
        assertTrue(visibleStrings.isNotEmpty(), "no user-visible string resources found")
        val found = visibleStrings.filter { it.value.contains(forbidden) }
        assertTrue(
            found.isEmpty(),
            "the old brand is still on display:\n" + found.joinToString("\n") { it.describe() },
        )
    }

    private fun ResourceValue.describe(): String = "$module ${file.relativeTo(RepositoryLayout.root)}: $name = «$value»"

    private companion object {
        const val VENDOR_RESOURCE = "brand_vendor"
        const val ABOUT_VENDOR_RESOURCE = "settings_about_vendor"
        const val APP_NAME_RESOURCE = "app_name"
    }
}
