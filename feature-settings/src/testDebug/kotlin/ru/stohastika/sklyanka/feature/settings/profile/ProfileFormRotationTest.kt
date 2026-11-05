package ru.stohastika.sklyanka.feature.settings.profile

import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.profile.CreateProfileUseCase
import ru.stohastika.sklyanka.domain.profile.ProfileIdSource
import ru.stohastika.sklyanka.domain.profile.ProfileSetReplanner
import ru.stohastika.sklyanka.domain.profile.RenameProfileUseCase
import ru.stohastika.sklyanka.domain.profile.SetMirrorRemindersUseCase
import ru.stohastika.sklyanka.domain.profile.SetProfileColourUseCase
import ru.stohastika.sklyanka.feature.settings.FakeProfiles
import ru.stohastika.sklyanka.feature.settings.transfer.RecordingLog
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.ProfilePalette

/**
 * Поворот экрана не теряет ни мастер профиля, ни введённое в него (ПРФ-9, ПРФ-27, SKL-121).
 *
 * Проверяется настоящее пересоздание активности со сменой конфигурации — не подмена композиции
 * стендом: `configurationChange` проводит активность по жизненному циклу, сохраняет состояние
 * композиции и отдаёт его новой сборке. Так же ведёт себя телефон при повороте.
 *
 * Черновик живёт в ViewModel (она пересоздание переживает), но безусловное открытие формы
 * затирало бы его: после поворота в мастере имя пустело, а цвет брался первым свободным —
 * образец формы лекарства (SKL-142) и причина, по которой форма открывается один раз на заход.
 * Маршрут состояния не сбрасывает: уход с экрана не должен обнулять то, что человек набрал
 * и что переживёт пересоздание.
 *
 * Место формы в навигации и возврат из неё держит `app` (SKL-121, `SettingsSection`): здесь
 * проверяется то, что принадлежит форме.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class ProfileFormRotationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val profiles = FakeProfiles(listOf(SELF, WARD))
    private val log = RecordingLog()
    private val replan = ProfileSetReplanner { }
    private val ids = object : ProfileIdSource {
        private var next = 0
        override fun newId(): String = "created-${++next}"
    }

    private val form = ProfileFormViewModel(
        profiles = profiles,
        createProfile = CreateProfileUseCase(
            profiles = profiles,
            palette = ProfilePalette.allowedArgb,
            ids = ids,
            replan = replan,
            log = log,
        ),
        renameProfile = RenameProfileUseCase(profiles = profiles, log = log),
        setProfileColour = SetProfileColourUseCase(
            profiles = profiles,
            palette = ProfilePalette.allowedArgb,
            log = log,
        ),
        setMirrorReminders = SetMirrorRemindersUseCase(profiles = profiles, replan = replan, log = log),
    )

    @After
    fun tearDownTheStand() {
        controllers.forEach { it.pause().stop().destroy() }
        controllers.clear()
        ProfileFormHost.factory = null
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Стенд убирает за собой: брошенная активность мешает чужим проверкам. */
    private val controllers = mutableListOf<ActivityController<ProfileFormHost>>()

    private fun host(mode: ProfileFormMode): ActivityController<ProfileFormHost> {
        ProfileFormHost.mode = mode
        ProfileFormHost.factory = ProfileFormFactory(form)
        return Robolectric.buildActivity(ProfileFormHost::class.java).setup().also { controllers += it }
    }

    @Test
    fun theWizardKeepsItsTypedNameAndTheSwitchedOffMirroringAcrossRotation() {
        val controller = host(ProfileFormMode.Add)
        idle()
        waitForTag(TAG_PROFILE_FORM)

        typeName(NEW_NAME)
        composeRule.onNodeWithTag(TAG_PROFILE_MIRROR).performClick()
        composeRule.waitForIdle()
        assertEquals("the wizard must offer the mirroring switched on (Р-29)", ToggleableState.Off, mirroring())

        rotate(controller)

        composeRule.onNodeWithTag(TAG_PROFILE_FORM).assertExists()
        assertEquals("what was typed must survive the rotation", NEW_NAME, typedName())
        assertEquals(
            "the switched-off mirroring must survive the rotation",
            ToggleableState.Off,
            mirroring(),
        )
    }

    @Test
    fun theEditFormKeepsItsTypedNewNameAcrossRotation() {
        val controller = host(ProfileFormMode.Edit(WARD.id))
        idle()
        waitForTag(TAG_PROFILE_FORM)
        assertEquals("the edit form must start with the profile name", WARD.name, typedName())

        typeName(RENAMED)
        rotate(controller)

        composeRule.onNodeWithTag(TAG_PROFILE_FORM).assertExists()
        assertEquals("the typed new name must survive the rotation", RENAMED, typedName())
    }

    /** Поворот: настоящая смена конфигурации, за которой следует пересоздание активности. */
    private fun rotate(controller: ActivityController<ProfileFormHost>) {
        val config = Configuration(controller.get().resources.configuration)
        config.orientation = Configuration.ORIENTATION_LANDSCAPE
        controller.configurationChange(config)
        controller.visible()
        idle()
        composeRule.waitForIdle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(WAIT_MS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Набрать имя: в правке поле уже несёт имя профиля, поэтому текст замещается. */
    private fun typeName(text: String) {
        composeRule.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_PROFILE_NAME_FIELD)),
            useUnmergedTree = true,
        ).performTextReplacement(text)
        composeRule.waitForIdle()
    }

    /** Введённое в поле имени: у поля читается его редактируемый текст, а не подпись. */
    private fun typedName(): String = composeRule.onNode(
        hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_PROFILE_NAME_FIELD)),
        useUnmergedTree = true,
    ).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    /** Состояние вопроса о дублировании: `Off` — человек его выключил. */
    private fun mirroring(): ToggleableState? = composeRule
        .onNodeWithTag(TAG_PROFILE_MIRROR)
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.ToggleableState)

    private companion object {

        val SELF = Profile(id = "self", name = "Я", color = 0xFF5F6F2E.toInt(), isSelf = true, mirrorReminders = false)

        val WARD = Profile(
            id = "ward",
            name = "Мама",
            color = ProfilePalette.all[0].argb,
            isSelf = false,
            mirrorReminders = true,
        )

        const val NEW_NAME = "Бабушка"
        const val RENAMED = "Анна"
        const val WAIT_MS = 5_000L
    }
}

/**
 * Хозяин экрана: форма профиля с настоящей моделью активности.
 *
 * Модель приходит из `ViewModelStore` активности — тем же путём, каким она живёт в продукте,
 * и потому пересоздание её переживает.
 */
class ProfileFormHost : ComponentActivity() {

    val form: ProfileFormViewModel by viewModels { requireNotNull(factory) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SklyankaTheme {
                ProfileFormRoute(viewModel = form, mode = mode, onLeave = {})
            }
        }
    }

    companion object {
        var factory: ViewModelProvider.Factory? = null

        /** Режим формы: у стенда он один на активность — как у маршрута в продукте. */
        var mode: ProfileFormMode = ProfileFormMode.Add
    }
}

/** Фабрика стенда: отдаёт модель из того же мира, что и проверка. */
class ProfileFormFactory(private val form: ProfileFormViewModel) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(ProfileFormViewModel::class.java) -> form as T
        else -> error("unexpected view model: ${modelClass.name}")
    }
}
