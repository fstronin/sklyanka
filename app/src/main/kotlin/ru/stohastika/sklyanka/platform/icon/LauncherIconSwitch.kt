package ru.stohastika.sklyanka.platform.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Переключение варианта иконки — два `activity-alias` на одну активность (§2.4, Р-31, SKL-120).
 *
 * Фильтр `MAIN`/`LAUNCHER` несёт не сама [ru.stohastika.sklyanka.MainActivity], а два алиаса —
 * по одному на вариант, и включён ровно один. Так система видит два знака одного приложения
 * и не поднимает вторую задачу: алиас запускает ту же активность.
 *
 * **Имя в лончере не меняется:** у обоих алиасов `android:label` — `app_name`, потому что
 * название в списке приложений принадлежит продукту, а не оформлению, и его смена сломала бы
 * узнавание ярлыка (В-120-2).
 *
 * **`DONT_KILL_APP` обязателен.** Без него смена состояния компонента перезапускает процесс:
 * человек, выбравший тёплую иконку, оказался бы выброшен из приложения на «Сегодня» —
 * ровно того, чего приложение обещает не делать.
 *
 * **Лончер кэширует иконку** и обновляет её, когда сочтёт нужным: это ограничение платформы,
 * и приложение его не скрывает — слова «готово» после выбора нет (П-7).
 *
 * Запись состояния компонента переживает перезапуск процесса, но не переустановку: поэтому
 * выбор применяется и на старте приложения, а не только в момент выбора, — иначе после
 * обновления алиасы вернулись бы к состоянию из манифеста и человек увидел бы чужую иконку.
 */
@Singleton
class LauncherIconSwitch @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Включить алиас варианта и выключить второй.
     *
     * Состояние читается перед записью: повторная установка того же состояния — лишний
     * вызов платформы на каждом старте приложения, а он не бесплатен.
     */
    fun apply(variant: IconVariant) {
        aliases().forEach { (candidate, alias) -> set(alias, enabled = candidate == variant) }
    }

    /** Имена алиасов из манифеста вместе с их вариантами. */
    private fun aliases(): Map<IconVariant, ComponentName> = mapOf(
        IconVariant.ICE to ComponentName(context.packageName, ALIAS_ICE),
        IconVariant.WARM to ComponentName(context.packageName, ALIAS_WARM),
    )

    private fun set(alias: ComponentName, enabled: Boolean) {
        val wanted = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (context.packageManager.getComponentEnabledSetting(alias) == wanted) return
        context.packageManager.setComponentEnabledSetting(alias, wanted, PackageManager.DONT_KILL_APP)
    }

    internal companion object {
        /** Алиас холодного варианта — базовый, включён в манифесте (Р-57). */
        const val ALIAS_ICE = "ru.stohastika.sklyanka.MainActivityIce"

        /** Алиас тёплого варианта — «Про» (§2.4). */
        const val ALIAS_WARM = "ru.stohastika.sklyanka.MainActivityWarm"
    }
}
