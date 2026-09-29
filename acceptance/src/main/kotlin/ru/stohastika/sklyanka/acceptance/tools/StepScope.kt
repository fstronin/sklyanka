package ru.stohastika.sklyanka.acceptance.tools

import ru.stohastika.sklyanka.acceptance.RepositoryLayout

/**
 * Прогон как область видимости словаря шагов (SKL-26, TDD §6.2).
 *
 * После SKL-26 прогонов несколько: сценарный на JVM и по одному экранному на модуль
 * с экранами. Одно и то же выражение, объявленное в двух модулях, — дубль **только если
 * оба объявления могут встретиться в одном прогоне**. Иначе проверка начала бы падать
 * на совершенно законном совпадении: шаги SKL-24 в `core-ui` повторяют три выражения
 * Предыстории из шагов задачи SKL-24 в модуле сценарной приёмки, и повторяют правильно —
 * classpath'ы этих прогонов не пересекаются.
 *
 * Внутри прогона правило прежнее и строгое: один шаг — одно объявление.
 */
data class StepScope(val name: String, val declarations: List<StepDeclaration>)

/** Как из объявлений собираются прогоны. */
object StepScopes {

    /** Модуль сценарной приёмки: его объявления читаются отражением. */
    const val ACCEPTANCE_MODULE = "acceptance"

    /** Набор исходников модуля сценарной приёмки, который является общим артефактом. */
    const val ARTIFACT_ORIGIN = "$ACCEPTANCE_MODULE/main"

    /**
     * Прогоны репозитория.
     *
     * @param reflected объявления модуля сценарной приёмки (основной и тестовый наборы)
     * @param scanned объявления остальных модулей, прочитанные из исходников
     */
    fun of(reflected: List<StepDeclaration>, scanned: Map<String, List<StepDeclaration>>): List<StepScope> {
        val artifact = reflected.filter { it.origin == ARTIFACT_ORIGIN }
        val jvm = StepScope("сценарный прогон на JVM (:acceptance)", reflected)
        val screens = scanned.entries.sortedBy { it.key }.map { (module, declarations) ->
            // Экранный прогон видит общий артефакт только если модуль от него зависит.
            // core-ui зависеть от него не может (ModuleGraphRules), и его прогон
            // состоит из собственных объявлений — так оно и есть на деле.
            val usesArtifact = ":$ACCEPTANCE_MODULE" in RepositoryLayout.projectDependenciesOf(module)
            StepScope(
                name = "экранный прогон :$module" + if (usesArtifact) " (с общим артефактом)" else "",
                declarations = if (usesArtifact) artifact + declarations else declarations,
            )
        }
        return listOf(jvm) + screens
    }
}
