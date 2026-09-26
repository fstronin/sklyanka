package ru.heuri.sklyanka.acceptance

import io.cucumber.java.Before

/**
 * Общие миры сценария: одно состояние на все классы шагов.
 *
 * Cucumber создаёт по экземпляру каждого класса шагов на сценарий, и до SKL-12 этого
 * хватало: у каждой задачи был свой мир и свои строки. Предыстория SKL-12 устроена иначе —
 * лекарство и остаток ей задают шаги SKL-07, порог и предупреждение добавляют шаги
 * SKL-12, и смотрят они обязаны в одно и то же состояние. Объявить те же шаги второй раз
 * нельзя: Cucumber отвергает неоднозначные определения, и прогон ломается целиком.
 *
 * Поэтому мир заводится здесь и раздаётся по требованию, а не создаётся каждым классом
 * шагов для себя. Хранится он в [ThreadLocal] и сбрасывается хуком перед каждым
 * сценарием: сценарии обязаны оставаться независимыми, а параллельный прогон — возможным.
 */
internal object AcceptanceWorlds {

    private val planning = ThreadLocal<PlanningWorld>()
    private val lifecycle = ThreadLocal<IntakeLifecycleWorld>()
    private val stock = ThreadLocal<StockWorld>()

    /** Мир генерации приёмов (SKL-06). */
    fun planning(): PlanningWorld = planning.get() ?: PlanningWorld().also { planning.set(it) }

    /** Мир жизненного цикла приёма (SKL-07): лекарства, остаток, отметки. */
    fun lifecycle(): IntakeLifecycleWorld = lifecycle.get() ?: IntakeLifecycleWorld().also { lifecycle.set(it) }

    /** Мир остатка упаковки (SKL-12) — надстройка над [lifecycle], а не второе состояние. */
    fun stock(): StockWorld = stock.get() ?: StockWorld(lifecycle(), planning()).also { stock.set(it) }

    fun reset() {
        planning.remove()
        lifecycle.remove()
        stock.remove()
    }
}

/**
 * Граница сценария.
 *
 * Без сброса состояние текло бы из сценария в сценарий, и порядок прогона стал бы
 * значимым — худший вид ложно-зелёной приёмки.
 */
class ScenarioWorlds {

    @Before
    fun startScenario() {
        AcceptanceWorlds.reset()
    }
}
