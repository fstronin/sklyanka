package ru.stohastika.sklyanka.gradle

/**
 * Правила состава артефакта (SKL-82 → SKL-100, ПЛ-3/ПЛ-4, ПМ-31).
 *
 * Одно место, где записано, чему разрешено ехать в сборке: запрещённые группы,
 * белый список сетевых координат и сетевые разрешения. Задачи проверки манифеста
 * и графа зависимостей читают эти правила, поэтому разрешение и зависимости
 * не могут разойтись.
 *
 * ПМ-31: в первой публикации сетевого контура нет — Huawei-аналитика и A/B убраны.
 * Белый список и обязательные координаты пусты: любая `com.huawei.*` валит сборку,
 * пока будущий контур (SKL-50, H2) не внесёт свои координаты осознанно. `INTERNET`
 * возвращается в запрещённые, как до ПМ-19.
 */
object AnalyticsContourRules {

    /** Обязательных координат у релиза нет: сетевого контура в первой публикации нет (ПМ-31). */
    val REQUIRED_COORDINATES = emptyList<String>()

    /**
     * Белый список сетевых координат — данные, а не логика.
     *
     * Пуст осознанно: координаты будущих контуров вносятся вместе с их задачами.
     * Любая новая координата `com.huawei.*` (и вообще `com.huawei.*`) валит сборку,
     * пока её не внесут сюда; реклама, GMS и чужая аналитика запрещены отдельно.
     */
    val ALLOWED_COORDINATES = emptyList<String>()

    /** Запрещённые группы: GMS, Firebase, реклама, чужая аналитика и крашлитика (ПЛ-4, НФТ-16/39). */
    val FORBIDDEN_GROUPS = listOf(
        "com.google.android.gms",
        "com.google.firebase",
        "com.google.android.play",
        "com.crashlytics",
        "io.fabric",
        "com.google.mlkit",
    )

    /**
     * Сетевые разрешения запрещены: релиз без сетевого контура (ПЛ-3, НФТ-15, ПМ-31).
     *
     * `com.google.gms.permission.AD_ID` — тоже запрещено: рекламные идентификаторы
     * не собираются ни при каких условиях (ПМ-17 п. 4).
     */
    val FORBIDDEN_PERMISSIONS = listOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "com.google.gms.permission.AD_ID",
    )

    /** Разрешена ли координата: точная или по началу (модули одной публикации). */
    fun allows(coordinate: String): Boolean = ALLOWED_COORDINATES.any { coordinate.startsWith(it) }
}
