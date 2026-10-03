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
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.ScreenName
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDateTime
import javax.inject.Inject
import android.graphics.Color as AndroidColor

/**
 * Полноэкранное напоминание поверх экрана блокировки (НАП-29).
 *
 * Своя Activity, а не экран внутри `MainActivity`: карточка поднимается системой по
 * полноэкранному намерению уведомления, в том числе когда приложение не запущено, и она
 * не должна ни поднимать навигацию приложения, ни зависеть от её состояния.
 *
 * Показ поверх блокировки включён и флагами окна, и атрибутами манифеста: атрибуты
 * `showWhenLocked`/`turnScreenOn` появились в API 27, а `minSdk` у проекта 26 (ПЛ-1),
 * поэтому на 26 работают устаревшие флаги окна — вторая ветка здесь не дублирование,
 * а единственный способ выполнить требование на всей поддерживаемой полосе версий.
 */
@AndroidEntryPoint
class ReminderActivity : ComponentActivity() {

    /**
     * Язык интерфейса закрепляется до первого обращения к ресурсам (НФТ-43а, `ADR-0008`).
     *
     * Без этого Android применил бы к русским строкам правила множественного числа языка
     * телефона, а даты вывел бы его словами.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withInterfaceLocale())
    }

    private val viewModel: ReminderViewModel by viewModels()

    /** Канал событий: экран — отдельная Activity, и корень приложения его не видит (SKL-82). */
    @Inject
    lateinit var analytics: Analytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics.track(AnalyticsEvent.ScreenView(ScreenName.REMINDER))
        // Тёмное полотно — светлые значки системных панелей (SKL-72 §4). Явный стиль,
        // а не умолчание: умолчание выбрало бы значки по системной теме телефона.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        showOverLockScreen()
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        open(intent)

        setContent {
            // Тёмная среда — параметром темы, а не системной настройкой: тёмными бывают
            // напоминание и знак, а не приложение целиком (docs/spec/08-design-system.md §5).
            SklyankaTheme(dark = true) {
                val state by viewModel.state.collectAsState()
                ReminderCardHost(
                    state = state,
                    actions = ReminderActions(
                        onTaken = viewModel::onTaken,
                        onSkipped = viewModel::onSkipped,
                        onTakenAll = viewModel::onTakenAll,
                        onSkippedAll = viewModel::onSkippedAll,
                        onSnooze = viewModel::onSnooze,
                    ),
                    onClosed = { finish() },
                )
            }
        }
    }

    /** Карточка может прийти второй раз — тем же слотом или другим: состояние перечитывается. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    private fun open(intent: Intent) {
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        val scheduledAt = intent.getStringExtra(EXTRA_SCHEDULED_AT)
        // Намерение без слота показывать нечего: карточка закрывается, а не гадает,
        // какой приём человек имел в виду (SKL-32).
        if (profileId == null || scheduledAt == null) {
            viewModel.close()
        } else {
            viewModel.open(profileId, LocalDateTime.parse(scheduledAt))
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
        private const val EXTRA_PROFILE_ID = "ru.stohastika.sklyanka.reminder.profile_id"
        private const val EXTRA_SCHEDULED_AT = "ru.stohastika.sklyanka.reminder.scheduled_at"

        /**
         * Намерение карточки приёма.
         *
         * Слот передаётся значениями, а не идентификатором приёма: карточка показывает все
         * приёмы своего времени, и её состав может измениться между постановкой будильника
         * и показом (НАП-36).
         */
        fun intentFor(context: Context, profileId: String, scheduledAt: LocalDateTime): Intent =
            Intent(context, ReminderActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_PROFILE_ID, profileId)
                .putExtra(EXTRA_SCHEDULED_AT, scheduledAt.toString())
    }
}
