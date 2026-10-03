package ru.stohastika.sklyanka.feature.reminder

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.ScreenName
import ru.stohastika.sklyanka.domain.reminder.port.NotificationGateway
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDateTime
import javax.inject.Inject
import android.graphics.Color as AndroidColor

/**
 * Полноэкранная карточка тестового напоминания (SKL-86, ОВ-108).
 *
 * Своя Activity рядом с боевой карточкой: тестовая показывается поверх блокировки
 * тем же полноэкранным намерением, но не поднимает навигацию приложения и не имеет
 * действий отметки. Язык закрепляется до первого обращения к ресурсам (НФТ-43а, ADR-0008),
 * фон — тёмная среда параметром темы, как у [ReminderActivity].
 */
@AndroidEntryPoint
class DeliveryCheckActivity : ComponentActivity() {

    @Inject
    lateinit var notifications: NotificationGateway

    /** Канал событий: экран — отдельная Activity, и корень приложения его не видит (SKL-82). */
    @Inject
    lateinit var analytics: Analytics

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withInterfaceLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        showOverLockScreen()
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        val at = intent.getStringExtra(EXTRA_AT)?.let(LocalDateTime::parse)
        // Намерение без времени показывать нечего: карточка закрывается, а не гадает,
        // какое срабатывание человек имел в виду.
        if (at == null) {
            finish()
            return
        }
        analytics.track(AnalyticsEvent.ScreenView(ScreenName.DELIVERY_CHECK))
        setContent {
            SklyankaTheme(dark = true) {
                var closing by remember { mutableStateOf(false) }
                ReminderFade(visible = true, closing = closing, onClosed = { finish() }) {
                    DeliveryCheckScreen(
                        at = at,
                        onAcknowledge = {
                            lifecycleScope.launch {
                                notifications.cancelDeliveryCheck()
                                closing = true
                            }
                        },
                    )
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    companion object {
        private const val EXTRA_AT = "ru.stohastika.sklyanka.reminder.delivery_check_at"

        /** Намерение карточки проверки: время передаётся значением, слота и приёма нет. */
        fun intentFor(context: Context, at: LocalDateTime): Intent = Intent(context, DeliveryCheckActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_AT, at.toString())
    }
}
