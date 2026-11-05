package ru.stohastika.sklyanka.gradle

/**
 * Правила состава артефакта как чистые функции (SKL-165, `ADR-0017` п. 4, TDD §6.4).
 *
 * Логику держим здесь, а не в задачах, ровно по одной причине: правило, которое нельзя
 * прогнать на заведомом нарушении, — это обещание, а не гейт. [VerifyArtifactCompositionRuleTask]
 * подаёт сюда образцы плохих манифестов и составов, а `VerifyMergedManifestTask`
 * и `VerifyDependencyCompositionTask` — настоящий артефакт. Задачи при этом ничего
 * не решают: они читают файлы и передают текст.
 */
object ArtifactCompositionRules {

    /**
     * Сети в поставляемой сборке нет (ПЛ-3, `ADR-0017` п. 2).
     *
     * Три правила:
     *
     * 1. запрещённое разрешение (состояние сети, рекламный идентификатор) не проходит
     *    никогда, откуда бы оно ни пришло;
     * 2. сетевое разрешение не появляется в слитом манифесте вообще: разрешению неоткуда
     *    взяться законно, и появление — утечка, а не «повезло»;
     * 3. ни один исходный манифест модуля сети не объявляет — иначе видно, кто именно
     *    её принёс, а не только что она есть.
     *
     * @param mergedManifest текст слитого манифеста собираемого артефакта
     * @param moduleManifests исходные манифесты **всех** модулей сборки: путь → текст
     * @return список нарушений; пустой список означает, что правило соблюдено
     */
    fun validateManifest(
        mergedManifest: String,
        moduleManifests: Map<String, String> = emptyMap(),
    ): List<String> {
        val problems = mutableListOf<String>()

        ArtifactCompositionCatalog.FORBIDDEN_PERMISSIONS.forEach { permission ->
            if (mergedManifest.declares(permission)) {
                problems += "forbidden permission $permission is present in the merged manifest " +
                    "(PLATFORM-3, NFR-15). A dependency may have merged it in; remove it with " +
                    "tools:node=\"remove\"."
            }
        }

        ArtifactCompositionCatalog.NETWORK_PERMISSIONS.forEach { permission ->
            if (mergedManifest.declares(permission)) {
                problems += "$permission is in the merged manifest: the shipped build declares " +
                    "no network (PLATFORM-3, ADR-0017 §2)"
            }

            moduleManifests.filterValues { it.declares(permission) }.keys.forEach { path ->
                problems += "$permission is declared by $path: no module may declare a network " +
                    "permission in an offline product (ADR-0017 §2)"
            }
        }

        return problems.sorted()
    }

    /**
     * Состав зависимостей (ПЛ-4, НФТ-15/16/39, `ADR-0017` п. 3, TDD §6.3).
     *
     * Запрещённые группы отсутствуют и в classpath, и в каталоге версий: каталог проверяется
     * отдельно, потому что координата, объявленная в нём, однажды станет зависимостью,
     * и заметить это на составе артефакта было бы поздно.
     */
    fun validateDependencies(
        coordinates: List<String>,
        versionCatalog: String,
    ): List<String> {
        val problems = mutableListOf<String>()

        ArtifactCompositionCatalog.FORBIDDEN_GROUPS.forEach { group ->
            coordinates.filter { it.contains(group) }.forEach { hit ->
                problems += "resolved dependency '$hit' belongs to the forbidden group '$group' " +
                    "(PLATFORM-4, NFR-39, NFR-16)"
            }
            if (versionCatalog.contains(group)) {
                problems += "version catalog mentions the forbidden group '$group'"
            }
        }

        return problems.sorted()
    }
}

/**
 * Объявляет ли манифест разрешение.
 *
 * Сравнение по имени атрибута, а не по подстроке. Узел с `tools:node="remove"` объявлением
 * не считается: манифест `app` именно так снимает разрешение, которое приносит библиотека,
 * и считать это объявлением значило бы валить сборку за собственное исправление (НФТ-15).
 */
internal fun String.declares(permission: String): Boolean =
    USES_PERMISSION.findAll(this).any { tag ->
        tag.value.contains("\"$permission\"") && !tag.value.contains(REMOVE_NODE)
    }

private val USES_PERMISSION = Regex("""<uses-permission\b[^>]*>""")
private const val REMOVE_NODE = "tools:node=\"remove\""
