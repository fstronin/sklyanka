// core-ui — дизайн-система: токены, типографика, базовые компоненты, общие строки.
//
// Единственный модуль, в пакете токенов которого литералы цвета и размера законны
// (НФТ-47). Сами токены появляются в SKL-05; здесь заводится модуль и его граница.

plugins {
    id("sklyanka.android.library")
    id("sklyanka.android.compose")
}

android {
    namespace = "ru.heuri.sklyanka.ui"
}

dependencies {
    implementation(project(":core-domain"))
}
