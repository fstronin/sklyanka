package ru.stohastika.sklyanka.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.stohastika.sklyanka.domain.profile.SwitchReason

/**
 * Контекст активного профиля.
 *
 * Хранится в постоянных настройках приложения, а не в состоянии навигации и не в поле
 * в памяти процесса (ПРФ-17): приложение обязано открыться в том профиле, который был
 * активен на момент выхода (ПРФ-18).
 *
 * Правила «на какой профиль переключаться можно» здесь нет намеренно — это предметное
 * решение, и живёт оно в `domain.profile` (`SwitchProfileUseCase`). Хранилище лишь
 * записывает значение, держит инвариант «указывает на существующий видимый профиль»
 * и оставляет след.
 */
interface ActiveProfileRepository {

    val activeProfileId: Flow<String>

    suspend fun current(): String

    suspend fun switchTo(profileId: String)

    /**
     * То же переключение, но с причиной для журнала (`TDD.md §11.1`).
     *
     * Отдельной перегрузкой, а не параметром основного метода: «почему я оказался
     * в мамином профиле» — вопрос, ответ на который обязан быть восстановим, но требовать
     * причину от каждого вызывающего значит поставить в четырёх местах одно и то же
     * значение. Реализация переопределяет её; тем, кто причину не различает, достаётся
     * поведение без неё.
     */
    suspend fun switchTo(profileId: String, reason: SwitchReason) {
        switchTo(profileId)
    }
}
