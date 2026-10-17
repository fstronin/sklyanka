package ru.stohastika.sklyanka.acceptance.world

import ru.stohastika.sklyanka.domain.startup.DatabaseLifeStore

/**
 * Отметки жизни базы в памяти сценария (МГ-8).
 *
 * В сценариях приёмки база не повреждается и платформой не стирается, поэтому
 * признак пересоздания по умолчанию снят и объяснение сброса не показывается.
 * Сценарий, которому нужно пересоздание, ставит его [recreate] вручную.
 */
class InMemoryDatabaseLife : DatabaseLifeStore {

    private var recreated = false
    private var life = false
    private var resetEventAt: Long? = null
    private var acknowledgedAt: Long? = null

    /** База пересоздана при этом открытии — то, что в продукте ставит колбэк Room. */
    fun recreate() {
        recreated = true
    }

    override fun consumeDatabaseRecreated(): Boolean {
        val value = recreated
        recreated = false
        return value
    }

    override suspend fun hadLife(): Boolean = life

    override suspend fun markLife() {
        life = true
    }

    override suspend fun resetEventAt(): Long? = resetEventAt

    override suspend fun recordResetEventAt(epochMillis: Long) {
        resetEventAt = epochMillis
    }

    override suspend fun resetEventAcknowledgedAt(): Long? = acknowledgedAt

    override suspend fun acknowledgeResetEventAt(epochMillis: Long) {
        acknowledgedAt = epochMillis
    }
}
