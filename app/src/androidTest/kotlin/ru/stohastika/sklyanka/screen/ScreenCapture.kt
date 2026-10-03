package ru.stohastika.sklyanka.screen

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ComposeTimeoutException
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
 *
 * Разовый таймаут RenderThread (`ComposeTimeoutException` внутри `captureToImage`) —
 * не повод ронять проверку: кадр переснимается, до [ATTEMPTS] попыток (SKL-98,
 * флак `ReminderCardDeviceTest`). Пауза между попытками — не сон, а ожидание простоя,
 * переданное вызывающим ([awaitRetry]); при исчерпании попыток падение остаётся
 * честным и называет шаг.
 */
internal object ScreenCapture {

    private const val PNG_QUALITY = 100

    /** Три попытки — тот же предел, что у соседних ожиданий проверок. */
    private const val ATTEMPTS = 3

    /**
     * @param awaitRetry ожидание простоя между попытками: проверка передаёт `waitForIdle`,
     *   чтобы повтор не был занятым циклом и не читал системные часы (НФТ-48).
     */
    fun write(target: File, step: String, test: String, node: SemanticsNodeInteraction, awaitRetry: () -> Unit = {}) {
        target.mkdirs()
        val file = File(target, "${step}__$test.png")
        var lastFailure: ComposeTimeoutException? = null
        repeat(ATTEMPTS) { attempt ->
            if (attempt > 0) awaitRetry()
            try {
                val bitmap = node.captureToImage().asAndroidBitmap()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
                return
            } catch (failure: ComposeTimeoutException) {
                lastFailure = failure
            }
        }
        throw AssertionError(
            "Кадр «$step» не снят за $ATTEMPTS попытки: RenderThread не отдал изображение. " +
                "Последняя ошибка: ${lastFailure?.message}",
            lastFailure,
        )
    }
}
