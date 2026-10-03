package ru.stohastika.sklyanka.gradle

/**
 * Правила аналитического контура (SKL-82, ADR-0011, ПЛ-3/ПЛ-4, НФТ-15/16).
 *
 * Одно место, где записано, что такое «аналитический контур»: обязательные координаты,
 * белый список и запрещённые группы. Задачи проверки манифеста и графа зависимостей
 * читают эти правила, поэтому разрешение и зависимости не могут разойтись.
 */
object AnalyticsContourRules {

    /** Координата самого контура: Huawei Analytics Kit. Без неё `INTERNET` объявлять нечем. */
    val REQUIRED_COORDINATES = listOf("com.huawei.hms:hianalytics")

    /**
     * Белый список координат — данные, а не логика: SKL-83 дополняет его координатами
     * A/B Testing и Remote Configuration, не переписывая проверку.
     *
     * Здесь только транзитивное замыкание публикации Analytics Kit 6.12.0.301: базовые
     * модули HMS, AppGallery Connect, криптография и задачи. Любая новая координата
     * `com.huawei.*` валит сборку, пока её не внесут сюда осознанно; реклама, push и
     * прочие сервисы HMS так в сборку не просочатся.
     */
    val ALLOWED_COORDINATES = listOf(
        "com.huawei.hms:hianalytics",
        "com.huawei.hms:base",
        "com.huawei.hms:baselegacyapi",
        "com.huawei.hms:availableupdate",
        "com.huawei.hms:device",
        "com.huawei.hms:hatool",
        "com.huawei.hms:log",
        "com.huawei.hms:opendevice",
        "com.huawei.hms:stats",
        "com.huawei.hms:ui",
        "com.huawei.hms:network-",
        "com.huawei.agconnect",
        // A/B-контур (SKL-83, ПМ-20): перечислены точно, чтобы отчёт гейтов и приёмка
        // называли состав контура. Обязательными не становятся: состав A/B первой
        // публикации решает заказчик, аналитика выходит независимо (ПМ-20 п. 6).
        "com.huawei.agconnect:agconnect-remoteconfig",
        "com.huawei.agconnect:agconnect-abtest",
        "com.huawei.android.hms:security-",
        "com.huawei.hmf:tasks",
    )

    /** Запрещённые группы: GMS, Firebase, реклама, чужая аналитика и крашлитика (ПЛ-4, НФТ-16/39). */
    val FORBIDDEN_GROUPS = listOf(
        "com.google.android.gms",
        "com.google.firebase",
        "com.google.android.play",
        "com.crashlytics",
        "io.fabric",
        "com.google.mlkit",
    )

    /** Разрешение аналитического контура: обязательно — иначе SDK не работает (ПМ-19). */
    const val REQUIRED_PERMISSION = "android.permission.INTERNET"

    /**
     * Разрешения состояния сети запрещены: сеть принадлежит контуру, а не продукту.
     * `com.google.gms.permission.AD_ID` — тоже запрещено: рекламные идентификаторы
     * не собираются ни при каких условиях (ПМ-17 п. 4).
     */
    val FORBIDDEN_PERMISSIONS = listOf(
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "com.google.gms.permission.AD_ID",
    )

    /** Разрешена ли координата: точная или по началу (модули одной публикации). */
    fun allows(coordinate: String): Boolean = ALLOWED_COORDINATES.any { coordinate.startsWith(it) }
}
