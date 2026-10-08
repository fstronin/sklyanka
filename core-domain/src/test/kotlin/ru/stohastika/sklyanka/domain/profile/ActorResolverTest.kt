package ru.stohastika.sklyanka.domain.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.IntakeSource

/**
 * Подпись «отметили вы» появляется ровно в одном случае из трёх (ПР-12, ПРФ-14).
 *
 * Проверяются все три ветви, потому что цена ошибки в каждой своя и все они про ложь
 * в истории: лишняя подпись приписывает владельцу действие, которого он не делал,
 * пропущенная — снимает с него авторство отметки, которую он поставил.
 */
@DisplayName("Who marked the intake: the signature appears only when the owner marked a foreign intake")
class ActorResolverTest {

    @Test
    fun `the owner marking a foreign intake from the phone is recorded as the actor`() {
        val actor = ActorResolver.actorFor(
            intakeProfileId = FOREIGN,
            source = IntakeSource.PHONE,
            selfProfileId = SELF,
        )

        assertEquals(SELF, actor)
    }

    @Test
    fun `an intake of the own profile carries no actor`() {
        assertNull(
            ActorResolver.actorFor(intakeProfileId = SELF, source = IntakeSource.PHONE, selfProfileId = SELF),
        )
    }

    @Test
    fun `an automatic miss has no actor at all`() {
        assertNull(
            ActorResolver.actorFor(intakeProfileId = FOREIGN, source = IntakeSource.AUTO, selfProfileId = SELF),
        )
    }

    @Test
    fun `without an owner profile there is nobody to sign with`() {
        assertNull(
            ActorResolver.actorFor(intakeProfileId = FOREIGN, source = IntakeSource.PHONE, selfProfileId = null),
        )
    }

    private companion object {
        const val SELF = "profile-self"
        const val FOREIGN = "profile-mother"
    }
}
