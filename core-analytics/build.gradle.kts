// core-analytics — сетевой контур аналитики (SKL-165, Р-93 п. 2, ADR-0017).
//
// Модуль знает провайдера и не знает продукта: он исполняет порт `AnalyticsTransport`
// из `core-domain` поверх MyTracker. Ни экраны, ни сценарии домена, ни `core-data`
// не импортируют `ru.stohastika.sklyanka.analytics.*` — на руках у них только типы домена.
// Смена провайдера = новый модуль за тем же портом; этот replace целиком.
//
// Модуль объявляет `INTERNET` своим манифестом и не объявляет разрешений состояния сети:
// рекомендация поставщика добавить `ACCESS_NETWORK_STATE` не выполняется (НФТ-15), а `AD_ID`
// и рекламные модули не подключаются вовсе (ПМ-26).

import org.gradle.api.Action
import org.gradle.api.execution.TaskExecutionGraph
import java.util.Properties

plugins {
    id("sklyanka.android.library")
    id("sklyanka.hilt")
}

/**
 * Ключ SDK провайдера (TDD §2.3).
 *
 * Источник — Gradle-свойство `sklyanka.mytracker.key` или ключ `mytracker.key`
 * в `local.properties`, по образцу конфигурации вендора в `app`. Ключ вне git:
 * пока его нет, отправки нет, а отладочная сборка собирается и ничего не отправляет.
 */
val myTrackerKey: String = resolveMyTrackerKey()

fun resolveMyTrackerKey(): String {
    val fromProperty = providers.gradleProperty("sklyanka.mytracker.key").orNull
    val fromLocalProperties = rootProject.file("local.properties")
        .takeIf { it.isFile }
        ?.let { file ->
            Properties().apply { file.inputStream().use { load(it) } }.getProperty("mytracker.key")
        }
    return (fromProperty ?: fromLocalProperties).orEmpty().trim()
}

if (myTrackerKey.isEmpty()) {
    logger.lifecycle(
        "MyTracker key is absent: analytics is configured but sends nothing " +
            "(provide sklyanka.mytracker.key or mytracker.key in local.properties).",
    )
}

android {
    namespace = "ru.stohastika.sklyanka.analytics"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        // Ключ уезжает в BuildConfig, а не в ресурсы: он подставляется один раз при сборке,
        // и адаптер решает «можно/нельзя» по нему (available = key.isNotBlank()).
        buildConfigField("String", "MYTRACKER_KEY", "\"${myTrackerKey.escapeForBuildConfig()}\"")
    }
}

fun String.escapeForBuildConfig(): String = replace("\\", "\\\\").replace("\"", "\\\"")

/**
 * Релизная сборка без ключа отказывает (TDD §2.3, риск 6).
 *
 * Отладочная сборка без ключа — норма: она собирается и ничего не отправляет.
 * Поставляемый артефакт без ключа отправлял бы «в никуда» молча, и заметить это можно было
 * бы только по пустой консоли поставщика; поэтому отказ громкий и на этапе сборки.
 *
 * Проверяется именно артефакт приложения (`:app`), а не собственная релизная сборка
 * библиотеки: `verifyAll` собирает `build` каждого модуля, и библиотека без ключа
 * остаётся собираемой — она не поставляется отдельно.
 */
if (myTrackerKey.isEmpty()) {
    gradle.taskGraph.whenReady(
        Action<TaskExecutionGraph> {
            val shippedReleaseTasks = allTasks.filter { task ->
                task.project.path == ":app" && task.name.matches(releaseAssembly)
            }
            check(shippedReleaseTasks.isEmpty()) {
                buildString {
                    appendLine("The release build is refused: the MyTracker SDK key is empty, so the")
                    appendLine("shipped artifact would send analytics into nowhere (TDD §2.3, risk 6).")
                    appendLine()
                    appendLine("Provide the key as the Gradle property sklyanka.mytracker.key or as")
                    appendLine("mytracker.key in local.properties (the key never goes to git).")
                    appendLine("Requested: ${shippedReleaseTasks.joinToString { it.path }}")
                }
            }
        },
    )
}

val releaseAssembly = Regex("assemble[A-Za-z]*Release")

dependencies {
    implementation(project(":core-domain"))

    // Провайдер аналитики (SKL-165). Замыкание SDK объявляет GMS, клиент Google Play Billing
    // (через него — ещё GMS и Firebase) и Play Install Referrer. Запрещённые группы (ПЛ-4)
    // снимаются здесь, а не ослаблением правил: гейт состава обязан оставаться строгим.
    //
    // Почему это безопасно для SDK, а не «на удачу»: `GooglePlayProductHelper` проверяет
    // наличие `com.android.billingclient.api.*` через `Class.forName` и при отсутствии гасит
    // себя флагом (`purchase helper is disabled`); обращения к `AdvertisingIdClient`
    // и `AppSet` завернуты в перехват `Throwable`. Итог: без этих модулей SDK собирается,
    // инициализируется и отправляет события, а не работает только неиспользуемая нами
    // авто-отслеживание покупок.
    implementation(libs.mytracker.sdk) {
        exclude(group = "com.google.android.gms")
        exclude(group = "com.google.firebase")
        exclude(group = "com.android.billingclient")
    }

    testImplementation(libs.junit4)
}
