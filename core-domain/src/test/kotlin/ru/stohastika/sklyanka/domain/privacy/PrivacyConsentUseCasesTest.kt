package ru.stohastika.sklyanka.domain.privacy

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Правило первого запуска (Р-89): диалог показывается, пока нет отметки, и снимается
 * только отметкой.
 */
class PrivacyConsentUseCasesTest {

    @Test
    fun `an install without the mark sees the policy`() = runTest {
        val store = FakeStore()

        assertTrue(ShouldShowPrivacyConsentUseCase(store)())
    }

    @Test
    fun `agreeing hides the policy from the next launch`() = runTest {
        val store = FakeStore()

        AcknowledgePrivacyConsentUseCase(store)()
        assertFalse(ShouldShowPrivacyConsentUseCase(store)())
        assertEquals(1, store.writes, "the acknowledgement is one write")
    }

    private class FakeStore(private var acknowledged: Boolean = false) : PrivacyConsentStore {

        var writes: Int = 0
            private set

        override suspend fun acknowledged(): Boolean = acknowledged

        override suspend fun acknowledge() {
            acknowledged = true
            writes++
        }
    }
}
