package ru.stohastika.sklyanka.gradle

/**
 * Правила сетевых контуров как чистые функции (SKL-165, `ADR-0017` п. 4, TDD §6.4).
 *
 * Логику держим здесь, а не в задачах, ровно по одной причине: правило, которое нельзя
 * прогнать на заведомом нарушении, — это обещание, а не гейт. [VerifyNetworkContourRuleTask]
 * подаёт сюда образцы плохих манифестов и составов, а `VerifyMergedManifestTask`
 * и `VerifyDependencyCompositionTask` — настоящий артефакт. Задачи при этом ничего
 * не решают: они читают файлы и передают текст.
 */
object NetworkContourRules {

    /**
     * Сеть только вместе со своим контуром и только из него (`ADR-0017` п. 2).
     *
     * Четыре правила на каждое разрешение контуров:
     *
     * 1. разрешение запрещено безусловно (состояние сети, рекламный идентификатор) —
     *    нарушение, откуда бы оно ни пришло;
     * 2. ни один модуль вне контуров не объявляет сетевое разрешение;
     * 3. если контур входит в артефакт, его разрешение обязано быть в слитом манифесте
     *    (иначе сетевой путь пропал, а не «повезло»), и объявить его обязан сам контур;
     * 4. объявление двумя контурами законно: в `pro` сеть нужна и аналитике, и покупкам.
     *
     * @param mergedManifest текст слитого манифеста собираемого артефакта
     * @param contourManifests манифесты контуров: имя модуля → текст
     * @param contoursInArtifact входит ли контур в артефакт: имя модуля → признак
     * @param moduleManifests исходные манифесты **всех** модулей сборки мимо контуров:
     *   путь → текст (по ним видно, кто ещё объявил сетевое разрешение)
     * @return список нарушений; пустой список означает, что правило соблюдено
     */
    fun validate(
        mergedManifest: String,
        contourManifests: Map<String, String>,
        contoursInArtifact: Map<String, Boolean>,
        moduleManifests: Map<String, String> = emptyMap(),
    ): List<String> {
        val problems = mutableListOf<String>()

        AnalyticsContourRules.FORBIDDEN_PERMISSIONS.forEach { permission ->
            if (mergedManifest.declares(permission)) {
                problems += "forbidden permission $permission is present in the merged manifest " +
                    "(PLATFORM-3, NFR-15). A dependency may have merged it in; remove it with " +
                    "tools:node=\"remove\" or return it with the contour decision (PM-26)."
            }
        }

        val contours = AnalyticsContourRules.NETWORK_CONTOURS
        val presentContours = contours.filter { contoursInArtifact[it.module] == true }
        val permissionNames = contours.flatMap { it.permissions }.distinct()

        permissionNames.forEach { permission ->
            val inMerged = mergedManifest.declares(permission)
            val declaring = contours.filter { contour ->
                contourManifests[contour.module].orEmpty().declares(permission)
            }
            val declaringPresent = declaring.filter { it in presentContours }

            moduleManifests.filterValues { it.declares(permission) }.keys.forEach { path ->
                problems += "$permission is declared by $path — the network point belongs to a " +
                    "network contour alone (ADR-0017 §2)"
            }

            val requiring = presentContours.filter { permission in it.permissions }

            when {
                // Контур в артефакте — разрешение обязано быть в слитом манифесте:
                // исчезновение сетевого пути нарушение, а не «повезло».
                !inMerged -> requiring.forEach { contour ->
                    problems += "the ${contour.module} contour is part of the artifact but " +
                        "$permission is missing from the merged manifest: the network point " +
                        "disappeared (ADR-0017 §2)"
                }

                // Разрешение в слитом манифесте, но ни один контур артефакта его не объявил:
                // сеть пришла неизвестно откуда — самая частая форма утечки.
                declaringPresent.isEmpty() -> problems +=
                    "$permission is in the merged manifest while no network contour in the " +
                        "artifact declares it (PLATFORM-3, ADR-0017 §2). Contours in the " +
                        "artifact: ${presentContours.joinToString { it.module }.ifEmpty { "none" }}"

                // Контур в артефакте и требует разрешение — объявить его обязан он сам,
                // а не соседний контур и не чужая библиотека.
                else -> requiring.filterNot { it in declaringPresent }.forEach { contour ->
                    problems += "the ${contour.module} contour is part of the artifact but its " +
                        "manifest does not declare $permission: the network point must name " +
                        "itself (ADR-0017 §2)"
                }
            }
        }

        return problems.sorted()
    }

    /**
     * Состав зависимостей по контурам (`ADR-0017` п. 3, TDD §6.3).
     *
     * Три правила: запрещённые группы отсутствуют и в classpath, и в каталоге версий;
     * ни одна координата охраняемой группы не проезжает мимо белого списка; обязательные
     * координаты контура присутствуют, **только** если контур входит в артефакт. Последнее
     * и есть снятие связки «сеть = платный контур»: `free` обязан нести провайдера
     * аналитики и не обязан нести клиент магазина.
     */
    fun validateDependencies(
        coordinates: List<String>,
        versionCatalog: String,
        contoursInArtifact: Map<String, Boolean>,
    ): List<String> {
        val problems = mutableListOf<String>()

        AnalyticsContourRules.FORBIDDEN_GROUPS.forEach { group ->
            coordinates.filter { it.contains(group) }.forEach { hit ->
                problems += "resolved dependency '$hit' belongs to the forbidden group '$group' " +
                    "(PLATFORM-4, NFR-39, NFR-16)"
            }
            if (versionCatalog.contains(group)) {
                problems += "version catalog mentions the forbidden group '$group'"
            }
        }

        coordinates
            .filter(AnalyticsContourRules::guards)
            .filterNot(AnalyticsContourRules::allows)
            .forEach { hit ->
                problems += "resolved dependency '$hit' is outside the contour white list " +
                    "(SKL-100, ADR-0017 §3). Add it to AnalyticsContourRules consciously, or remove it."
            }

        AnalyticsContourRules.NETWORK_CONTOURS.forEach { contour ->
            if (contoursInArtifact[contour.module] != true) return@forEach
            val missing = contour.requiredCoordinates.filterNot { required ->
                coordinates.any { it.startsWith(required) }
            }
            if (missing.isNotEmpty()) {
                problems += "the ${contour.module} contour is part of the artifact but its required " +
                    "coordinates are missing: ${missing.joinToString()} (PLATFORM-3, ADR-0017 §3)"
            }
        }

        return problems.sorted()
    }
}

/** Объявляет ли манифест разрешение. Сравнение по имени атрибута, а не по подстроке. */
internal fun String.declares(permission: String): Boolean = contains("\"$permission\"")
