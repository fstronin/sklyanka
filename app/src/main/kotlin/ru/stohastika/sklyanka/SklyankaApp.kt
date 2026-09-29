package ru.stohastika.sklyanka

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.data.log.LogRetention
import ru.stohastika.sklyanka.data.reminder.ReminderLogRetention
import ru.stohastika.sklyanka.data.repository.ProfileBootstrap
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.platform.notification.ReminderChannels
import ru.stohastika.sklyanka.reminder.ReminderOutageAnnouncer
import ru.stohastika.sklyanka.reminder.ReminderPlanningTrigger
import ru.stohastika.sklyanka.startup.StartupSeed
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.token.ProfilePalette
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

    /**
     * Перерыв в напоминаниях: признак считается здесь, а показывает его корень приложения
     * (НАП-38). Запись в журнал делает то же обнаружение.
     */
    @Inject
    lateinit var outageAnnouncer: ReminderOutageAnnouncer

    @Inject
    lateinit var reminderChannels: ReminderChannels

    @Inject
    lateinit var reminderJournal: ReminderJournal

    /**
     * Заготовки данных отладочной сборки (`ОВ-3`).
     *
     * В релизной сборке набор пуст: связка живёт в `src/debug`, и в релизном артефакте
     * её физически нет.
     */
    @Inject
    lateinit var seeds: Set<@JvmSuppressWildcards StartupSeed>

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
                // Имя профиля по умолчанию — текст интерфейса, и язык у него свой (НФТ-43а).
                defaultName = withInterfaceLocale().getString(R.string.profile_self_default_name),
                // Цвет профиля владельца берётся из палитры дизайн-системы, а не из
                // ресурсов приложения (SKL-15 закрывает замечание SKL-04): токен цвета
                // живёт в одном месте (НФТ-47), и в набор выбираемых цветов профиля он
                // не входит (ПРФ-15).
                color = ProfilePalette.ownerAccentArgb,
            )
            // Отладочная заготовка данных: второй профиль, без которого приёмку профилей
            // нечем пройти руками, пока нет экрана добавления (`ОВ-3`). В релизной сборке
            // набор пуст — связки там нет вовсе.
            seeds.forEach { it.seed() }
            // Порядок важен: профиль владельца обязан существовать до того, как
            // генерация пойдёт по профилям, иначе первый запуск обходит пустой список.
            // Дальше порядок держит ReminderPlanningTrigger: сначала генерация приёмов,
            // потом постановка будильников на окно
            // (SKL-13, НАП-11). Старт — единственное событие, которое на EMUI случается
            // наверняка, поэтому окно будильников пересобирается и здесь.
            reminderPlanning.planAll(RescheduleReason.APP_START)
            // Признак перерыва считается после пересборки: приложение могло быть
            // принудительно остановлено, и об этом человеку говорят прямо — сообщением
            // над содержимым при входе, а не только записью в журнале (НАП-38).
            outageAnnouncer.refresh()
            operationLog.prune(LogRetention.KEEP_DAYS, LogRetention.KEEP_RECORDS)
            reminderJournal.prune(ReminderLogRetention.KEEP_DAYS, ReminderLogRetention.KEEP_RECORDS)
        }
    }
}
