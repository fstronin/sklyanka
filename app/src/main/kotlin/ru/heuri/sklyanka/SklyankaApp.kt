package ru.heuri.sklyanka

import android.app.Application
import androidx.core.content.ContextCompat
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.heuri.sklyanka.data.log.LogRetention
import ru.heuri.sklyanka.data.repository.ProfileBootstrap
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.schedule.PlanReason
import ru.heuri.sklyanka.scheduler.IntakePlanningTrigger
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

    @Inject
    lateinit var intakePlanning: IntakePlanningTrigger

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startupScope.launch {
            profileBootstrap.ensureOwnerProfile(
                defaultName = getString(R.string.profile_self_default_name),
                color = ContextCompat.getColor(this@SklyankaApp, R.color.profile_self_default),
            )
            // Порядок важен: профиль владельца обязан существовать до того, как
            // генерация пойдёт по профилям, иначе первый запуск обходит пустой список.
            intakePlanning.planAll(PlanReason.APP_START)
            operationLog.prune(LogRetention.KEEP_DAYS, LogRetention.KEEP_RECORDS)
        }
    }
}
