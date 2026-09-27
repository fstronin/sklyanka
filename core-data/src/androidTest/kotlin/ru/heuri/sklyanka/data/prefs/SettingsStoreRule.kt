package ru.heuri.sklyanka.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import java.io.File

/**
 * Файл настроек для инструментальных проверок — с областью, которую есть кому закрыть.
 *
 * `DataStore` ведёт список файлов, открытых в процессе, и второй экземпляр поверх того же
 * файла отказывается работать («There are multiple DataStores active for the same file»).
 * Проверки класса идут в одном процессе одна за другой, поэтому экземпляр, созданный
 * проверкой, обязан закрываться вместе с ней: без этого первая проверка класса проходит,
 * а следующие падают — и падают не на своём предмете.
 *
 * Своя область сопрограмм здесь не украшение: файл освобождается вместе с завершением
 * работы области, и у экземпляра с областью по умолчанию закрыть файл нечем.
 */
class SettingsStoreRule(private val name: String) : ExternalResource() {

    private lateinit var file: File
    private var job: Job? = null
    private var store: DataStore<Preferences>? = null

    /** Настройки текущего «процесса». */
    val preferences: DataStore<Preferences> get() = requireNotNull(store) { "the rule has not started yet" }

    override fun before() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = context.preferencesDataStoreFile(name)
        file.delete()
        store = open()
    }

    override fun after() {
        close()
        file.delete()
    }

    /**
     * Перезапуск процесса: прежний экземпляр закрывается целиком, новый открывает тот же файл.
     *
     * Именно это перезапуск и означает — старого процесса больше нет. Второй экземпляр поверх
     * живого первого воспроизводил бы не перезапуск, а состояние, которого в продукте не бывает:
     * там хранилище настроек одно на приложение.
     */
    fun restart(): DataStore<Preferences> {
        close()
        return open().also { store = it }
    }

    private fun open(): DataStore<Preferences> {
        val supervisor = SupervisorJob()
        job = supervisor
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + supervisor),
            produceFile = { file },
        )
    }

    /**
     * Завершения области нужно дождаться: файл освобождается обработчиком завершения,
     * и без ожидания следующий экземпляр успевает открыться раньше, чем закрылся прежний.
     */
    private fun close() {
        val running = job ?: return
        running.cancel()
        runBlocking { running.join() }
        job = null
        store = null
    }
}
