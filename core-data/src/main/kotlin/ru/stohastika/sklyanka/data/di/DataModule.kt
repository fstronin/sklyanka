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
import ru.stohastika.sklyanka.data.room.DatabaseState
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.data.room.SklyankaMigrations
import ru.stohastika.sklyanka.data.room.dao.IntakeDao
import ru.stohastika.sklyanka.data.room.dao.MedicationDao
import ru.stohastika.sklyanka.data.room.dao.OpLogDao
import ru.stohastika.sklyanka.data.room.dao.ProfileDao
import ru.stohastika.sklyanka.data.room.databaseStateOf
import ru.stohastika.sklyanka.data.room.withForeignKeysEnabled
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
     * База приложения — и первое место, где решается её судьба (Р-44, `ADR-0012` п. 7).
     *
     * Состояние читается **до** `build()`: `Builder.build()` файла не касается, а открывает
     * его `withForeignKeysEnabled()`, и на базе новее ожидаемой именно там старая сборка
     * падала бы. Поэтому при [DatabaseState.Newer] база возвращается неоткрытой: граф
     * собирается (иначе приложение не дошло бы до экрана отказа), но ни одна выборка
     * не выполняется — их и не зовёт никто: старт и маршрут в этом состоянии пропускаются.
     *
     * Разрушающая деградация здесь не вызывается и не появится (МГ-2): отказ
     * честнее разрушения, а данные человека — единственная их копия.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, state: DatabaseState): SklyankaDatabase {
        val builder = Room.databaseBuilder(context, SklyankaDatabase::class.java, SklyankaDatabase.NAME)
        SklyankaMigrations.ALL.forEach { builder.addMigrations(it) }
        val database = builder.build()
        return if (state == DatabaseState.Newer) database else database.withForeignKeysEnabled()
    }

    /**
     * Состояние базы — один раз на процесс.
     *
     * Читается без Room (прямым `PRAGMA`), поэтому спрашивать его можно до сборки базы:
     * приёмник будильника обязан знать ответ раньше, чем коснётся графа.
     */
    @Provides
    @Singleton
    fun provideDatabaseState(@ApplicationContext context: Context): DatabaseState = databaseStateOf(context)

    @Provides
    fun provideProfileDao(database: SklyankaDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideMedicationDao(database: SklyankaDatabase): MedicationDao = database.medicationDao()

    @Provides
    fun provideIntakeDao(database: SklyankaDatabase): IntakeDao = database.intakeDao()

    @Provides
    fun provideOpLogDao(database: SklyankaDatabase): OpLogDao = database.opLogDao()

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(SETTINGS_NAME)
        }

    /** Имя файла постоянных настроек. Активный профиль живёт здесь, а не в памяти процесса (ПРФ-17). */
    private const val SETTINGS_NAME = "sklyanka_settings"
}
