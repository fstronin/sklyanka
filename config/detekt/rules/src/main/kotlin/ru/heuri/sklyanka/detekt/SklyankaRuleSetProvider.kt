package ru.heuri.sklyanka.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Набор правил проекта «Склянка».
 *
 * Здесь живут запреты, которые нельзя выразить стандартными правилами detekt:
 * дизайн-токены, русский текст в коде, прямое обращение к часам, разрушающая
 * миграция Room и Android в доменном модуле (TDD §5.3).
 */
class SklyankaRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = RULE_SET_ID

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            HardcodedDesignToken(config),
            RussianTextInCode(config),
            DirectSystemClockAccess(config),
            DestructiveRoomMigration(config),
            AndroidImportInDomain(config),
        ),
    )

    companion object {
        const val RULE_SET_ID = "sklyanka"
    }
}
