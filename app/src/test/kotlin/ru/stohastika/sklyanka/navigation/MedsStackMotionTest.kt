package ru.stohastika.sklyanka.navigation

import androidx.compose.runtime.mutableStateListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.ui.motion.LayerMotion

class MedsStackMotionTest {

    @Test
    fun `a card or a form goes deeper`() {
        val stack = stack(MedsDestination.List)

        stack.push(MedsDestination.Card("med-1"))
        assertEquals(LayerMotion.Forward, stack.motion)

        stack.push(MedsDestination.Edit("med-1"))
        assertEquals(LayerMotion.Forward, stack.motion)
    }

    @Test
    fun `the archive segment is an adjacent layer`() {
        val stack = stack(MedsDestination.List)

        stack.push(MedsDestination.Archive)

        assertEquals(LayerMotion.Crossfade, stack.motion)
    }

    @Test
    fun `closing a card returns backwards and closing the archive crosses`() {
        val card = stack(MedsDestination.List)
        card.push(MedsDestination.Card("med-1"))
        assertTrue(card.pop())
        assertEquals(LayerMotion.Back, card.motion)

        val archive = stack(MedsDestination.List)
        archive.push(MedsDestination.Archive)
        assertTrue(archive.pop())
        assertEquals(LayerMotion.Crossfade, archive.motion)
    }

    @Test
    fun `the root of the section has nowhere to pop`() {
        val stack = stack(MedsDestination.List)

        assertFalse(stack.pop())
        assertEquals(MedsDestination.List, stack.current)
    }

    private fun stack(initial: MedsDestination): MedsStack {
        val entries = mutableStateListOf<MedsDestination>(MedsDestination.List)
        if (initial != MedsDestination.List) entries.add(initial)
        return MedsStack(entries)
    }
}
