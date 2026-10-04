package ru.stohastika.sklyanka.acceptance.steps.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Разбор состояний из исходника каталога (SKL-24).
 *
 * Проверка эталонов читает состояния из кода, а не из списка в тесте: пропущенное
 * состояние обязано валить приёмку, а проза KDoc — не притворяться состоянием.
 * Кейс с комментариями внутри перечисления заведён нарочно: в настоящем
 * `ComponentStates.kt` такой комментарий есть, и без вырезания комментариев он
 * давал фальшивое состояние без эталона.
 */
class ComponentStateParsingTest {

    @Test
    fun `block and line comments inside an enum are not states`() {
        val source = """
            enum class ScreenHeaderKind {
                /**
                 * Modal: cancel on the left, title in the middle (§4.9, OV-146).
                 */
                Modal, // the only modal kind

                /* Nested: back arrow on the left. */
                Nested,
            }
        """.trimIndent()

        assertEquals(listOf("modal", "nested"), componentStatesOf(source))
    }

    @Test
    fun `a value after a comment stays visible`() {
        val source = """
            enum class BannerKind {
                /** Stock left. */
                Warning, // shown above the list
            }
        """.trimIndent()

        assertEquals(listOf("warning"), componentStatesOf(source))
    }

    @Test
    fun `the catalogue yields every declared state and no comment fragment`() {
        assertEquals(CATALOG_STATES, componentStates())
    }

    private companion object {
        /** Состояния каталога на момент правки: на каждое проверка эталонов требует снимок. */
        val CATALOG_STATES = listOf(
            "pending", "duenow", "taken", "missed", "asneeded",
            "normal", "pressed", "disabled", "busy",
            "primary", "danger",
            "accent", "muted", "warning",
            "row", "header",
            "info", "unselected", "selected",
            "empty", "filled", "focused", "error",
            "on", "off",
            "profile", "neutral",
            "day", "course", "stock", "stockcritical",
            "root", "nested", "modal",
        )
    }
}
