package ru.stohastika.sklyanka.gradle

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Варианты сборки и подпись (TDD §4.3).
 *
 * Измерение `contour` (Р-86, ADR-0016) оставляет одно значение и удваивает имена:
 * `freeDebug`, `freeRelease`, `freeWatchTest`.
 *
 * freeDebug     — отладочный ключ; на часы ничего не отправит (ЧАС-9);
 * freeRelease   — релизный ключ; единственная поставляемая сборка;
 * freeWatchTest — релизная подпись при возможности отладки: именно этот вариант идёт
 *                 на испытания карточки Wear Engine (SKL-03, SKL-16).
 *
 * Без секретов релизный артефакт остаётся неподписанным: так его собирает F-Droid
 * и так его может собрать контрибьютор без хранилища ключей. Отладочный ключ
 * вместо релизного не подставляется никогда: такая сборка выглядела бы релизной,
 * но не встала бы поверх опубликованной.
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
            // An unsigned release artifact is a legitimate outcome: F-Droid builds from
            // source and signs with its own key, and a contributor without the release
            // keystore still has to be able to build the shipped variant. The artifact is
            // left unsigned; the debug key is never substituted, because that would look
            // like a release build and would not install over a published one.
            logger.warn(
                "Release signing secrets are absent: release artifacts are left unsigned " +
                    "(provide ${SigningSecrets.PROPERTIES_FILE} or the SKLYANKA_KEYSTORE_* variables to sign).",
            )
        }
    }

    private companion object {
        const val DEBUG = "debug"
        const val RELEASE = "release"
        const val WATCH_TEST = "watchTest"
    }
}
