package ru.stohastika.sklyanka.pro

import ru.stohastika.sklyanka.ui.pro.ProContour
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Контур интерфейса бесплатной сборки (Р-86, `ADR-0016` п. 4).
 *
 * `uiEnabled = false` скрывает метку «Про» и блок «Склянка Про»: платных границ в `free`
 * нет, и метка у бесплатной функции была бы ложью (П-7). Значение читают `ProMark`,
 * настройки и `ProHost`; у экрана настройки виджета оно приходит через тот же порт.
 */
@Singleton
class FreeProContour @Inject constructor() : ProContour {

    override val uiEnabled: Boolean = false
}
