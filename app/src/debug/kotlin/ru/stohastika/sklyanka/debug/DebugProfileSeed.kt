package ru.stohastika.sklyanka.debug

import android.content.Context
import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.R
import ru.stohastika.sklyanka.domain.profile.CreateProfileUseCase
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.startup.StartupSeed
import ru.stohastika.sklyanka.ui.token.ProfileColor

/**
 * Второй профиль в отладочной сборке (`ОВ-3`, ПРФ-9).
 *
 * Экрана добавления профиля нет — он не описан ни в макетах, ни в спецификации, и решение
 * по нему принимает заказчик. Без второго профиля половину приёмки SKL-15 нельзя проверить
 * руками на устройстве, а проверять её на данных, записанных в базу в обход домена, значит
 * проверять не то приложение, которое работает у человека.
 *
 * Профиль создаётся тем же сценарием, что понадобится экрану добавления
 * ([CreateProfileUseCase]), а не прямой записью: правило «цвет только из набора» (ПРФ-15)
 * и след в журнале обязаны работать и здесь.
 *
 * `mirrorReminders = true` — **это значение отладочной заготовки, а не решение по `ОВ-3`.**
 * Значение по умолчанию при создании профиля остаётся открытым вопросом: от него зависит,
 * зазвонит ли телефон владельца в 09:00, и решает это заказчик. Здесь включено потому, что
 * приёмка требует проверить именно дублирование, а выключенное состояние проверяется
 * переключением флага.
 *
 * Класс живёт в `src/debug` и в релизный артефакт не попадает вовсе.
 */
class DebugProfileSeed(
    private val context: Context,
    private val profiles: ProfileRepository,
    private val createProfile: CreateProfileUseCase,
) : StartupSeed {

    override suspend fun seed() {
        // Повторный старт профиля не дублирует: заготовка ставит ровно один чужой профиль
        // и больше не вмешивается — иначе каждый запуск отладочной сборки добавлял бы ещё один.
        if (profiles.observeAll().first().any { !it.isSelf }) return
        createProfile(
            name = context.getString(R.string.debug_seed_profile_name),
            color = ProfileColor.Terracotta.argb,
            mirrorReminders = true,
        )
    }
}
