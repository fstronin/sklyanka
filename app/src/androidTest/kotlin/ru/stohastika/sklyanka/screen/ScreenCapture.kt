package ru.stohastika.sklyanka.screen

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import java.io.File

/**
 * Кадр окна приложения на настоящем устройстве (SKL-96).
 *
 * Кадр — свидетельство для человека, а не эталон (TDD §6): ни хешей, ни порогов,
 * ни сверки с образцом здесь нет и не заводится. Пишется во внутренний каталог
 * приложения, откуда его одинаково надёжно забирают и `adb root` (образ AOSP),
 * и `run-as` отладочной сборки (`tools/emulator/screenshots.sh`).
 *
 * Кадр не включает системные панели и оформление прошивки: это рендер композиции
 * приложения, и притворяться, что он показывает EMUI, нельзя.
 */
internal object ScreenCapture {

    private const val PNG_QUALITY = 100

    fun write(target: File, step: String, test: String, node: SemanticsNodeInteraction) {
        target.mkdirs()
        val bitmap = node.captureToImage().asAndroidBitmap()
        val file = File(target, "${step}__$test.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    }
}
