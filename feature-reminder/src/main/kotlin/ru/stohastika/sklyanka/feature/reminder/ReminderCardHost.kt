package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun ReminderCardHost(state: ReminderUiState, actions: ReminderActions, onClosed: () -> Unit) {
    val card = state as? ReminderUiState.Card
    var lastCard by remember { mutableStateOf<ReminderUiState.Card?>(null) }
    LaunchedEffect(card) { if (card != null) lastCard = card }
    ReminderFade(
        visible = card != null,
        closing = state is ReminderUiState.Closed,
        onClosed = onClosed,
    ) {
        (card ?: lastCard)?.let { shown ->
            ReminderScreen(state = shown, actions = actions)
        }
    }
}
