// feature-widgets — виджеты домашнего экрана (SKL-118, Р-34): «Сегодня» с быстрой отметкой,
// неделя и по лекарству, плюс экран настройки — точка гейта «Про» (Р-32).
//
// Модуль — поверхность, а не источник правил: что виджет показывает, собирает домен
// (`core-domain/.../widget/WidgetSnapshot.kt`), отметка идёт тем же сценарием, что в приложении
// (`MarkIntakeUseCase`, источник `PHONE`, ПР-22). Здесь — только разметка Glance и экран
// настройки. Зависимостей на другие feature-* нет и быть не может (verifyModuleDependencies):
// экран настройки получает данные тем же графом Hilt, а не вызовом соседнего экрана.
//
// Отметка и напоминания не продаются (Р-22): ни цен, ни слов о подписке в виджете нет —
// гейт живёт только на экране настройки.

plugins {
    id("sklyanka.android.library")
    id("sklyanka.android.compose")
    // Разметка виджета отрисовывается в JVM-тесте и снимается эталоном под Robolectric:
    // кадр типа не требует устройства (SKL-24, TDD §10.3).
    id("sklyanka.screen.tests")
    id("sklyanka.hilt")
}

android {
    namespace = "ru.stohastika.sklyanka.feature.widgets"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-ui"))

    // Разметка виджета и его обновление по событиям.
    implementation(libs.glance.appwidget)

    // Экран настройки виджета — конфигурационная активность с Compose-содержимым (§5.12).
    implementation(libs.androidx.activity.compose)

    // Привязка экземпляра плиты к лекарству переживает перезапуск процесса (Р-34, TDD §3).
    implementation(libs.androidx.datastore.preferences)

    // Отрисовка Glance-разметки в обычной задаче test: тем же Composвом, что и на экране,
    // но без устройства и без лончера.
    testImplementation(libs.glance.appwidget.testing)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    // Экранный прогон приёмки SKL-118 (SKL-26): Cucumber запускается изнутри модульного
    // теста под Robolectric, поэтому шаги видят собранный экран настройки. Общий мир
    // сценария и словарь шагов приезжают общим артефактом приёмки, а фабрика объектов
    // Cucumber раздаёт один мир всем классам шагов сценария.
    testDebugImplementation(project(":acceptance"))
    testDebugImplementation(libs.cucumber.picocontainer)
}
