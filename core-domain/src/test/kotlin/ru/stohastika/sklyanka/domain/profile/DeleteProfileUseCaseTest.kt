package ru.stohastika.sklyanka.domain.profile

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OpLogRecord
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository

/**
 * Удаление чужого профиля: правило, порядок и след (ОВ-7, ПРФ-28, ПРФ-30, ПРФ-31).
 *
 * Порядок шагов проверяется отдельным тестом с записывающими заглушками: «стирание →
 * журнал → переключение → перепланирование» — это контракт, а не удобство реализации.
 * Свой и неизвестный профиль не оставляют ни следа, ни перепланирования: исход без
 * записи — единственное доказательство, что операция не началась.
 */
class DeleteProfileUseCaseTest {

    private val world = ProfileTestWorld()

    @Test
    fun aForeignProfileIsErasedLoggedAndReplacedByTheOwner() = runBlocking {
        val foreign = world.foreign()
        world.active.switchTo(foreign.id, SwitchReason.USER_SETTINGS)

        val outcome = world.delete(foreign.id)

        assertEquals(DeleteProfileOutcome.Deleted(foreign), outcome)
        assertEquals(listOf(foreign.id), world.eraser.erased)
        val record = world.log.records.single()
        assertEquals(OpEvent.PROFILE_DELETED, record.event)
        assertEquals(foreign.id, record.profileId)
        assertEquals(null, record.medicationId)
        assertEquals(null, record.intakeId)
        assertEquals(SwitchReason.PROFILE_DELETED, world.active.reasons.last())
        assertEquals(Fixtures.PROFILE_ID, world.active.value)
        assertEquals(1, world.replanner.calls)
    }

    @Test
    fun theOwnProfileIsNeverDeleted() = runBlocking {
        val outcome = world.delete(Fixtures.PROFILE_ID)

        assertEquals(DeleteProfileOutcome.OwnProfile, outcome)
        assertNothingHappened()
    }

    @Test
    fun anUnknownProfileIsNotDeleted() = runBlocking {
        val outcome = world.delete(UNKNOWN_PROFILE_ID)

        assertEquals(DeleteProfileOutcome.UnknownProfile, outcome)
        assertNothingHappened()
    }

    @Test
    fun theStepsFollowTheDeclaredOrder() = runBlocking {
        val order = mutableListOf<String>()
        val useCase = DeleteProfileUseCase(
            profiles = world.profiles,
            eraser = ProfileEraser { order += ERASE },
            active = OrderRecordingActive(order),
            replan = ProfileSetReplanner { order += REPLAN },
            log = OrderRecordingLog(order),
        )
        val foreign = world.foreign()

        useCase(foreign.id)

        assertEquals(listOf(ERASE, LOG, SWITCH, REPLAN), order)
    }

    private fun assertNothingHappened() {
        assertTrue(world.eraser.erased.isEmpty())
        assertTrue(world.log.records.isEmpty())
        assertTrue(world.active.reasons.isEmpty())
        assertEquals(0, world.replanner.calls)
    }

    private companion object {
        const val UNKNOWN_PROFILE_ID = "no-such-profile"
        const val ERASE = "erase"
        const val LOG = "log"
        const val SWITCH = "switch"
        const val REPLAN = "replan"
    }
}

/** Активный профиль, записывающий сам факт переключения в общий список порядка. */
private class OrderRecordingActive(private val order: MutableList<String>) : ActiveProfileRepository {

    override val activeProfileId: Flow<String> = flowOf(OWNER_ID)

    override suspend fun current(): String = OWNER_ID

    override suspend fun switchTo(profileId: String) {
        order += SWITCH
    }

    override suspend fun switchTo(profileId: String, reason: SwitchReason) {
        order += SWITCH
    }

    private companion object {
        const val OWNER_ID = "owner"
        const val SWITCH = "switch"
    }
}

/** Журнал, записывающий сам факт записи в общий список порядка. */
private class OrderRecordingLog(private val order: MutableList<String>) : OperationLog {

    @Suppress("LongParameterList")
    override suspend fun record(
        event: OpEvent,
        profileId: String?,
        medicationId: String?,
        intakeId: String?,
        before: String?,
        after: String?,
        source: String?,
        detail: String?,
    ) {
        order += "log"
    }

    override suspend fun recent(limit: Int): List<OpLogRecord> = emptyList()

    override suspend fun prune(keepDays: Long, keepRecords: Int) = Unit
}
