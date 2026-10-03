package ru.stohastika.sklyanka.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Варианты сборки и подпись (TDD §4.3).
 *
 * debug       — отладочный ключ; на часы ничего не отправит (ЧАС-9);
 * release     — релизный ключ, выпуск;
 * watchTest   — релизная подпись при возможности отладки: именно этот вариант
 *               идёт на испытания карточки Wear Engine (SKL-03, SKL-16).
 *
 * Без секретов релизные варианты не собираются вовсе, а попытка их собрать
 * заканчивается внятным отказом, а не отладочной подписью.
 */
class ReleaseSigningConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        val secrets = SigningSecrets.resolve(this)

        extensions.configure<ApplicationExtension> {
            if (secrets != null) {
                signingConfigs.create(RELEASE) {
                    storeFile = secrets.storeFile
                    storePassword = secrets.storePassword
                    keyAlias = secrets.keyAlias
                    keyPassword = secrets.keyPassword
                    enableV1Signing = true
                    enableV2Signing = true
                }
            }

            buildTypes {
                getByName(DEBUG) {
                    isMinifyEnabled = false
                }
                getByName(RELEASE) {
                    // Обфускация, удаление мёртвого кода и сжатие ресурсов (SKL-22,
                    // задачи 9 и 33). Правила, к которым обращаются по имени строкой,
                    // собраны в app/proguard-rules.pro; остальное покрывают
                    // consumer-правила библиотек.
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                    signingConfig = signingConfigs.findByName(RELEASE)
                }
                create(WATCH_TEST) {
                    initWith(getByName(RELEASE))
                    // Отпечаток подписи тот же, что у release, иначе Wear Engine
                    // не примет отправку; отладка при этом остаётся доступной.
                    isDebuggable = true
                    isMinifyEnabled = false
                    // Сжатие ресурсов требует включённой минификации, а здесь её нет:
                    // вариант остаётся неминифицированным, как и был (SKL-22).
                    isShrinkResources = false
                    signingConfig = signingConfigs.findByName(RELEASE)
                    matchingFallbacks += RELEASE
                }
            }
        }

        if (secrets == null) {
            disableReleaseVariants()
            registerRefusingStubs()
        }
    }

    /**
     * Варианты с релизной подписью выключаются, чтобы `./gradlew build` на чистой
     * машине без ключа оставался зелёным: требование НФТ-49 — одна команда без
     * ручной правки файлов, а ключ появляется только в SKL-01.
     */
    private fun Project.disableReleaseVariants() {
        extensions.configure<ApplicationAndroidComponentsExtension> {
            beforeVariants(selector().withBuildType(RELEASE)) { it.enable = false }
            beforeVariants(selector().withBuildType(WATCH_TEST)) { it.enable = false }
        }
    }

    /** Явный отказ вместо «задача не найдена»: сообщение должно называть причину. */
    private fun Project.registerRefusingStubs() {
        REFUSED_TASKS.forEach { name ->
            tasks.register(name) {
                group = "build"
                description = "Refuses to build a release artifact without signing secrets."
                doLast {
                    throw GradleException(SigningSecrets.describeMissing(project))
                }
            }
        }
    }

    private companion object {
        const val DEBUG = "debug"
        const val RELEASE = "release"
        const val WATCH_TEST = "watchTest"
        val REFUSED_TASKS = listOf(
            "assembleRelease",
            "bundleRelease",
            "assembleWatchTest",
            "bundleWatchTest",
        )
    }
}
