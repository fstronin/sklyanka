package ru.stohastika.sklyanka.feature.history

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import ru.stohastika.sklyanka.domain.history.ReportDocument

/**
 * Проверка обработчика системного выбора места (SKL-46).
 *
 * Проверяется то, на чём стоит честный отказ: без обработчика в устройстве намерение
 * не разрешается, с зарегистрированным — разрешается. Настоящий `DocumentsUI` на JVM
 * не существует, и его отсутствием представлен образ без него.
 */
@RunWith(AndroidJUnit4::class)
class DocumentSaveSupportTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val document = ReportDocument(
        fileName = "report.pdf",
        mediaType = "application/pdf",
        bytes = ByteArray(0),
    )

    @Test
    fun aDeviceWithoutAPlaceChooserIsReportedAsSuch() {
        assertFalse(canCreateDocument(context, document))
    }

    @Test
    fun aRegisteredPlaceChooserIsReportedAsAvailable() {
        val probe = ActivityResultContracts.CreateDocument(document.mediaType)
            .createIntent(context, document.fileName)
        shadowOf(context.packageManager).addResolveInfoForIntent(probe, chooser())

        assertTrue(canCreateDocument(context, document))
    }

    /** Системный выбор места: пакет и Activity, которых на JVM нет. */
    private fun chooser() = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            packageName = "com.android.documentsui"
            name = "com.android.documentsui.picker.PickActivity"
        }
    }
}
