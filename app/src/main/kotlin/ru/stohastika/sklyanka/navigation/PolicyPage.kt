package ru.stohastika.sklyanka.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import ru.stohastika.sklyanka.feature.settings.policyUrl

/**
 * Открытие документа «Условия и политика» (SKL-122, ОВ-123).
 *
 * Одна точка на весь продукт: строку «Условия и политика» несут и настройки, и шторка
 * согласия, и у каждой из них нет ни браузера, ни адреса (ADR-0007). Адрес берётся из того
 * же ресурса, что и в настройках, — второй копии адреса в дереве не заводится.
 *
 * Устройство без браузера — обычный исход на урезанной прошивке, а не сбой приложения:
 * проверка обработчика и перехват отказа, как у остальных системных переходов (SKL-46).
 */
object PolicyPage {

    private const val TAG = "PolicyPage"

    /** Открыть документ системным браузером; `false` — показывать нечем. */
    fun open(context: Context): Boolean {
        val intent = Intent(
            Intent.ACTION_VIEW,
            policyUrl(context).let(Uri::parse),
        )
        // Экранный контекст открывает документ в своей задаче; контекст приложения
        // (фоновая точка) обязан попросить новую, иначе платформа откажет в запуске.
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            Log.i(TAG, "no application can show the policy page")
            return false
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.onFailure { error -> Log.i(TAG, "no application can show the policy page", error) }.getOrDefault(false)
    }
}
