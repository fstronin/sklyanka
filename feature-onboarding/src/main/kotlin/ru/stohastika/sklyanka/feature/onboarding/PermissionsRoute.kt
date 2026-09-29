package ru.stohastika.sklyanka.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep

/**
 * Хозяин экрана разрешений.
 *
 * Здесь и только здесь живёт системный запрос разрешения: контракт результата активности
 * требует Activity, и ни модель состояния, ни `SystemSettingsNavigator` его показать не могут.
 * Своего диалога разрешений приложение не рисует и нарисовать не вправе (ПЛ-13).
 *
 * @param onExit куда уходить с экрана. Решает хозяин навигации: `feature-*` друг о друге
 * не знают, и «Дальше» не может сам открыть экран подключения часов (SKL-17).
 */
@Composable
fun PermissionsRoute(
    viewModel: OnboardingViewModel,
    source: OnboardingSource,
    onExit: (OnboardingExit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val exit by viewModel.exit.collectAsState()

    val requestNotifications = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Ответ системы в состояние не пишется: состояние перечитывается у системы (НФТ-52).
        // Отказ не закрывает экран и не мешает пройти остальные шаги (НАП-18, критерий 5).
        viewModel.onPermissionResult(PermissionStep.NOTIFICATIONS, granted)
    }

    LaunchedEffect(source) { viewModel.start(source) }
    LaunchedEffect(exit) { exit?.let(onExit) }

    PermissionsScreen(
        state = state,
        actions = PermissionsActions(
            onStepAction = { step ->
                // Модель отвечает «да» только тогда, когда система действительно покажет
                // диалог: до Android 13 разрешения не существует, и шаг ведёт в настройки.
                if (viewModel.onStepAction(step)) requestNotifications.launch(POST_NOTIFICATIONS)
            },
            onAutostartConfirmed = viewModel::onAutostartConfirmed,
            onNext = viewModel::onNext,
            onSkip = viewModel::onSkip,
        ),
        modifier = modifier,
    )
}

/**
 * Имя разрешения строкой, а не константой платформы.
 *
 * `Manifest.permission.POST_NOTIFICATIONS` появилась в API 33 и при компиляции подставляется
 * значением, поэтому Android Lint справедливо считает её обращением к более новому API
 * (`InlinedApi`, ошибка по конвейеру проекта). Само значение неизменно, а решение «можно ли
 * вообще запрашивать» принято выше — портом `PermissionRequestSupport` (ПЛ-13).
 */
private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
