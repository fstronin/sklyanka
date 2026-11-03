package ru.stohastika.sklyanka.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.domain.analytics.AnalyticsProperty
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecord
import ru.stohastika.sklyanka.domain.analytics.AnalyticsScope
import java.time.Instant

/**
 * Адаптер провайдера без провайдера (SKL-165, TDD §11.3).
 *
 * Проверяется то, что иначе проверялось бы только на живом устройстве и в консоли
 * поставщика: SDK не инициализируется без ключа и без согласия, после `stop()` не получает
 * ни одного вызова, пачка уходит одним `trackEvent` на запись и одним `flush`, а отказ
 * поставщика не роняет вызывающего. Все 13 событий каталога проходят через адаптер —
 * это и есть проверка отображения каталога в вызовы SDK.
 */
class MyTrackerTransportTest {

    private val sdk = RecordingSdk()

    private fun transport(key: String = KEY): MyTrackerTransport = MyTrackerTransport(sdk = sdk, key = key)

    @Test
    fun `without a key the provider is unavailable and the SDK is never touched`() {
        val transport = transport(key = "")

        assertFalse(transport.available)

        transport.start()

        assertTrue(sdk.calls.isEmpty())
        assertFalse(transport.deliver(listOf(record(AnalyticsEventName.SCREEN_VIEW))))
        assertTrue(sdk.calls.isEmpty())
    }

    @Test
    fun `the SDK is initialised once, and only when transmission is opened`() {
        val transport = transport()

        assertTrue(sdk.calls.isEmpty())

        transport.start()
        transport.start()

        assertEquals(listOf("init:$KEY", "configure:1:true"), sdk.calls.take(2))
        assertEquals(1, sdk.calls.count { it.startsWith("init:") })
    }

    @Test
    fun `a batch is one event call per record and one flush`() {
        val transport = transport()
        transport.start()
        sdk.calls.clear()

        val delivered = transport.deliver(
            listOf(
                record(AnalyticsEventName.SCREEN_VIEW, mapOf(AnalyticsProperty.SCREEN to "history")),
                record(AnalyticsEventName.INTAKE_ACTION, mapOf(AnalyticsProperty.ACTION to "taken")),
            ),
        )

        assertTrue(delivered)
        assertEquals(
            listOf(
                "event:screen_view:{screen=history}",
                "event:intake_action:{action=taken}",
                "flush",
            ),
            sdk.calls,
        )
    }

    @Test
    fun `every catalog event reaches the SDK under its wire name`() {
        assertEquals(13, AnalyticsEventName.entries.size)

        val transport = transport()
        transport.start()
        sdk.calls.clear()

        AnalyticsEventName.entries.forEach { name ->
            assertTrue(transport.deliver(listOf(record(name))))
        }

        val sent = sdk.calls.filter { it.startsWith("event:") }
            .map { it.removePrefix("event:").substringBefore(':') }
        assertEquals(AnalyticsEventName.entries.map { it.wire }, sent)
    }

    @Test
    fun `an empty batch touches nothing and does not open the SDK`() {
        val transport = transport()

        assertFalse(transport.deliver(emptyList()))
        assertTrue(sdk.calls.isEmpty())

        transport.start()
        sdk.calls.clear()
        assertFalse(transport.deliver(emptyList()))
        assertTrue(sdk.calls.isEmpty())
    }

    @Test
    fun `a refusal from the SDK is swallowed and reported as not delivered`() {
        val transport = transport()
        transport.start()

        sdk.failOnEvent = true

        assertFalse(transport.deliver(listOf(record(AnalyticsEventName.SCREEN_VIEW))))
        assertFalse(transport.deliver(listOf(record(AnalyticsEventName.SCREEN_VIEW))))
    }

    @Test
    fun `after stop the SDK receives not a single call`() {
        val transport = transport()
        transport.start()
        sdk.calls.clear()

        transport.stop()

        assertFalse(transport.deliver(listOf(record(AnalyticsEventName.SCREEN_VIEW))))
        assertTrue(sdk.calls.isEmpty())
    }

    @Test
    fun `opening the transmission again resumes delivery without a second init`() {
        val transport = transport()
        transport.start()
        transport.stop()
        transport.start()

        assertEquals(1, sdk.calls.count { it.startsWith("init:") })
        sdk.calls.clear()

        assertTrue(transport.deliver(listOf(record(AnalyticsEventName.SCREEN_VIEW))))
        assertEquals(listOf("event:screen_view:{}", "flush"), sdk.calls)
    }

    private fun record(name: AnalyticsEventName, properties: Map<AnalyticsProperty, String> = emptyMap()) =
        AnalyticsRecord(
            name = name,
            properties = properties,
            occurredAt = Instant.parse("2026-11-01T10:00:00Z"),
            scope = AnalyticsScope.PRODUCT,
        )

    /** Журнал вызовов вместо SDK: тест судит о порядке и о том, чего не было. */
    private class RecordingSdk : MyTrackerSdk {

        val calls = mutableListOf<String>()

        var failOnEvent = false

        override fun init(apiKey: String) {
            calls += "init:$apiKey"
        }

        override fun configure(bufferingPeriodSeconds: Int, trackingLaunchEnabled: Boolean, debug: Boolean) {
            calls += "configure:$bufferingPeriodSeconds:$trackingLaunchEnabled"
        }

        override fun trackEvent(name: String, properties: Map<String, String>) {
            if (failOnEvent) error("provider refused")
            calls += "event:$name:${properties.entries.joinToString(",", "{", "}") { it.key + "=" + it.value }}"
        }

        override fun trackLaunchManually(activity: android.app.Activity) = Unit

        override fun flush() {
            if (failOnEvent) error("provider refused")
            calls += "flush"
        }
    }

    private companion object {
        const val KEY = "test-key"
    }
}
