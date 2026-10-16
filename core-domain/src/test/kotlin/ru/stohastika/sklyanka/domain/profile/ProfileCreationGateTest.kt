package ru.stohastika.sklyanka.domain.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Таблица границы «сверх двух» и предела пяти (ПРФ-32, ПРФ-33, ПРФ-34, Р-84 пп. 1–3).
 *
 * Проверка на JVM без Android и без базы: правило предметное, и экран обязан показывать
 * ровно тот ответ, который даёт домен. Порядок исходов здесь — часть правила: предел пяти
 * проверяется первым, потому что доступ не должен открывать дверь в тупик (П-7).
 */
@DisplayName("Creating a profile is closed by the count of profiles, not by the subscription alone")
class ProfileCreationGateTest {

    @Test
    fun `the first two profiles are free`() {
        assertEquals(ProfileCreationDecision.Allowed, ProfileCreationGate.decide(created = 0, includesPro = false))
        assertEquals(ProfileCreationDecision.Allowed, ProfileCreationGate.decide(created = 1, includesPro = false))
    }

    @Test
    fun `a third profile is closed without a subscription`() {
        assertEquals(ProfileCreationDecision.BlockedByPro, ProfileCreationGate.decide(created = 2, includesPro = false))
    }

    @Test
    fun `a third profile passes with a subscription`() {
        assertEquals(ProfileCreationDecision.Allowed, ProfileCreationGate.decide(created = 2, includesPro = true))
    }

    @Test
    fun `four profiles with a subscription still allow a fifth`() {
        assertEquals(ProfileCreationDecision.Allowed, ProfileCreationGate.decide(created = 4, includesPro = true))
    }

    /** ПРФ-34: при пяти профилях добавление закрыто при любой подписке, и предел не продаётся. */
    @Test
    fun `five profiles are the limit under any subscription`() {
        assertEquals(
            ProfileCreationDecision.BlockedByLimit,
            ProfileCreationGate.decide(created = 5, includesPro = true),
        )
        assertEquals(
            ProfileCreationDecision.BlockedByLimit,
            ProfileCreationGate.decide(created = 5, includesPro = false),
        )
    }

    /**
     * ПРФ-33: порог считается по числу заведённых, а не по факту удаления.
     *
     * Четыре профиля, удалённый лишний — заведённых стало три, и без доступа добавление
     * по-прежнему закрыто: удаление не возвращает право добавить, иначе граница обходилась
     * бы парой «удалил — завёл».
     */
    @Test
    fun `deleting down to two does not unlock adding`() {
        assertEquals(ProfileCreationDecision.BlockedByPro, ProfileCreationGate.decide(created = 3, includesPro = false))
        assertEquals(ProfileCreationDecision.BlockedByPro, ProfileCreationGate.decide(created = 2, includesPro = false))
        assertEquals(ProfileCreationDecision.Allowed, ProfileCreationGate.decide(created = 1, includesPro = false))
    }
}
