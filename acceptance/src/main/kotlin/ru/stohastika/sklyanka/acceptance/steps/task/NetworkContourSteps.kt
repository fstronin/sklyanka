package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.NetworkContourFacts
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld

/**
 * Шаги о составе сборки по сети (SKL-02, SKL-22; SKL-165 п. 38).
 *
 * Утверждения этой группы — о **сборке**, а не о поведении экрана, и раньше они либо
 * не имели реализации (и потому не краснели), либо проверяли манифест приложения.
 * Теперь они читают данные гейта состава (`ADR-0017`): список сетевых контуров
 * из правил сборки и манифесты всех модулей. Ложное утверждение роняет прогон,
 * а неразбираемые правила — тоже: проверка, которая не может определить факт, падает.
 *
 * Мира сценарию не нужно — приёмка проверяет файлы репозитория. Класс принимает мир
 * потому, что так устроен словарь шагов: состояние сценария приходит конструктором.
 */
class NetworkContourSteps(@Suppress("UNUSED_PARAMETER") world: AcceptanceWorld) {

    /**
     * Сбор данных гейта: контуры и манифесты читаются здесь, а не в проверках.
     *
     * Шаг существует как отдельная строка сценария, потому что «Когда» — это действие,
     * а «Тогда» — суждение о его результате. Ошибка разбора правил обязана прозвучать
     * в действии: тогда видно, что сломался источник, а не утверждение.
     */
    @Когда("^проверяют сетевые контуры сборки и манифесты её модулей$")
    fun theCompositionIsInspectable() {
        assertTrue(
            NetworkContourFacts.contours().isNotEmpty(),
            "the network contours must be readable: the composition gate reads the same list",
        )
        assertTrue(
            NetworkContourFacts.declaredPermissions().isNotEmpty(),
            "no module manifest is readable: the composition gate would check nothing",
        )
    }

    /** Правило `ADR-0017` п. 2 в одну строку: сеть — только у контура и только из его манифеста. */
    @Тогда("^`INTERNET` объявлен только модулями сетевых контуров$")
    fun internetComesFromContoursOnly() {
        NetworkContourFacts.assertDeclaredOnlyByContours()
    }

    /** `free` — единственная поставляемая сборка, и её сетевой контур один (Р-93, Р-94). */
    @Тогда("^поставляемая сборка несёт контур аналитики и не несёт платёжный$")
    fun theShippedBuildCarriesTheAnalyticsContour() {
        NetworkContourFacts.assertShippedContourIsAnalyticsOnly()
    }

    /** `ACCESS_WIFI_STATE` запрещён всегда; `ACCESS_NETWORK_STATE` принадлежит контуру (Р-100). */
    @Тогда(
        "^разрешения `ACCESS_WIFI_STATE` нет ни в одном манифесте, " +
            "а `ACCESS_NETWORK_STATE` объявляет только контур аналитики$",
    )
    fun networkStatePermissionsFollowTheContour() {
        val forbidden = NetworkContourFacts.forbiddenPermissions()
        val wifi = "android.permission.ACCESS_WIFI_STATE"
        assertTrue(wifi in forbidden, "the contour rules must forbid $wifi")
        assertEquals(
            emptyList<String>(),
            NetworkContourFacts.declarationsOf(wifi),
            "$wifi must not be declared (НФТ-15)",
        )

        val networkState = "android.permission.ACCESS_NETWORK_STATE"
        val expected = NetworkContourFacts.contours()
            .filter { networkState in it.permissions }
            .map { it.module }
            .toSet()
        val declaring = NetworkContourFacts.declarationsOf(networkState).toSet()
        assertEquals(expected, declaring, "$networkState may be declared only by the contours that own it (Р-100)")
        assertTrue(networkState !in forbidden, "the contour owns $networkState, so it is not forbidden outright")
    }

    /** Рекламный идентификатор не собирается ни при каких условиях (ПМ-26). */
    @Тогда("^рекламный идентификатор не объявлен ни в одном манифесте сборки$")
    fun advertisingIdIsAbsent() {
        assertEquals(
            emptyList<String>(),
            NetworkContourFacts.declarationsOf(NetworkContourFacts.ADVERTISING_ID),
            "${NetworkContourFacts.ADVERTISING_ID} must not be declared (НФТ-15)",
        )
    }

    /** Отсутствие разрешения — не «повезло», а запрет, который сработал бы. */
    @Тогда("^запрет этих разрешений остаётся в правилах конвейера$")
    fun theRulesStillGuardTheAbsence() {
        NetworkContourFacts.assertRulesStillForbid()
    }

    /** GMS, Firebase и прочие запрещённые группы не встречаются даже в каталоге версий. */
    @Тогда("^в нём нет артефактов сервисов Google Play и Firebase$")
    fun noGoogleOrFirebaseArtifacts() {
        val catalog = NetworkContourFacts.versionCatalog()
        NetworkContourFacts.forbiddenGroups().forEach { group ->
            assertTrue(
                !catalog.contains(group),
                "the version catalog mentions $group, so it can reach the artifact (ПЛ-4, НФТ-39)",
            )
        }
    }

    /**
     * Сторонней аналитики, сбора сбоев и рекламы нет: из охраняемых групп в каталоге
     * присутствует только провайдер контура аналитики.
     */
    @Тогда("^нет сторонних аналитических SDK, сбора сбоев и рекламы$")
    fun noThirdPartyAnalytics() {
        val catalog = NetworkContourFacts.versionCatalog()
        val providerGroups = NetworkContourFacts.shippedContours()
            .flatMap { it.coordinates }
            .map { it.substringBefore(':') }
            .toSet()
        val guardedPresent = NetworkContourFacts.guardedGroups().filter { catalog.contains(it) }.toSet()
        assertEquals(
            providerGroups,
            guardedPresent,
            "the only guarded dependency group may be the provider of the analytics contour",
        )
        NetworkContourFacts.forbiddenGroups().forEach { group ->
            assertTrue(!catalog.contains(group), "the forbidden group $group is mentionable in the catalog")
        }
    }

    /** Сетевая координата в сборке одна — та, без которой контур аналитики не работает. */
    @Тогда("^единственная сетевая координата — координата провайдера контура аналитики$")
    fun theOnlyNetworkCoordinateIsTheProvider() {
        val shipped = NetworkContourFacts.shippedContours().flatMap { it.coordinates }
        assertEquals(1, shipped.size, "the shipped build must carry exactly one network contour coordinate: $shipped")
        assertTrue(
            NetworkContourFacts.versionCatalog().contains(shipped.single()),
            "the provider coordinate ${shipped.single()} must be declared in the version catalog",
        )
        assertEquals(
            setOf(NetworkContourFacts.contours().single { it.module == "core-analytics" }.coordinates.single()),
            shipped.toSet(),
            "the only network coordinate is the analytics provider's",
        )
    }
}
