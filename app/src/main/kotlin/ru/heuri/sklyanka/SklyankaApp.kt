package ru.heuri.sklyanka

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Точка сборки графа зависимостей.
 *
 * На этом этапе в графе ровно одна привязка — источник времени. Граф заложен
 * в скелет намеренно: добавленный в готовый проект, он требует правки каждого
 * модуля; заложенный сразу — не требует ничего (TDD §7).
 */
@HiltAndroidApp
class SklyankaApp : Application()
