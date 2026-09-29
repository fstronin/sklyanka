package ru.stohastika.sklyanka.feature.settings.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.profile.DeleteProfileOutcome
import ru.stohastika.sklyanka.domain.profile.DeleteProfileUseCase
import ru.stohastika.sklyanka.domain.profile.ProfileContextProvider
import ru.stohastika.sklyanka.domain.profile.ProfileLossesSource
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.transfer.ExportDataUseCase
import ru.stohastika.sklyanka.domain.transfer.ExportOutcome
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import javax.inject.Inject

/**
 * Состояние экрана удаления профиля (ОВ-7, SKL-36).
 *
 * Удаляемый профиль читается разово — [ProfileContextProvider.snapshot]: экран
 * открывается в контексте ровно того чужого профиля, которого называет строка настроек,
 * и пока экран открыт, контекст смениться сам не может (после удаления шаг уже `RESULT`).
 * Второго ответа «кого удаляем» не заводится.
 *
 * Числа потерь приходят потоком тем же запросом, что список и история: экран остаётся
 * открытым и во время системного выбора места выгрузки, и число обязано не разойтись
 * с тем, что будет стёрто.
 *
 * Выгрузка файла — та же ([ExportDataUseCase]), что на экране «Переноса данных»:
 * второго механизма и второго формата нет. Сохранение файла ничего не удаляет
 * и не заменяет подтверждение — оно лишь даёт человеку копию.
 */
@HiltViewModel
class DeleteProfileViewModel @Inject constructor(
    private val context: ProfileContextProvider,
    private val deleteProfile: DeleteProfileUseCase,
    private val losses: ProfileLossesSource,
    private val exportData: ExportDataUseCase,
    private val time: TimeSource,
) : ViewModel() {

    private val mutable = MutableStateFlow(DeleteProfileUiState())
    val state: StateFlow<DeleteProfileUiState> = mutable.asStateFlow()

    /** Подписка на числа потерь: отменяется при уходе и перезапускается при загрузке. */
    private var lossJob: Job? = null

    init {
        load()
    }

    fun onIntent(intent: DeleteProfileIntent) {
        when (intent) {
            is DeleteProfileIntent.WordChanged -> mutable.update { it.copy(word = intent.text) }
            is DeleteProfileIntent.ExportTargetChosen -> export(ExportTarget(intent.target))
            DeleteProfileIntent.NoFilePicker ->
                mutable.update { it.copy(failure = DeleteProfileFailure.NoFilePicker) }

            DeleteProfileIntent.Confirm -> confirm()
            DeleteProfileIntent.DismissFailure -> mutable.update { it.copy(failure = null) }
            DeleteProfileIntent.Leave -> reset()
        }
    }

    private fun load() {
        viewModelScope.launch {
            val profile = context.snapshot() ?: return@launch
            mutable.update {
                it.copy(
                    profileId = profile.id,
                    profileName = profile.name,
                    exportFileName = exportData.fileName(),
                    zone = time.zone(),
                )
            }
            observeLosses(profile.id)
        }
    }

    private fun observeLosses(profileId: String) {
        lossJob?.cancel()
        lossJob = viewModelScope.launch {
            losses.observe(profileId).collect { numbers -> mutable.update { it.copy(losses = numbers) } }
        }
    }

    private fun export(target: ExportTarget) {
        viewModelScope.launch {
            mutable.update { it.copy(busy = true, failure = null) }
            val outcome = exportData(target)
            mutable.update { current ->
                when (outcome) {
                    is ExportOutcome.Saved -> current.copy(
                        busy = false,
                        exportedAt = time.now(),
                        exportFileName = outcome.fileName,
                    )

                    is ExportOutcome.NotEnoughSpace ->
                        current.copy(busy = false, failure = DeleteProfileFailure.NoSpace)

                    ExportOutcome.Failed -> current.copy(busy = false, failure = DeleteProfileFailure.ExportFailed)
                }
            }
        }
    }

    /**
     * Единственная точка невозврата: точка подтверждения словом лежит на экране,
     * а сюда приходит только нажатие доступной кнопки.
     *
     * `OwnProfile`/`UnknownProfile` ничего не меняют: экран удаления открывается только
     * в контексте активного чужого профиля, и эти исходы сюда не приходят.
     */
    private fun confirm() {
        val profileId = mutable.value.profileId
        if (profileId.isEmpty()) return
        viewModelScope.launch {
            mutable.update { it.copy(busy = true, failure = null) }
            val outcome = deleteProfile(profileId)
            mutable.update {
                it.copy(
                    busy = false,
                    step = if (outcome is DeleteProfileOutcome.Deleted) DeleteProfileStep.RESULT else it.step,
                )
            }
        }
    }

    /** Уход с экрана: следующее открытие начинается с нового профиля и пустого слова (ПЕР-17). */
    private fun reset() {
        lossJob?.cancel()
        lossJob = null
        mutable.value = DeleteProfileUiState()
        load()
    }
}
