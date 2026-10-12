package ru.stohastika.sklyanka.navigation

import androidx.compose.runtime.mutableStateListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Форма нового лекарства — уровень стека раздела, а не флаг маршрута списка (SKL-150).
 *
 * Пока открытость формы жила флагом `MedsRoute`, стек раздела оставался на списке, и системная
 * «Назад» с формы, открытой круглой кнопкой, вела на «Сегодня» — то есть выводила человека
 * из раздела, тогда как та же форма, открытая с «Сегодня», возвращала в список: два входа вели
 * себя противоположно (`docs/norm/04-behavior.md` §5.1: системная «Назад» повторяет возврат).
 *
 * Здесь проверяется договор стека, на который опирается `MedsSection`: место над списком
 * снимается возвратом, а корень раздела возврату не поддаётся — по нему вызывающий и решает
 * выйти из раздела. Это поведение вкладки, и оно не изменилось.
 */
class MedsFormFromListTest {

    @Test
    fun `a form opened from the list keeps the person in the section`() {
        // Так форму поднимает MedsSection по намерению списка (MedsIntent.AddMedication).
        val stack = stack()
        stack.push(MedsDestination.NewMedication)

        assertTrue("the form must be a level above the list", stack.pop())
        assertEquals(MedsDestination.List, stack.current)
    }

    @Test
    fun `a form opened from today keeps the person in the section`() {
        // Вход с «Сегодня»: раздел открывается сразу на форме, список лежит под ней.
        val stack = stack(initial = MedsDestination.NewMedication)

        assertTrue(stack.pop())
        assertEquals(MedsDestination.List, stack.current)
    }

    @Test
    fun `the root of the section is where the person leaves it`() {
        // На корне раздела возвращать некуда: уход из раздела остаётся решением вызывающего,
        // как и был (SKL-150 — форма его не трогает).
        val stack = stack()

        assertFalse(stack.pop())
        assertEquals(MedsDestination.List, stack.current)
    }

    private fun stack(initial: MedsDestination = MedsDestination.List): MedsStack {
        // Тип назван явно: `MedsDestination.List` — это вложенный объект с именем List,
        // и без аргумента выводится список именно этого типа, а не стек разделов.
        val entries = mutableStateListOf<MedsDestination>(MedsDestination.List)
        if (initial != MedsDestination.List) entries.add(initial)
        return MedsStack(entries)
    }
}
