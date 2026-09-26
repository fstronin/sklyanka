package ru.heuri.sklyanka.detekt

import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Проверка самих правил (TDD §5.2).
 *
 * На каждое правило — нарушающий фрагмент и чистый фрагмент: правило обязано
 * и срабатывать, и молчать там, где нарушения нет. Иначе его начнут отключать.
 */
@DisplayName("Sklyanka project rules fire on violations and stay silent otherwise")
class SklyankaRulesTest {

    @Test
    fun `design token rule rejects dimension and color literals outside the token package`() {
        val findings = HardcodedDesignToken(TOKEN_CONFIG).lint(
            """
            package ru.heuri.sklyanka.feature.today

            val padding = 16.dp
            val accent = Color(0xFFB4512F)
            """.trimIndent(),
        )

        assertEquals(2, findings.size, findings.joinToString { it.message })
    }

    @Test
    fun `design token rule allows literals inside the design system package`() {
        val findings = HardcodedDesignToken(TOKEN_CONFIG).lint(
            """
            package ru.heuri.sklyanka.ui.token

            val padding = 16.dp
            val accent = Color(0xFFB4512F)
            """.trimIndent(),
        )

        assertTrue(findings.isEmpty(), findings.joinToString { it.message })
    }

    @Test
    fun `russian text rule rejects a cyrillic string literal`() {
        val findings = RussianTextInCode().lint(
            """
            package ru.heuri.sklyanka.feature.today

            const val LABEL = "Принято"
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `russian text rule accepts latin text`() {
        val findings = RussianTextInCode().lint(
            """
            package ru.heuri.sklyanka.feature.today

            const val TAG = "today_screen"
            """.trimIndent(),
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `clock rule rejects direct wall clock access`() {
        val findings = DirectSystemClockAccess().lint(
            """
            package ru.heuri.sklyanka.domain.intake

            fun startedAt() = System.currentTimeMillis()
            fun today() = LocalDate.now()
            fun zone() = ZoneId.systemDefault()
            """.trimIndent(),
        )

        assertEquals(3, findings.size, findings.joinToString { it.message })
    }

    @Test
    fun `clock rule rejects a fully qualified clock call`() {
        val findings = DirectSystemClockAccess().lint(
            """
            package ru.heuri.sklyanka.domain.intake

            fun today() = java.time.LocalDate.now()
            """.trimIndent(),
        )

        assertEquals(1, findings.size, findings.joinToString { it.message })
    }

    @Test
    fun `clock rule accepts time taken from the injected source`() {
        val findings = DirectSystemClockAccess().lint(
            """
            package ru.heuri.sklyanka.domain.intake

            fun startedAt(time: TimeSource) = time.now()
            """.trimIndent(),
        )

        assertTrue(findings.isEmpty(), findings.joinToString { it.message })
    }

    @Test
    fun `room rule rejects a destructive migration`() {
        val findings = DestructiveRoomMigration().lint(
            """
            package ru.heuri.sklyanka.data.room

            fun build(builder: Builder) = builder.fallbackToDestructiveMigration().build()
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `domain rule rejects an android import in the domain package`() {
        val findings = AndroidImportInDomain().lint(
            """
            package ru.heuri.sklyanka.domain.intake

            import android.os.Build

            val sdk = Build.VERSION.SDK_INT
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `domain rule ignores an android import outside the domain package`() {
        val findings = AndroidImportInDomain().lint(
            """
            package ru.heuri.sklyanka.data.room

            import android.os.Build

            val sdk = Build.VERSION.SDK_INT
            """.trimIndent(),
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `text style rule rejects a style built outside the token package`() {
        val findings = HardcodedTextStyle(TOKEN_CONFIG).lint(
            """
            package ru.heuri.sklyanka.feature.today

            val title = TextStyle(fontSize = 20.sp)
            val caption = SpanStyle(fontWeight = FontWeight.Medium)
            """.trimIndent(),
        )

        assertEquals(2, findings.size, findings.joinToString { it.message })
    }

    @Test
    fun `text style rule allows the scale itself and edits of a scale style`() {
        val insideScale = HardcodedTextStyle(TOKEN_CONFIG).lint(
            """
            package ru.heuri.sklyanka.ui.token

            val title = TextStyle(fontSize = 20.sp)
            """.trimIndent(),
        )
        val editOfScale = HardcodedTextStyle(TOKEN_CONFIG).lint(
            """
            package ru.heuri.sklyanka.feature.today

            val title = SklyankaType.Body.copy(color = colors.text)
            """.trimIndent(),
        )

        assertTrue(insideScale.isEmpty(), insideScale.joinToString { it.message })
        assertTrue(editOfScale.isEmpty(), editOfScale.joinToString { it.message })
    }

    private companion object {
        val TOKEN_CONFIG = TestConfig("allowedPackages" to "ru.heuri.sklyanka.ui.token")
    }
}
