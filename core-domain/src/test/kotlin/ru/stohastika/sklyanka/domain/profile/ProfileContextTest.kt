package ru.stohastika.sklyanka.domain.profile

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.Fixtures

/**
 * Контекст профиля: признак индикации вычисляется, а не хранится (ПРФ-16).
 *
 * Хранимое поле можно выставить в `false` у чужого профиля — и вся защита отключается
 * одним значением в базе. Вычисляемое — нельзя.
 */
@DisplayName("The profile context decides about the foreign indication by is_self alone")
class ProfileContextTest {

    @Test
    fun `the own profile asks for no indication`() {
        val context = ProfileContext.of(Fixtures.profile())

        assertFalse(context.showsForeignIndication)
        assertTrue(context.isSelf)
    }

    @Test
    fun `a foreign profile asks for the indication`() {
        val context = ProfileContext.of(Fixtures.profile(name = "mother", isSelf = false))

        assertTrue(context.showsForeignIndication)
    }

    @Test
    fun `the context carries the name and the colour the screen needs`() {
        val profile = Fixtures.profile(name = "mother", isSelf = false)

        val context = ProfileContext.of(profile)

        assertTrue(context.name == profile.name && context.color == profile.color)
    }
}
