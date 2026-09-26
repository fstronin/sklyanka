package ru.heuri.sklyanka.data.transfer

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Цепочка миграций формата файла (ПЕР-30).
 *
 * Настоящих шагов пока нет: выпущена одна версия формата, и файла более старой версии
 * не существует. Механизм проверяется на подставных шагах, потому что иначе первая
 * настоящая миграция окажется и первым запуском этого кода — а запускать его придётся
 * на единственной резервной копии человека.
 */
class TransferFormatMigrationsTest {

    @Test
    fun `nothing to do when the file version already matches`() {
        val document = buildJsonObject { put("marker", JsonPrimitive(1)) }

        val migrated = TransferFormatMigrations.migrate(document, fromVersion = 1, toVersion = 1, steps = emptyList())

        assertSame(document, migrated)
    }

    @Test
    fun `steps are applied in order, one version at a time`() {
        val document = buildJsonObject { put(FIELD, JsonPrimitive("")) }

        val migrated = TransferFormatMigrations.migrate(
            document = document,
            fromVersion = 1,
            toVersion = 3,
            steps = listOf(appending(1, "a"), appending(2, "b")),
        )

        assertEquals("ab", migrated?.get(FIELD)?.jsonPrimitive?.content)
    }

    @Test
    fun `a missing step is a refusal, not a silent skip`() {
        val document = buildJsonObject { put(FIELD, JsonPrimitive("")) }

        val migrated = TransferFormatMigrations.migrate(
            document = document,
            fromVersion = 1,
            toVersion = 3,
            steps = listOf(appending(1, "a")),
        )

        assertNull(migrated)
    }

    @Test
    fun `the released build carries no migration steps yet`() {
        assertEquals(emptyList<TransferFormatMigration>(), TransferFormatMigrations.ALL)
    }

    /** Подставной шаг: дописывает букву в поле, чтобы порядок применения был виден. */
    private fun appending(version: Int, mark: String) = object : TransferFormatMigration {
        override val from: Int = version

        override fun migrate(document: JsonObject): JsonObject = buildJsonObject {
            document.forEach { (key, value) -> put(key, value) }
            put(FIELD, JsonPrimitive(document[FIELD]?.jsonPrimitive?.content.orEmpty() + mark))
        }
    }

    private companion object {
        const val FIELD = "trace"
    }
}
