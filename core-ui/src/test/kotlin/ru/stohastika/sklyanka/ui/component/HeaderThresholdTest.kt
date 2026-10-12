package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing

/**
 * Правило порога шапки (§4.9, ОВ-133) — чистая JVM-проверка.
 *
 * Почему проверка живёт здесь, а не на композиционном стенде: границу «равно» на стенде
 * измеряет один пиксель прокрутки, и она легко становится недостижимой (`TDD.md §17.3`).
 * Здесь же проверяется то, что композиция не видит: порог — константа каркаса, а не
 * измеренная грань ряда, поэтому признак не зависит от высоты ряда в покое (§4.1).
 */
class HeaderThresholdTest {

    @Test
    fun `a title row above the boundary keeps the header expanded`() {
        val threshold = boundary()

        assertFalse(
            "the title still hangs below the row face by one pixel: nothing has to collapse yet",
            headerCollapsed(threshold + 1f, threshold),
        )
    }

    @Test
    fun `the title row reaching the boundary collapses the header`() {
        val threshold = boundary()

        assertTrue(
            "the boundary itself belongs to the collapsed state, otherwise the large title " +
                "stays covered while the compact one is still absent (§4.1)",
            headerCollapsed(threshold, threshold),
        )
    }

    @Test
    fun `a title row past the boundary collapses the header`() {
        assertTrue(headerCollapsed(boundary() - HALF_ROW_PX, boundary()))
    }

    @Test
    fun `the boundary is the row face in its collapsed geometry`() {
        assertEquals(
            "the boundary is the status inset plus the pinned row height, taken by token (§4.1)",
            STATUS_INSET_PX + HEADER_ROW_PX.toFloat(),
            headerThresholdPx(FRAME_TOP_PX, STATUS_INSET_PX, HEADER_ROW_PX),
            0f,
        )
    }

    @Test
    fun `the boundary follows the top of the frame area and the system inset`() {
        // Каркас живёт ниже полосы профиля, а инсет системной панели известен только на
        // устройстве: оба слагаемых входят в порог, поэтому координаты окна сокращаются (§4.1, §5).
        assertEquals(
            "the frame area shifted down by the stripe moves the whole boundary",
            boundary() + STRIPE_PX,
            headerThresholdPx(FRAME_TOP_PX + STRIPE_PX, STATUS_INSET_PX, HEADER_ROW_PX),
            0f,
        )
        assertEquals(
            "a taller system panel moves the boundary by the same amount",
            boundary() + STRIPE_PX,
            headerThresholdPx(FRAME_TOP_PX, STATUS_INSET_PX + STRIPE_PX.toInt(), HEADER_ROW_PX),
            0f,
        )
    }

    @Test
    fun `the boundary does not depend on how tall the row is at rest`() {
        // Ряд в покое: у корневого экрана нулевой, у вложенного — 56 dp (§8.1). Порог один на
        // оба случая: он берётся токеном, а не измеренной гранью ряда.
        //
        // Если бы каркас мерил ряд, корневой экран сжимался бы на уровне инсета статус-бара:
        // крупный заголовок ушёл бы за самый верх экрана, а появившаяся полоса 56 dp закрыла
        // бы уже читаемое содержимое под ней (§4.1).
        val rootRestFace = FRAME_TOP_PX + STATUS_INSET_PX + with(DENSITY) { SklyankaSpacing.None.roundToPx() }
        val threshold = boundary()
        val titleRowBottom = STATUS_INSET_PX + HALF_ROW_PX

        assertNotEquals("the resting face and the token face really differ", rootRestFace, threshold)
        assertTrue(
            "the token boundary collapses the header as soon as the title reaches the row face",
            headerCollapsed(titleRowBottom, threshold),
        )
        assertFalse(
            "the measured resting face would keep the header expanded while the row already covers readable content",
            headerCollapsed(titleRowBottom, rootRestFace),
        )
    }

    private fun boundary(): Float = headerThresholdPx(FRAME_TOP_PX, STATUS_INSET_PX, HEADER_ROW_PX)

    private companion object {
        /** Плотность 1: пиксели совпадают с dp, сравнения читаются без пересчёта. */
        val DENSITY = Density(density = 1f)

        const val FRAME_TOP_PX = 0f
        const val STATUS_INSET_PX = 24

        /** Полоса профиля (§4.10) — то, что стоит над областью каркаса. */
        const val STRIPE_PX = 6f

        val HEADER_ROW_PX: Int = with(DENSITY) { SklyankaSizes.HeaderCompactRowHeight.roundToPx() }
        val HALF_ROW_PX: Float = HEADER_ROW_PX / 2f
    }
}
