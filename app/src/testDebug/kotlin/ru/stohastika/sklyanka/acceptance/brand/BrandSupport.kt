package ru.stohastika.sklyanka.acceptance.brand

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Опора шагов приёмки знака (SKL-21): снимок в память, пиксели, контраст, чтение ресурсов.
 *
 * Общая для двух классов шагов и шагами не является: `ru.stohastika.sklyanka.acceptance.brand`
 * — пакет шагов задачи, и вспомогательные функции живут рядом с ними, а не в общем
 * артефакте приёмки: этим проверкам мир сценария и общий словарь не нужны.
 */

// --- Снимок и пиксели ------------------------------------------------------------------------

/** Снять композицию в память: свои снимки шаги никуда не пишут. */
internal fun bitmapOf(widthDp: Int, heightDp: Int, content: @Composable () -> Unit): Bitmap {
    val target = File.createTempFile("sklyanka-brand", ".png")
    try {
        captureRoboImage(
            file = target,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            roborazziComposeOptions = RoborazziComposeOptions { size(widthDp = widthDp, heightDp = heightDp) },
            content = content,
        )
        return requireNotNull(BitmapFactory.decodeFile(target.path)) { "the composition was not rendered" }
    } finally {
        target.delete()
    }
}

/** Цвет пикселя с координатами в пикселях снимка. */
internal fun Bitmap.colourAt(x: Int, y: Int): Int = getPixel(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))

/** Расхождение двух цветов по каналам: цвет фона и краска различаются заметно. */
internal fun colourGap(first: Int, second: Int): Int = maxOf(
    kotlin.math.abs(Color.red(first) - Color.red(second)),
    kotlin.math.abs(Color.green(first) - Color.green(second)),
    kotlin.math.abs(Color.blue(first) - Color.blue(second)),
)

/** Есть ли в прямоугольнике пиксель, близкий к цвету: краска слоя на снимке. */
internal fun Bitmap.hasColourNear(bounds: Rect, target: Int, tolerance: Int): Boolean {
    for (x in bounds.left.toInt()..bounds.right.toInt()) {
        for (y in bounds.top.toInt()..bounds.bottom.toInt()) {
            if (colourGap(colourAt(x, y), target) <= tolerance) return true
        }
    }
    return false
}

// --- Контраст (НФТ-32) -----------------------------------------------------------------------

/** Относительная яркость sRGB по WCAG 2.1. */
internal fun relativeLuminance(argb: Int): Double {
    fun channel(component: Int): Double {
        val value = component / 255.0
        return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(Color.red(argb)) +
        0.7152 * channel(Color.green(argb)) +
        0.0722 * channel(Color.blue(argb))
}

/** Отношение контраста пары цветов: 1.0 — цвета совпали, 21.0 — предел. */
internal fun contrastRatio(first: Int, second: Int): Double {
    val a = relativeLuminance(first)
    val b = relativeLuminance(second)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

/** Токен палитры как ARGB: знак и заставка читают цвета ролями, а не строками. */
internal fun argb(composeColour: androidx.compose.ui.graphics.Color): Int = composeColour.toArgb()

// --- Репозиторий: ресурсы и вызовы знака -----------------------------------------------------

/** Корень репозитория: его задаёт конвенция экранных проверок. */
internal fun brandRepoRoot(): File = File(
    requireNotNull(System.getProperty("sklyanka.repo.root")) {
        "sklyanka.repo.root is not set: the screen test convention must be applied to this module"
    },
)

/** Строка исходника с местом: для структурных проверок «вариант не выбирают по месту». */
internal data class SourceLine(val file: String, val number: Int, val text: String) {
    override fun toString(): String = "$file:$number: $text"
}

/** Все исходники продукта из наборов исходников — по ним ищутся вызовы знака. */
internal fun productSources(): List<File> = brandRepoRoot().listFiles()
    ?.filter { it.isDirectory && it.name != "build" }
    ?.map { module -> File(module, "src/main/kotlin") }
    ?.filter { it.isDirectory }
    ?.flatMap { directory -> directory.walkTopDown().filter { it.extension == "kt" }.toList() }
    ?.sortedBy { it.path }
    .orEmpty()

/**
 * Вызовы компонентов знака: выражения целиком, где знак просят показать.
 *
 * Выражение, а не строка: вызов знака многострочный, и «есть ли размер» по одной строке
 * не проверить. Объявления компонентов (`fun SklyankaMark(`) не в счёт — это не экран.
 */
internal fun markCallSites(): List<SourceLine> = productSources().flatMap { file ->
    val text = file.readText()
    CALL_NAMES.flatMap { name ->
        generateSequence(text.indexOf(name)) { next -> text.indexOf(name, next + 1) }
            // `indexOf` возвращает -1, а не null: без остановки последовательность бесконечна.
            .takeWhile { index -> index >= 0 }
            .filter { index -> index > 0 && text.substring(maxOf(0, index - 4), index) != "fun " }
            .map { index ->
                SourceLine(
                    file = file.name,
                    number = text.take(index).count { it == '\n' } + 1,
                    text = callExpression(text, index + name.length - 1),
                )
            }
            .toList()
    }
}.distinct()

/** Выражение вызова от открывающей скобки до парной закрывающей. */
private fun callExpression(text: String, openParen: Int): String {
    var depth = 0
    var index = openParen
    while (index < text.length) {
        when (text[index]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) return text.substring(openParen, index + 1).replace(WHITESPACE, " ")
            }
        }
        index++
    }
    return text.substring(openParen)
}

