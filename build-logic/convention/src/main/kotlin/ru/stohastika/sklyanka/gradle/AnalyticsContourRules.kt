package ru.stohastika.sklyanka.gradle

/**
 * Сетевой контур сборки (SKL-165, `ADR-0017` п. 1).
 *
 * Контур — это данные, а не код: модуль, его путь в графе, разрешения, которые он объявляет,
 * и координаты, без которых он не работает. Правила ([NetworkContourRules]) читают этот
 * список и не знают, сколько контуров заведено, поэтому третий контур добавляется здесь,
 * а не правкой проверок.
 */
data class NetworkContour(
    /** Имя модуля: по нему находится его манифест и его артефакт. */
    val module: String,
    /** Путь модуля в графе проектов. */
    val project: String,
    /** Разрешения, которые контур объявляет своим манифестом. */
    val permissions: List<String>,
    /** Координаты, без которых обещание контура не выполняется. */
    val requiredCoordinates: List<String>,
)

/**
 * Правила состава артефакта (SKL-82 → SKL-100 → SKL-122 → SKL-165, ПЛ-3/ПЛ-4, ПМ-26/31).
 *
 * Одно место, где записано, чему разрешено ехать в сборке: запрещённые группы, белый список
 * координат вендора и сетевых провайдеров, сетевые разрешения. Задачи проверки манифеста
 * и состава зависимостей читают эти правила, поэтому разрешение и зависимости не могут
 * разойтись.
 *
 * **Сетевых контуров два** (`ADR-0017` п. 1, Р-93 п. 4): аналитика (`core-analytics`,
 * MyTracker) и платёжный контур (`billing-appgallery`, AppGallery IAP). Правило «сеть
 * разрешена ровно вместе с платным контуром» больше не верно: `INTERNET` законен, когда его
 * объявил контур, входящий в артефакт, и никто вне контуров. Запреты GMS, Firebase, рекламы
 * и чужой аналитики сохраняются (ПЛ-4). Разрешение состояния сети принадлежит контуру
 * аналитики: без него SDK поставщика не отправляет события (Р-100).
 */
object AnalyticsContourRules {

