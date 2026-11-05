package ru.stohastika.sklyanka.domain.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Таблица предела пяти профилей (ПРФ-32, ПРФ-34).
 *
 * Проверка на JVM без Android и без базы: правило предметное, и экран обязан показывать
 * ровно тот ответ, который даёт домен. Предел — продуктовое ограничение, а не платная
 * граница (Р-86): он закрыт при любом доступе, и продавать здесь нечего (П-7).
 */
@DisplayName("Creating a profile is closed by the count of profiles only")
class ProfileCreationGateTest {

    @Test
    fun `profiles up to the fifth are allowed`() {
        assertEquals(ProfileCreationDecision.Allowed, ProfileCreationGate.decide(created = 0))
        assertEquals(ProfileCreationDecision.Allowed, ProfileCreationGate.decide(created = 1))
        assertEquals(ProfileCreationDecision.Allowed, ProfileCreationGate.decide(created = 2))
        assertEquals(ProfileCreationDecision.Allowed, ProfileCreationGate.decide(created = 3))
        assertEquals(ProfileCreationDecision.Allowed, ProfileCreationGate.decide(created = 4))
    }

    /** ПРФ-34: при пяти профилях добавление закрыто, и предел не продаётся. */
    @Test
    fun `five profiles are the limit`() {
        assertEquals(ProfileCreationDecision.BlockedByLimit, ProfileCreationGate.decide(created = 5))
        assertEquals(ProfileCreationDecision.BlockedByLimit, ProfileCreationGate.decide(created = 6))
    }

    /** ПРФ-33: порог считается по числу заведённых, а не по факту удаления. */
    @Test
    fun `deleting down to four unlocks adding again`() {
        assertEquals(ProfileCreationDecision.Allowed, ProfileCreationGate.decide(created = 4))
    }
}
