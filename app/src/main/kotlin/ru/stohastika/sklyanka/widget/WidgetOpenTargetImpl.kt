package ru.stohastika.sklyanka.widget

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.MainActivity
import ru.stohastika.sklyanka.feature.widgets.open.WidgetOpenTarget
import ru.stohastika.sklyanka.feature.widgets.open.WidgetPlace
import ru.stohastika.sklyanka.navigation.WidgetLaunch
import javax.inject.Inject

/**
 * Открытие приложения с плиты виджета (SKL-118, §5.11).
 *
 * Живёт в `app`, потому что только приложение знает свои экраны: модуль виджетов зовёт
 * порт и не держит в разметке домашнего экрана имени активности. Место на экране
 * превращается в действие намерения ([WidgetLaunch]), маршрут по нему выбирает
 * [ru.stohastika.sklyanka.navigation.RootNavigation] — то же правило, что у ярлыка
 * быстрого доступа.
 *
 * Флаги намерения — не украшение: плита запускает активность из другого процесса,
 * и без `NEW_TASK` запуск был бы отказом, а `CLEAR_TOP` не даёт копиться экземплярам
 * корня при повторных нажатиях.
 */
class WidgetOpenTargetImpl @Inject constructor(@ApplicationContext private val appContext: Context) : WidgetOpenTarget {

    override fun intentFor(context: Context, place: WidgetPlace, medicationId: String?): Intent =
        Intent(appContext, MainActivity::class.java)
            .setAction(WidgetLaunch.actionFor(place))
            .putExtra(WidgetLaunch.EXTRA_MEDICATION_ID, medicationId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
