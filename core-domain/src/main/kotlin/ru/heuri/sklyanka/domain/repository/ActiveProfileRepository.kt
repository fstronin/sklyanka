package ru.heuri.sklyanka.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Контекст активного профиля.
 *
 * Хранится в постоянных настройках приложения, а не в состоянии навигации и не в поле
 * в памяти процесса (ПРФ-17): приложение обязано открыться в том профиле, который был
 * активен на момент выхода (ПРФ-18).
 */
interface ActiveProfileRepository {

    val activeProfileId: Flow<String>

    suspend fun current(): String

    suspend fun switchTo(profileId: String)
}
