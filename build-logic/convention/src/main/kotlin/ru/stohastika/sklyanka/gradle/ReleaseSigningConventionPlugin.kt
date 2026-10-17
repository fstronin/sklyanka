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
 * Измерение `contour` (Р-86, ADR-0016) удваивает имена: `freeDebug`/`proDebug`,
 * `freeRelease`/`proRelease`, `freeWatchTest`/`proWatchTest`.
 *
 * freeDebug / proDebug     — отладочный ключ; на часы ничего не отправит (ЧАС-9);
 * freeRelease              — релизный ключ; единственная поставляемая сборка;
 * proRelease / proWatchTest — выключены: подписанного артефакта у `pro` нет вовсе,
 *                            вариант живёт отладочной сборкой и unit-тестами;
 * freeWatchTest            — релизная подпись при возможности отладки: именно этот
 *                            вариант идёт на испытания карточки Wear Engine (SKL-03, SKL-16).
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

        // `pro` не поставляется: подписанного релизного артефакта у него нет вовсе
        // (Р-86, ADR-0016 п. 2). Компиляция и unit-тесты идут отладочным вариантом;
        // freeRelease остаётся единственной подписываемой сборкой.
        disableProReleaseVariants()

        if (secrets == null) {
            disableReleaseVariants()
            registerRefusingStubs()
        }
    }

    /**
     * Выключить релизные варианты контура `pro`.
     *
     * Селектор назван измерением и значением, а не порядком: конвенция применяется только
     * к `app`, где эти имена объявлены (Р-86, ADR-0016). `proWatchTest` выключен по той же
     * причине — он тоже несёт релизную подпись.
     */
    private fun Project.disableProReleaseVariants() {
        extensions.configure<ApplicationAndroidComponentsExtension> {
            beforeVariants(selector().withFlavor(CONTOUR_DIMENSION, PRO_FLAVOR).withBuildType(RELEASE)) {
                it.enable = false
            }
            beforeVariants(selector().withFlavor(CONTOUR_DIMENSION, PRO_FLAVOR).withBuildType(WATCH_TEST)) {
                it.enable = false
            }
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

        /** Измерение контура и непоставляемый вариант (Р-86, ADR-0016 п. 2). */
        const val CONTOUR_DIMENSION = "contour"
        const val PRO_FLAVOR = "pro"

        /**
         * Варианты контура (Р-86, ADR-0016): `free` — поставляемый, `pro` — запасной.
         *
         * Список имён повторяет фактические задачи AGP с измерением `contour`
         * (`assembleFreeRelease`, `assembleProRelease`, …): у неflavored-сборки были
         * `assembleRelease`/`bundleRelease`, и после введения вариантов они перестали
         * существовать. Без этого списка release без секретов падал бы «задачей не найдено»
         * или, хуже, собирался бы без явного отказа.
         */
        val REFUSED_TASKS = listOf(
            "assembleFreeRelease",
            "bundleFreeRelease",
            "assembleProRelease",
            "bundleProRelease",
            "assembleFreeWatchTest",
            "bundleFreeWatchTest",
            "assembleProWatchTest",
            "bundleProWatchTest",
        )
    }
}
