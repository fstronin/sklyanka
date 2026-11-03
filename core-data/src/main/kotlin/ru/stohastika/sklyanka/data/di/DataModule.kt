package ru.stohastika.sklyanka.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.data.room.DatabaseRecreation
import ru.stohastika.sklyanka.data.room.DatabaseState
import ru.stohastika.sklyanka.data.room.DatabaseUpgrade
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.data.room.SklyankaMigrations
import ru.stohastika.sklyanka.data.room.dao.AnalyticsDao
import ru.stohastika.sklyanka.data.room.dao.IntakeDao
import ru.stohastika.sklyanka.data.room.dao.MedicationDao
import ru.stohastika.sklyanka.data.room.dao.OpLogDao
import ru.stohastika.sklyanka.data.room.dao.ProfileDao
import ru.stohastika.sklyanka.data.room.withForeignKeysEnabled
import ru.stohastika.sklyanka.domain.time.TimeSource
import javax.inject.Singleton

/**
 * Сборка хранилища.
 *
 * Разрушающая миграция здесь не вызывается и не появится: она запрещена во всех
 * сборках, включая отладочную (МГ-2). Статических держателей базы и синглтонов
 * «по месту» нет — всё приходит через граф.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /**
     * База приложения — и первое место, где решается её судьба (Р-44, `ADR-0012` пп. 3, 7).
     *
     * Состояние читается **до** `build()`: `Builder.build()` файла не касается, а открывает
     * его `withForeignKeysEnabled()`, и на базе новее ожидаемой именно там старая сборка
     * падала бы. Поэтому при [DatabaseState.Newer] база возвращается неоткрытой: граф
     * собирается (иначе приложение не дошло бы до экрана отказа), но ни одна выборка
     * не выполняется — их и не зовёт никто: старт и маршрут в этом состоянии пропускаются.
     *
     * Здесь же снимается копия перед миграцией (`ADR-0012` п. 3): подготовка идёт до открытия,
     * потому что после него миграция уже случилась бы — а без копии она запрещена. Если места
     * на копию нет, миграция не выполняется вовсе, и база возвращается неоткрытой: третье
     * состояние ([DatabaseState.NotEnoughSpace]) — такой же отказ, как база новее.
     *
     * Разрушающая деградация здесь не вызывается и не появится (МГ-2): отказ
     * честнее разрушения, а данные человека — единственная их копия.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        upgrade: DatabaseUpgrade,
        recreation: DatabaseRecreation,
    ): SklyankaDatabase {
        upgrade.prepare()
        val builder = Room.databaseBuilder(context, SklyankaDatabase::class.java, SklyankaDatabase.NAME)
        SklyankaMigrations.ALL.forEach { builder.addMigrations(it) }
        // Признак пересоздания базы (МГ-8): `onCreate` срабатывает и на чистой
        // установке, и после стирания повреждённого файла платформой — отличить
        // одно от другого помогает отметка жизни базы вне файла.
        builder.addCallback(recreation.callback)
        val database = builder.build()
        // Состояние спрашивается **после** подготовки, а не приходит готовым значением:
        // отказ по месту, случившийся между решением и копией, обязан победить и здесь —
        // иначе база откроется и мигрирует без копии (`ADR-0012` п. 3).
        val open = upgrade.state() == DatabaseState.Ready
        return if (open) database.withForeignKeysEnabled() else database
    }

    /**
     * Подготовка базы к открытию (`ADR-0012` пп. 3, 4).
     *
     * Собирается здесь, а не конструктором с `@Inject`: имя файла базы — параметр
     * со значением по умолчанию, и Dagger потребовал бы отдельной привязки для строки,
     * которую в продукте всегда называют одним и тем же именем базы.
     */
    @Provides
    @Singleton
    fun provideDatabaseUpgrade(@ApplicationContext context: Context, time: TimeSource): DatabaseUpgrade =
        DatabaseUpgrade(context, time)

    @Provides
    fun provideProfileDao(database: SklyankaDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideMedicationDao(database: SklyankaDatabase): MedicationDao = database.medicationDao()

    @Provides
    fun provideIntakeDao(database: SklyankaDatabase): IntakeDao = database.intakeDao()

    @Provides
    fun provideOpLogDao(database: SklyankaDatabase): OpLogDao = database.opLogDao()

    /** Очередь событий аналитики (SKL-165, АН-4); адаптер — `RoomAnalyticsQueue`. */
    @Provides
    fun provideAnalyticsDao(database: SklyankaDatabase): AnalyticsDao = database.analyticsDao()

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(SETTINGS_NAME)
        }

    /** Имя файла постоянных настроек. Активный профиль живёт здесь, а не в памяти процесса (ПРФ-17). */
    private const val SETTINGS_NAME = "sklyanka_settings"
}
