package ru.stohastika.sklyanka.pro

import ru.stohastika.sklyanka.ui.pro.ProContour
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Контур интерфейса платной сборки (SKL-153, `ADR-0016` п. 2).
 *
 * `uiEnabled = true` включает метку «Про» и строку «Склянка Про» в настройках: в этом
 * контуре платные границы снова настоящие. Значение читают `ProMark`, настройки, `ProHost`
 * и экран настройки виджета — через один порт, чтобы `free` и `pro` расходились ровно
 * в одном месте.
 */
@Singleton
class ProContourImpl @Inject constructor() : ProContour {

    override val uiEnabled: Boolean = true
}