    /**
     * Контур аналитики: реализация порта `AnalyticsTransport` поверх MyTracker.
     *
     * Сеть и признак её наличия: события уходят по HTTPS, а `ACCESS_NETWORK_STATE` нужен
     * самому SDK — без него он считает сеть недоступной и не отправляет события (Р-100).
     */
    val ANALYTICS_CONTOUR = NetworkContour(
        module = "core-analytics",
        project = ":core-analytics",
        permissions = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
        ),
        requiredCoordinates = listOf("com.my.tracker:mytracker-sdk"),
    )

    /**
     * Платёжный контур «Склянки Про»: клиент магазина AppGallery.
     *
     * Обязательная координата, а не просто разрешённая: контур без клиента — витрина,
     * которая никогда не покажет цену, то есть обещание покупки без покупки (П-7).
     */
    val PAID_CONTOUR = NetworkContour(
        module = "billing-appgallery",
        project = ":billing-appgallery",
        permissions = listOf("android.permission.INTERNET"),
        requiredCoordinates = listOf("com.huawei.hms:iap"),
    )

    /** Все сетевые контуры сборки. Порядок — порядок в отчётах, не приоритет. */
    val NETWORK_CONTOURS: List<NetworkContour> = listOf(ANALYTICS_CONTOUR, PAID_CONTOUR)

    /**
     * Группы, чьи координаты обязаны быть в белом списке, если оказались в артефакте.
     *
     * Проверяется каждая, а не только вендорская: иначе координата провайдера аналитики
     * и его транзитивное замыкание проехали бы мимо проверки состава молча, и обещание
     * «GMS/Firebase/рекламы нет» держалось бы только на честном слове.
     */
    val GUARDED_GROUPS = listOf("com.huawei", "com.my.tracker")

    /**
     * Белый список координат — данные, а не логика, и заполнен **осознанно**.
     *
     * Координаты вендора магазина — замыкание `com.huawei.hms:iap` (посчитано по POM
     * и подтверждено разрешением classpath): сам клиент, его база и то, что база тянет
     * за собой — настройки среды, телеметрия клиента, сетевая библиотека вендора, его же
     * криптография и hmf-задачи, на типе которых возвращаются ответы.
     *
     * **Названные следствия** (решение заказчика 15.10.2026, записано в пересмотре
     * `ADR-0010`): `com.huawei.hms:stats` и `com.huawei.hms:hatool` — телеметрия вендорского
     * клиента, `network-*` — его сетевая библиотека. Это не наша аналитика (наша — контур
     * MyTracker) и не наш сетевой слой: исключить их значило бы менять поведение клиента
     * магазина без возможности проверить покупку автоматическим прогоном.
     *
     * Координата провайдера аналитики — одна: `com.my.tracker:mytracker-sdk`. Его замыкание
     * (`play-services-*`, клиент Billing, Firebase-кодировщики) в артефакт не едет вовсе:
     * запрещённые группы сняты исключениями в `core-analytics/build.gradle.kts`, и проверка
     * состава это видит, а не предполагает.
     */
    val ALLOWED_COORDINATES = listOf(
        // Клиент магазина и его база.
        "com.huawei.hms:iap",
        "com.huawei.hms:base",
        "com.huawei.hms:ui",
        "com.huawei.hms:device",
        "com.huawei.hms:log",
        "com.huawei.hms:baselegacyapi",
        "com.huawei.hms:availableupdate",
        // Телеметрия клиента: приходит с базой, продуктом не зовётся.
        "com.huawei.hms:stats",
        "com.huawei.hms:hatool",
        // Сетевая библиотека вендора и его криптография: ими ходит клиент магазина.
        "com.huawei.hms:network-grs",
        "com.huawei.hms:network-common",
        "com.huawei.hms:network-framework-compat",
        "com.huawei.android.hms:security-ssl",
        "com.huawei.android.hms:security-encrypt",
        "com.huawei.android.hms:security-base",
        // Настройки проекта AppGallery Connect и задачи, которыми возвращаются ответы.
        "com.huawei.agconnect:agconnect-core",
        "com.huawei.hmf:tasks",
        // Провайдер аналитики: SDK за портом `AnalyticsTransport` (SKL-165, Р-98).
        "com.my.tracker:mytracker-sdk",
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

    /**
     * Разрешения, запрещённые всегда (НФТ-15, `ADR-0017` п. 3).
     *
     * `ACCESS_WIFI_STATE` продукту не нужен: состояние сети читает только SDK аналитики,
     * и делает это через `ConnectivityManager`, которому довольно `ACCESS_NETWORK_STATE`
     * (разрешено контуру, Р-100). `com.google.android.gms.permission.AD_ID` запрещён
     * безусловно: рекламные идентификаторы не собираются ни при каких условиях (ПМ-26).
     */
    val FORBIDDEN_PERMISSIONS = listOf(
        "android.permission.ACCESS_WIFI_STATE",
        "com.google.android.gms.permission.AD_ID",
    )

    /** Разрешена ли координата: точная или по началу (модули одной публикации). */
    fun allows(coordinate: String): Boolean = ALLOWED_COORDINATES.any { coordinate.startsWith(it) }

    /**
     * Охраняется ли координата: её группа входит в [GUARDED_GROUPS].
     *
     * Сравнивается **группа**, а не начало строки: разделитель координаты — `:`, и проверка
     * начала пропустила бы мимо `com.my.tracker:mytracker-sdk` (в группе нет точки после
     * `tracker`). Ошибка ровно того сорта, от которого защищает прогон правила на образцах.
     */
    fun guards(coordinate: String): Boolean {
        val group = coordinate.substringBefore(':')
        return GUARDED_GROUPS.any { group == it || group.startsWith("$it.") }
    }
}
