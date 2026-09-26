package ru.heuri.sklyanka.gradle

/**
 * Правило направления зависимостей (TDD §3.3, §5.4):
 * app → feature-* → core-ui / core-data → core-domain.
 *
 * Логика вынесена в чистую функцию, чтобы её можно было проверить на заведомо
 * нарушающем графе, не ломая настоящий проект (TDD §5.2).
 *
 * Ручная дисциплина здесь не работает: нарушение появляется в момент «нужна всего
 * одна функция из соседнего экрана», и после трёх таких случаев модульность
 * существует только в документации.
 */
object ModuleGraphRules {

    /**
     * @param graph путь модуля → пути модулей проекта, от которых он зависит
     * @return список нарушений; пустой список означает, что направление соблюдено
     */
    fun validate(graph: Map<String, List<String>>): List<String> {
        val violations = mutableListOf<String>()
        graph.forEach { (module, dependencies) ->
            dependencies.forEach { dependency ->
                val reason = reject(module.trimStart(':'), dependency.trimStart(':'))
                if (reason != null) {
                    violations += "$module must not depend on $dependency: $reason"
                }
            }
        }
        return violations.sorted()
    }

    private fun reject(module: String, dependency: String): String? = when {
        module == dependency -> "a module cannot depend on itself"

        module == CORE_DOMAIN ->
            "core-domain is the innermost module and depends on nothing — " +
                "that is what keeps the domain free of Android and of Room (NFR-46)"

        module.startsWith(FEATURE_PREFIX) && dependency.startsWith(FEATURE_PREFIX) ->
            "screen modules must not depend on each other; shared code belongs in core-* " +
                "(otherwise the module boundary exists only on paper)"

        module in CORE_MODULES && dependency !in setOf(CORE_DOMAIN) ->
            "core-ui and core-data may depend on core-domain only"

        dependency == APP -> "nothing may depend on the application module"

        else -> null
    }

    private const val APP = "app"
    private const val CORE_DOMAIN = "core-domain"
    private const val FEATURE_PREFIX = "feature-"
    private val CORE_MODULES = setOf("core-ui", "core-data")
}
