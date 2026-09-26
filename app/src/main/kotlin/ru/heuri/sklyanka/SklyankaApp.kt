package ru.heuri.sklyanka

import android.app.Application
import androidx.core.content.ContextCompat
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.heuri.sklyanka.data.log.LogRetention
import ru.heuri.sklyanka.data.reminder.ReminderLogRetention
import ru.heuri.sklyanka.data.repository.ProfileBootstrap
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.reminder.RecordReminderOutageUseCase
import ru.heuri.sklyanka.domain.reminder.RescheduleReason
import ru.heuri.sklyanka.domain.reminder.port.ReminderJournal
import ru.heuri.sklyanka.platform.notification.ReminderChannels
import ru.heuri.sklyanka.reminder.ReminderPlanningTrigger
import javax.inject.Inject

/**
 * Точка сборки графа зависимостей и первого запуска.
 *
 * Профиль владельца создаётся здесь, до всякого онбординга (ПРФ-25): данные не могут
 * оказаться без владельца, а экранов профилей не будет до SKL-15. Проверка делается при
 * каждом старте, а не только при создании базы, — базу мог создать перенос данных.
 *
 * Здесь же подрезается журнал операций. Подрезка идёт отдельной операцией и вне
 * транзакции: она не имеет права откатить отметку приёма.
 *
 * И здесь же пополняется окно приёмов (SKL-06): приложение на EMUI выгружается, и старт —
 * единственное событие, которое случается наверняка. Остальные события перепланирования
 * (перезагрузка, смена пояса, перевод часов, якорный будильник) приходят с SKL-13.
 */
@HiltAndroidApp
class SklyankaApp : Application() {

    @Inject
    lateinit var profileBootstrap: ProfileBootstrap

    @Inject
    lateinit var operationLog: OperationLog

    /**
     * Генерация приёмов и постановка будильников в одном порядке (SKL-06, SKL-13).
     *
     * Прямой доступ к генератору приёмов здесь больше не нужен: порядок «сначала приёмы,
     * потом будильники» держит один класс, и второй вызов из этого места означал бы
     * двойной проход по профилям при каждом старте.
     */
    @Inject
    lateinit var reminderPlanning: ReminderPlanningTrigger

    @Inject
    lateinit var recordOutage: RecordReminderOutageUseCase

    @Inject
    lateinit var reminderChannels: ReminderChannels

    @Inject
    lateinit var reminderJournal: ReminderJournal

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Каналы уведомлений создаются один раз и никогда не пересоздаются (ПЛ-10):
        // параметры канала неизменяемы, а удаление и создание канала с тем же
        // идентификатором восстанавливает прежний. Вызов синхронный и дешёвый —
        // без каналов не показать ни одного напоминания.
        reminderChannels.ensure()
        startupScope.launch {
            profileBootstrap.ensureOwnerProfile(
                defaultName = getString(R.string.profile_self_default_name),
                color = ContextCompat.getColor(this@SklyankaApp, R.color.profile_self_default),
            )
            // Порядок важен: профиль владельца обязан существовать до того, как
            // генерация пойдёт по профилям, иначе первый запуск обходит пустой список.
            // Дальше порядок держит ReminderPlanningTrigger: сначала генерация приёмов,
            // потом постановка будильников на окно
            // (SKL-13, НАП-11). Старт — единственное событие, которое на EMUI случается
            // наверняка, поэтому окно будильников пересобирается и здесь.
            reminderPlanning.planAll(RescheduleReason.APP_START)
            // Признак перерыва считается после пересборки: приложение могло быть
            // принудительно остановлено, и об этом человеку говорят прямо (НАП-38).
            recordOutage()
            operationLog.prune(LogRetention.KEEP_DAYS, LogRetention.KEEP_RECORDS)
            reminderJournal.prune(ReminderLogRetention.KEEP_DAYS, ReminderLogRetention.KEEP_RECORDS)
        }
    }
}
