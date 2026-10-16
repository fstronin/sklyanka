package ru.stohastika.sklyanka.acceptance.report

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.history.AndroidPdfTextMetrics
import ru.stohastika.sklyanka.feature.history.PdfPagePainter
import ru.stohastika.sklyanka.feature.history.ReportPdfRole
import ru.stohastika.sklyanka.ui.token.darkColors
import ru.stohastika.sklyanka.ui.token.lightColors

/**
 * Экранные шаги приёмки SKL-120 — документ отчёта остаётся светлым (§9, §5.15).
 *
 * Собирается **настоящий** слой документа ([AndroidPdfTextMetrics]) вместе с его
 * умолчаниями: цвета документа назначает он сам, а не стенд, — и проверка говорит именно
 * об этом. Документ берёт роли светлой среды базовой темы, потому что его печатают
 * и пересылают: тёмная бумага в письме врачу читалась бы как ошибка, а тема интерфейса —
 * украшение экрана, а не свойство документа.
 *
 * Выгрузка истории (CSV) в проверке не участвует намеренно: это таблица без красок,
 * и «светлой» её делает не палитра, а отсутствие оформления вовсе.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class Sk120DocumentSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var metrics: AndroidPdfTextMetrics? = null

    /** Слой страницы: заливка полотна берётся у него, а не у токена по месту. */
    private var painter: PdfPagePainter? = null

    @Когда("^на стенде документа собран слой выгрузки$")
    fun theDocumentLayerIsBuilt() {
        // Умолчания продукта: стенд ничего не подставляет — иначе проверялось бы то,
        // что стенд и положил (Р-35, §5.15).
        metrics = AndroidPdfTextMetrics(context)
        painter = PdfPagePainter(requireNotNull(metrics))
        assertTrue(
            "the scenario must run in the dark environment: the flag comes from the system (Р-23)",
            world.system.darkTheme,
        )
    }

    @Тогда("^на стенде бумага документа белая$")
    fun thePaperIsWhite() {
        // Заливка полотна — то, чем документ и остаётся светлым: её берёт тот же слой
        // страницы, что печатает отчёт, а не токен по месту (§9).
        val paper = paperColour(requireNotNull(painter))
        assertEquals("the document paper is the light surface of the base theme (§9)", WHITE, paper.toArgb())
        assertNotEquals(
            "the paper must not follow the dark environment: the document is not repainted (§9)",
            paper,
            darkColors().surface,
        )
    }

    /** Цвет полотна страницы: его держит слой печати — читаем то, чем он и заливает. */
    private fun paperColour(page: PdfPagePainter): Color {
        val field = PdfPagePainter::class.java.getDeclaredField("colors").apply { isAccessible = true }
        val colours = field.get(page) as ru.stohastika.sklyanka.ui.token.SklyankaColors
        return colours.surface
    }

    @Тогда("^на стенде чернила документа — роли светлой среды, а не тёмной$")
    fun theInkIsTheLightRoles() {
        val layer = requireNotNull(metrics) { "the scenario must build the document layer first" }
        val light = lightColors()
        val dark = darkColors()
        ReportPdfRole.entries.forEach { role ->
            val expected = when (role) {
                ReportPdfRole.HEADER_RULE -> light.accent
                ReportPdfRole.DIVIDER -> light.line
                ReportPdfRole.META,
                ReportPdfRole.BODY_MUTED,
                ReportPdfRole.SECTION,
                ReportPdfRole.FOOTER,
                -> light.textMuted
                else -> light.text
            }
            assertEquals(
                "the document role $role must carry the light ink (§9)",
                expected.toArgb(),
                layer.paint(role).color,
            )
        }
        assertNotEquals(
            "the document must not be repainted by the dark environment (§9)",
            dark.text.toArgb(),
            layer.paint(ReportPdfRole.BODY).color,
        )
    }

    private companion object {
        const val WHITE = -0x1
    }
}

/**
 * Экранный прогон приёмки SKL-120 — светлый документ отчёта.
 *
 * Модуль свой: слой документа живёт в `feature-history`, и умолчания его красок видит
 * только он. Группа `@screen and @document` отсекает сценарии других стендов.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk120DocumentAcceptanceTest {

    @Test
    fun theDocumentScenariosOfSk120RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-120"

        const val TAGS = "@screen and @document"
    }
}
