package ru.stohastika.sklyanka.acceptance.steps.sk64

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import ru.stohastika.sklyanka.feature.settings.transfer.TransferStep
import ru.stohastika.sklyanka.feature.settings.transfer.transferStepMotion
import ru.stohastika.sklyanka.ui.motion.LayerMotion

class Sk64TransferScreenSteps {

    @Допустим("^шаги переноса данных собраны на стенде SKL-64$")
    fun theTransferStepsAreOnTheStand() = Unit

    @Тогда("^шаг «(.+)» после шага «(.+)» — движение «(вперёд|назад|рядом)»$")
    fun theStepMotionIs(target: String, initial: String, motion: String) {
        val expected = when (motion) {
            "вперёд" -> LayerMotion.Forward
            "назад" -> LayerMotion.Back
            else -> LayerMotion.Crossfade
        }
        assertEquals(expected, transferStepMotion(steps.getValue(initial), steps.getValue(target)))
    }

    private companion object {
        val steps: Map<String, TransferStep> = mapOf(
            "обзор" to TransferStep.OVERVIEW,
            "сводка" to TransferStep.SUMMARY,
            "совпадения" to TransferStep.MATCHES,
            "подтверждение" to TransferStep.REPLACE_CONFIRM,
            "отчёт" to TransferStep.REPORT,
        )
    }
}