private val CALL_NAMES = listOf("SklyankaMark(", "FlatMark(")
private val WHITESPACE = Regex("\\s+")

/** Файлы, в которых встречается выражение: имя файла и текст. */
internal fun filesContaining(needle: String): List<String> =
    productSources().filter { it.readText().contains(needle) }.map { it.name }.distinct().sorted()

/** Значение цвета из `values/colors.xml` приложения: имя токена → ARGB. */
internal fun appColour(name: String): Int {
    val colors = File(brandRepoRoot(), APP_COLORS).readText()
    val match = requireNotNull(Regex("""<color name="$name">#([0-9A-Fa-f]{6,8})</color>""").find(colors)) {
        "the colour $name is not declared in $APP_COLORS"
    }
    val hex = match.groupValues[1]
    return Color.parseColor("#$hex")
}

/** Вариант иконки приложения: подложка, краска знака и тёплая дуга. */
internal data class IconVariant(val folder: String, val tile: Int, val ink: Int, val warmArc: String)

/**
 * Вариант иконки из ресурсов приложения.
 *
 * `drawable` — вариант для светлой системной темы (тёмная плитка), `drawable-night` —
 * для тёмной (светлая плитка). Читаются настоящие файлы: подложка, передний план и слой
 * знака; цвета разрешаются по именам токенов, а не по литералам шага.
 */
internal fun iconVariant(folder: String): IconVariant {
    val root = brandRepoRoot()
    val background = File(root, "app/src/main/res/$folder/ic_launcher_background.xml").readText()
    val foreground = File(root, "app/src/main/res/$folder/ic_launcher_foreground.xml").readText()
    val tile = colourTokenOf(background, "fillColor", folder)
    val ink = colourTokenOf(foreground, "strokeColor", folder)
    return IconVariant(folder, appColour(tile), appColour(ink), warmArcOf(foreground, folder))
}

/** Имя цветового токена в атрибуте ресурса: `attribute="@color/имя"`. */
private fun colourTokenOf(xml: String, attribute: String, folder: String): String {
    val marker = attribute + "=\"@color/"
    val at = xml.indexOf(marker)
    require(at >= 0) { "the icon $folder has no $attribute colour token" }
    return xml.substring(at + marker.length).substringBefore('"')
}

/** Тёплая дуга: путь со штрихом акцентного токена — в обоих вариантах иконки. */
private fun warmArcOf(foreground: String, folder: String): String {
    val marker = "strokeColor=\"@color/mark_accent\""
    val at = foreground.indexOf(marker)
    require(at >= 0) { "the icon foreground of $folder has no warm arc" }
    return foreground.substring(foreground.lastIndexOf("<path", at), foreground.indexOf("/>", at) + 2)
}

internal const val APP_COLORS = "app/src/main/res/values/colors.xml"
