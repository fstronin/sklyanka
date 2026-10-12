package ru.stohastika.sklyanka.platform.onboarding

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.domain.onboarding.VendorClassifier
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile
import ru.stohastika.sklyanka.domain.onboarding.VendorProfileReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Определение прошивки по фактам устройства (`TDD.md §5.1`).
 *
 * Знание `Build.MANUFACTURER` и пакета системного менеджера живёт здесь, а не в домене:
 * домен работает с абстрактным профилем и не знает имён пакетов чужих прошивок.
 *
 * Признаки Xiaomi и Samsung — строки производителя и бренда, и отдельного чтения их
 * менеджеров для классификации не нужно; видимость этих пакетов объявлена в манифесте
 * ради открытия их разделов, а не ради определения профиля.
 */
@Singleton
class AndroidVendorProfileReader @Inject constructor(@ApplicationContext private val context: Context) :
    VendorProfileReader {

    override fun profile(): VendorProfile =
        VendorClassifier.classify(Build.MANUFACTURER, Build.BRAND, huaweiSystemManagerPresent())

    /**
     * Есть ли на устройстве системный менеджер EMUI.
     *
     * Признак косвенный, но надёжнее строки производителя: на сборках, где `Build` переименован
     * (Honor), менеджер остаётся на месте. Видимость пакета объявлена в манифесте ради адресов
     * автозапуска, отдельного разрешения не требуется.
     */
    private fun huaweiSystemManagerPresent(): Boolean = runCatching {
        context.packageManager.getPackageInfo(SystemScreenTargets.HUAWEI_SYSTEM_MANAGER, 0)
    }.isSuccess
}
