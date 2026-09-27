package ru.heuri.sklyanka.acceptance

/**
 * Имя лекарства из текста сценария в том падеже, в котором его поставила фраза.
 *
 * Предыстория заводит «Магний B6», а проверка говорит «остаток упаковки «Магния B6»».
 * Это одно и то же лекарство, и требовать от сценария именительного падежа значило бы
 * переписывать документ приёмки под удобство шага — прямой запрет: подогнанный сценарий
 * хуже отсутствующего.
 *
 * Разбор грубый намеренно: сравниваются основы слов — от каждого русского слова длиной
 * больше четырёх букв отбрасывается последняя буква, если она гласная или «й». Латиница
 * и цифры («B6», «D3») не трогаются. Полноценная морфология здесь не нужна: имена
 * лекарств приходят из документов приёмки, их несколько десятков, и неоднозначность
 * ловится отказом, а не догадкой.
 */
object RussianNames {

    /**
     * Найти известное имя, к которому относится [text].
     *
     * @param known имена, заведённые Предысторией сценария
     * @return точное имя или `null`, если подходящего нет ровно одного
     */
    fun resolve(text: String, known: Collection<String>): String? {
        if (text in known) return text
        val wanted = stem(text)
        val matches = known.filter { stem(it) == wanted }
        return matches.singleOrNull()
    }

    private fun stem(name: String): String = name.trim().split(WHITESPACE)
        .joinToString(" ") { word -> stemOf(word) }

    private fun stemOf(word: String): String {
        if (word.length <= SHORT_WORD) return word
        val last = word.last().lowercaseChar()
        return if (last in ENDINGS) word.dropLast(1) else word
    }

    /** Короткое слово не склоняется настолько, чтобы его основу стоило угадывать. */
    private const val SHORT_WORD = 4

    /** Окончания, различающие падежи в именах лекарств: «Магний» — «Магния», «Эутирокс» — «Эутирокса». */
    private val ENDINGS = setOf('а', 'я', 'й', 'у', 'ю', 'е', 'ы', 'и', 'о')

    private val WHITESPACE = Regex("\\s+")
}
