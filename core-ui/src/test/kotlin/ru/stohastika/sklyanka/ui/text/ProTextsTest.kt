package ru.stohastika.sklyanka.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.domain.pro.ProNotice

/**
 * Полнота слов витрины (SKL-122, Р-70 п. 3, НФТ-42).
 *
 * Сопоставление ключа домена со строкой каталога живёт таблицей, а не ветвлением, поэтому
 * «ключ без слов» компилятор больше не ловит. Ловит этот тест: он и есть замена
 * исчерпывающему `when`, и без него новый ключ домена молча остался бы без строки
 * в шести каталогах — то есть без слов для человека (П-7).
 */
class ProTextsTest {

    @Test
    fun `every notice of the domain has words in the catalog`() {
        val missing = ProNotice.entries.filterNot { it in proStringKeys }

        assertEquals(emptyList<ProNotice>(), missing)
    }
}
