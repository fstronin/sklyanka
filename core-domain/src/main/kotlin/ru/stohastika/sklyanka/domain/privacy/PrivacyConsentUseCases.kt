package ru.stohastika.sklyanka.domain.privacy

/**
 * Показывать ли диалог политики при запуске.
 *
 * Правило одно и то же для первого запуска и для запуска после гибели процесса
 * до нажатия «Принимаю»: пока факта нет, диалог показывается. Отдельного состояния
 * «первый запуск» не заводится — оно уже выражено отсутствием отметки.
 */
class ShouldShowPrivacyConsentUseCase(private val store: PrivacyConsentStore) {

    suspend operator fun invoke(): Boolean = !store.acknowledged()
}

/**
 * Человек нажал «Принимаю»: запомнить это до следующего запуска.
 *
 * Отдельный сценарий, а не вызов хранилища из экрана: решение «нажатие означает
 * принятие» — правило продукта, и оно проверяется тестом без Compose.
 */
class AcknowledgePrivacyConsentUseCase(private val store: PrivacyConsentStore) {

    suspend operator fun invoke() = store.acknowledge()
}
