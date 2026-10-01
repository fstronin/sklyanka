package ru.stohastika.sklyanka.reminder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.stohastika.sklyanka.domain.reminder.AcknowledgeDeliveryCheckUseCase
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckRule
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.FinishDeliveryCheckOnEntryUseCase
import ru.stohastika.sklyanka.domain.reminder.port.DeliveryCheckAcknowledgements
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Объявление результата самопроверки доставки при входе (SKL-86, ОВ-108).
 *
 * **Зачем отдельный держатель.** Результат живёт в журнале, но показать его должен корень
 * приложения: на «Проверку напоминаний» человек сам не пойдёт, а пойдёт он туда, когда
 * уже потерял напоминания. Поэтому исход читается здесь, а баннер ставит `MainActivity`
 * над содержимым.
 *
 * **Почему подтверждение постоянное.** Результат проверки не кончается сам: без записи
 * «объявлено» баннер о вчерашней неудаче возвращался бы при каждом входе — та же ложь,
 * что вечная галочка. Момент проверки, а не флаг: следующая проверка объявляется заново.
 *
 * **Почему refresh закрывает истёкшую проверку.** Вход после срока без срабатывания —
 * это и есть ответ «не дошло»; заодно снимается тестовый будильник, чтобы позднее
 * срабатывание не спорило с названным исходом.
 */
@Singleton
class DeliveryCheckAnnouncer @Inject constructor(
    private val finishCheck: FinishDeliveryCheckOnEntryUseCase,
    private val acknowledgeCheck: AcknowledgeDeliveryCheckUseCase,
    private val acknowledgements: DeliveryCheckAcknowledgements,
) {

    private val _current = MutableStateFlow<DeliveryCheckState.Finished?>(null)

    /** Что показать над содержимым. `null` — объявлять нечего или уже объявлено. */
    val current: StateFlow<DeliveryCheckState.Finished?> = _current.asStateFlow()

    suspend fun refresh() {
        val state = finishCheck()
        _current.value = DeliveryCheckRule.announcementOf(state, acknowledgements.announced())
    }

    /** Результат доставлен: дальше он виден блоком на «Проверке напоминаний» (ОВ-108). */
    suspend fun acknowledge() {
        _current.value?.let { acknowledgeCheck(it.dueAt) }
        _current.value = null
    }
}
