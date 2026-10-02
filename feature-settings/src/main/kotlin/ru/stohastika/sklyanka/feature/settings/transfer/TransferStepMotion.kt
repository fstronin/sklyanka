package ru.stohastika.sklyanka.feature.settings.transfer

import ru.stohastika.sklyanka.ui.motion.LayerMotion

fun transferStepMotion(initial: TransferStep, target: TransferStep): LayerMotion = when {
    target.rank > initial.rank -> LayerMotion.Forward
    target.rank < initial.rank -> LayerMotion.Back
    else -> LayerMotion.Crossfade
}

private val TransferStep.rank: Int
    get() = when (this) {
        TransferStep.OVERVIEW -> 0
        TransferStep.SUMMARY -> 1
        TransferStep.MATCHES -> 2
        TransferStep.REPLACE_CONFIRM -> 3
        TransferStep.REPORT -> 4
    }
