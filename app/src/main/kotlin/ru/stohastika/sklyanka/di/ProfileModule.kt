package ru.stohastika.sklyanka.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.profile.CreateProfileUseCase
import ru.stohastika.sklyanka.domain.profile.DeleteProfileUseCase
import ru.stohastika.sklyanka.domain.profile.OpenReminderProfileUseCase
import ru.stohastika.sklyanka.domain.profile.ProfileEraser
import ru.stohastika.sklyanka.domain.profile.ProfileIdSource
import ru.stohastika.sklyanka.domain.profile.ProfileSetReplanner
import ru.stohastika.sklyanka.domain.profile.SetMirrorRemindersUseCase
import ru.stohastika.sklyanka.domain.profile.SwitchProfileUseCase
import ru.stohastika.sklyanka.domain.profile.UuidProfileIds
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.reminder.ReminderPlanningTrigger
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import javax.inject.Singleton

/**
 * Сборка профилей (SKL-15).
 *
 * Сценарии живут в домене и об Hilt не знают: домен не видит ни `javax.inject`, ни `dagger`
 * (НФТ-46), поэтому объекты собираются здесь, а не аннотацией на конструкторе.
 *
 * Здесь же сходятся две вещи, которые больше нигде вместе не встречаются: палитра цветов
 * профиля из дизайн-системы (`core-ui`) и правило «цвет только из набора» из домена
 * (ПРФ-15). Домен не видит `core-ui` — направление зависимостей идёт внутрь, — поэтому
 * набор допустимых значений передаётся ему параметром, а собирает связку `app`.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProfileModule {

    /**
     * Перепланирование при изменении **состава** профилей (ПРФ-6, ПРФ-30).
     *
     * Порядок «сначала приёмы, потом будильники» держит [ReminderPlanningTrigger] — тот же,
     * что и для всех прочих поводов (НАП-11). Смена активного профиля этот порт не вызывает
     * и вызвать не может: у неё нет на него зависимости (НАП-12).
     */
    @Provides
    @Singleton
    fun provideProfileSetReplanner(planning: ReminderPlanningTrigger): ProfileSetReplanner =
        ProfileSetReplanner { planning.planAll(RescheduleReason.PROFILE_SET_CHANGED) }

    @Provides
    @Singleton
    fun provideProfileIdSource(): ProfileIdSource = UuidProfileIds

    @Provides
    @Singleton
    fun provideSwitchProfile(profiles: ProfileRepository, active: ActiveProfileRepository): SwitchProfileUseCase =
        SwitchProfileUseCase(profiles = profiles, active = active)

    /**
     * Удаление чужого профиля (ОВ-7, SKL-36).
     *
     * Стирание — портом [ProfileEraser] (транзакция поверх Room), перепланирование —
     * тем же [ProfileSetReplanner], что у заведения профиля и переключения дублирования:
     * состав профилей изменился, и будильники удалённого обязаны сняться немедленно.
     */
    @Provides
    @Singleton
    fun provideDeleteProfile(
        profiles: ProfileRepository,
        eraser: ProfileEraser,
        active: ActiveProfileRepository,
        replan: ProfileSetReplanner,
        log: OperationLog,
    ): DeleteProfileUseCase = DeleteProfileUseCase(
        profiles = profiles,
        eraser = eraser,
        active = active,
        replan = replan,
        log = log,
    )

    @Provides
    @Singleton
    fun provideOpenReminderProfile(
        medications: MedicationRepository,
        profiles: ProfileRepository,
        switch: SwitchProfileUseCase,
    ): OpenReminderProfileUseCase = OpenReminderProfileUseCase(
        medications = medications,
        profiles = profiles,
        switch = switch,
    )

    @Provides
    @Singleton
    fun provideSetMirrorReminders(
        profiles: ProfileRepository,
        replan: ProfileSetReplanner,
        log: OperationLog,
    ): SetMirrorRemindersUseCase = SetMirrorRemindersUseCase(
        profiles = profiles,
        replan = replan,
        log = log,
    )

    /**
     * Заведение профиля.
     *
     * Набор допустимых цветов — [ProfilePalette]: закрытый набор дизайн-системы, в который
     * основной акцент приложения не входит (ПРФ-15). Произвольный цвет позволил бы назначить
     * профилю оттенок, неотличимый от своего, и индикация формально осталась бы, а человек
     * перестал бы её видеть.
     */
    @Provides
    @Singleton
    fun provideCreateProfile(
        profiles: ProfileRepository,
        ids: ProfileIdSource,
        replan: ProfileSetReplanner,
        log: OperationLog,
    ): CreateProfileUseCase = CreateProfileUseCase(
        profiles = profiles,
        palette = ProfilePalette.allowedArgb,
        ids = ids,
        replan = replan,
        log = log,
    )
}
