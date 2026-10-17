package ru.stohastika.sklyanka.pro

import androidx.compose.runtime.Composable
import ru.stohastika.sklyanka.domain.pro.ProPoint

/**
 * Витрина «Склянки Про» на стороне приложения (SKL-153, `ADR-0016` п. 2–3).
 *
 * Единственная точка `app`, знающая о `feature-pro`. В `free` реализации нет вовсе
 * (`FreeProHost`), поэтому платёжный модуль не попадает в граф зависимостей бесплатной
 * сборки; в `pro` (`ProHostImpl`) здесь живут шторка, её `ViewModel`, возврат и проверка
 * подписки. Экранам такое разделение незаметно: они просят [Content] и передают
 * [ProPoint] в `openPro`, а что за этим стоит, решает контур.
 *
 * `onClosed` вызывается, только когда шторка действительно закрылась: настройки, форма
 * лекарства, мастер профиля и отчёт обязаны перечитать доступ, не теряя набранное (Р-84,
 * Р-85). В `free` доступа не отнимает никто, и обработчик не вызывается ни разу.
 */
interface ProHost {

    /** Есть ли в этом контуре витрина: `false` в `free`, `true` в `pro`. */
    val enabled: Boolean

    /**
     * Содержимое приложения с открывателем витрины.
     *
     * @param onPolicy открыть документ «Условия и политика» — адрес и браузер живут в `app`.
     * @param onClosed шторка закрылась; в `free` не вызывается.
     * @param content композиция экранов, получающая `openPro` — открыватель витрины
     *   из точки-повода. В `free` он ничего не делает: платных границ нет.
     */
    @Composable
    fun Content(onPolicy: () -> Unit, onClosed: () -> Unit, content: @Composable (openPro: (ProPoint) -> Unit) -> Unit)
}
