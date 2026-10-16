package ru.stohastika.sklyanka.acceptance.world

import kotlinx.coroutines.flow.Flow
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.profile.CreateProfileUseCase
import ru.stohastika.sklyanka.domain.profile.DeleteProfileUseCase
import ru.stohastika.sklyanka.domain.profile.ProfileIdSource
import ru.stohastika.sklyanka.domain.profile.ProfileSetReplanner
import ru.stohastika.sklyanka.domain.profile.RenameProfileUseCase
import ru.stohastika.sklyanka.domain.profile.SetMirrorRemindersUseCase
import ru.stohastika.sklyanka.domain.profile.SetProfileColourUseCase
import ru.stohastika.sklyanka.domain.repository.ProfileRepository

/**
 * Правка состава профилей в мире сценария (SKL-121).
 *
 * Здесь живёт **ровно один** настоящий сценарий домена на каждое решение
 * (`CreateProfileUseCase`, `RenameProfileUseCase`, `SetProfileColourUseCase`,
 * `SetMirrorRemindersUseCase`, `DeleteProfileUseCase`), а правила не пересказываются:
 * «сверх двух», «предел пяти», «имя непусто», «цвет из палитры» решает домен, а не шаг
 * приёмки. Это то же разделение, что у переноса данных и удаления профиля: сценарий
 * подставляет хранилище в памяти, а не копию продукта (`ADR-0007`).
 *
 * **Палитра приходит числом, а не из `core-ui`.** Набор цветов профиля живёт
 * в `core-ui` (`ProfileColor`), а домен его не видит (направление зависимостей внутрь).
 * Чистый JVM-артефакт приёмки тоже его не видит — Android сюда не приезжает, — поэтому
 * пять значений ARGB приходят константами, а полноту набора держит тест `core-ui`,
 * а не эта копия. Значения сверены с `Palette.Profile*` (`SKL-121`, §2.2).
 *
 * **Хранилище доводит заведение нового профиля до мира.** Хранилище сценария
 * (`ScenarioRepositories.Profiles`) кладёт запись только поверх уже известного
 * идентификатора; заведение нового профиля с новым идентификатором оно бы потеряло.
 * Поэтому [Store] повторяет наблюдаемое следствие `upsert` базы: незнакомый
 * идентификатор — профиль появляется в мире, знакомый — обновляется. Так же ведёт себя
 * `ScenarioProfiles.insert` при переносе данных.
 */
class ScenarioProfileManagement(
    private val profiles: ScenarioProfiles,
    /**
     * Хранилище профилей сценария (`world.repositories.profileStore`); поверх него
     * собирается [Store], доводящий заведение нового профиля до мира.
     */
    repository: ProfileRepository,
    replan: ProfileSetReplanner,
    log: OperationLog,
    private val deletion: ScenarioProfileDeletion,
) {

    /** Идентификаторы заведённых сценарием профилей: детерминированные, чтобы связи читались. */
    private val ids: ProfileIdSource = ScenarioProfileIds()

    private val store: ProfileRepository = Store(repository)

    /** Заведение нового профиля (ПРФ-9, ПРФ-32, ПРФ-33, Р-84). */
    val create: CreateProfileUseCase = CreateProfileUseCase(store, PALETTE, ids, replan, log)

    /** Правка имени любого профиля (ПРФ-27). */
    val rename: RenameProfileUseCase = RenameProfileUseCase(store, log)

    /** Правка цвета чужого профиля (ПРФ-15, ПРФ-27). */
    val colour: SetProfileColourUseCase = SetProfileColourUseCase(store, PALETTE, log)

    /** Дублирование напоминаний чужого профиля (ПРФ-5, ПРФ-6, НАП-12). */
    val mirror: SetMirrorRemindersUseCase = SetMirrorRemindersUseCase(store, replan, log)

    /** Удаление чужого профиля тем же сценарием, что и «Удалить профиль» (ПРФ-28, ПРФ-31). */
    val delete: DeleteProfileUseCase = deletion.useCase

    /** Сколько профилей заведено — источник для [ru.stohastika.sklyanka.domain.profile.ProfileCreationGate]. */
    suspend fun count(): Int = store.count()

    /**
     * Чужой профиль Предыстории: имя и цвет приходят строкой сценария.
     *
     * Владельца этим путём завести нельзя (ИНВ-1): строка Предыстории «активен профиль «Я»»
     * уже завела его первым, а второй профиль владельца не бывает.
     */
    fun addForeign(name: String, colour: Int): Profile {
        val profile = Profile(
            id = ids.newId(),
            name = name,
            color = colour,
            isSelf = false,
            // Чужой профиль зеркалит напоминания владельцу, пока Предыстория не скажет иначе (ПРФ-12).
            mirrorReminders = true,
        )
        profiles.insert(profile)
        return profile
    }

    /**
     * Хранилище профилей, доводящее заведение нового профиля до мира.
     *
     * Повторяется наблюдаемое следствие `upsert` базы: запись с незнакомым идентификатором
     * появляется, знакомая — обновляется. Второй копии правил здесь нет — только это одно
     * следствие, без которого настоящий сценарий заведения не был бы виден сценарию.
     */
    private inner class Store(private val delegate: ProfileRepository) : ProfileRepository {

        override fun observeAll(): Flow<List<Profile>> = delegate.observeAll()

        override suspend fun get(profileId: String): Profile? = delegate.get(profileId)

        override suspend fun getSelf(): Profile? = delegate.getSelf()

        override suspend fun save(profile: Profile) {
            if (delegate.get(profile.id) == null) profiles.insert(profile) else delegate.save(profile)
        }

        override suspend fun count(): Int = delegate.count()
    }

    companion object {

        /** Пять цветов закрытой палитры (ПРФ-15, Р-20, Р-84): `Palette.Profile*` как ARGB. */
        val PALETTE: Set<Int> = setOf(
            0xFF8F3E23.toInt(), // Терракота
            0xFF1F5F6B.toInt(), // Бирюза
            0xFF3C5014.toInt(), // Олива
            0xFF6B4A6B.toInt(), // Слива
            0xFF4555B1.toInt(), // Индиго
        )
    }
}

/**
 * Идентификаторы профилей сценария: последовательные, а не случайные.
 *
 * Так же, как в тестах ядра (`SequentialProfileIds`): сценарий проверяет, что заведённый
 * профиль получает **свой** идентификатор и не затирает существующий, и случайные значения
 * доказывали бы меньше — по ним связи профилей не читались бы глазами.
 */
private class ScenarioProfileIds : ProfileIdSource {

    private var next = 0

    override fun newId(): String = "scenario-profile-${++next}"
}
