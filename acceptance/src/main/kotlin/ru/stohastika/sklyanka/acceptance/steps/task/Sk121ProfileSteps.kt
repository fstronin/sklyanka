package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.RussianNames
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.ScenarioProfileManagement
import ru.stohastika.sklyanka.acceptance.world.TransferFileBuilder
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.pro.ProBenefit
import ru.stohastika.sklyanka.domain.pro.ProPoint
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.domain.profile.CreateProfileOutcome
import ru.stohastika.sklyanka.domain.profile.DeleteProfileOutcome
import ru.stohastika.sklyanka.domain.profile.MirrorRemindersOutcome
import ru.stohastika.sklyanka.domain.profile.ProfileCreationDecision
import ru.stohastika.sklyanka.domain.profile.ProfileCreationGate
import ru.stohastika.sklyanka.domain.profile.RenameProfileOutcome
import ru.stohastika.sklyanka.domain.profile.SetProfileColourOutcome
import ru.stohastika.sklyanka.domain.transfer.TransferMode
import ru.stohastika.sklyanka.domain.transfer.TransferOutcome
import java.io.File
import java.time.YearMonth

/**
 * Шаги приёмки SKL-121 — профили сверх двух: мастер добавления, платная граница и предел пяти
 * (ПРФ-9, ПРФ-15, ПРФ-26, ПРФ-27, ПРФ-32…ПРФ-34, Р-84).
 *
 * Правила не пересказываются: границу «сверх двух» и предел пяти решает настоящий
 * `ProfileCreationGate`, заведение — `CreateProfileUseCase`, правку имени и цвета —
 * `RenameProfileUseCase` и `SetProfileColourUseCase`, дублирование — `SetMirrorRemindersUseCase`,
 * удаление — `DeleteProfileUseCase` (`world.profileManagement`). Мир сценария подставляет
 * только хранилище в памяти (`ADR-0007`).
 *
 * **Состояние мастера живёт здесь.** Экрана добавления профиля у сценарного прогона нет
 * (`ОВ-3`), поэтому черновик формы держится полями этого класса — как у формы расписания
 * в `Sk117ScheduleSteps`: имя, цвет, вопрос о дублировании и доступ, перечитанный при
 * открытии. Шаг не пересказывает продукт, а спрашивает у домена те же величины, что видит
 * человек: границу — у `ProfileCreationGate`, доступ — у `ObserveProStatusUseCase`
 * (`world.pro.status()`), причину — у каталога строк формы.
 *
 * **«Названа словами» проверяется по каталогу формы.** В чистом JVM-модуле нет ни разметки,
 * ни ресурсов Android, поэтому «слова есть» значит: ключ существует в
 * `feature-settings/res/values/strings.xml` и его значение непусто (П-7). Вёрстку и метку
 * на строке держит экранный прогон `@screen`.
 *
 * **Что доказывает какой уровень** (уровни дополняют друг друга, и ни один не судит сам себя):
 * — **сценарий** — правило домена и **продуктовые слова**: границу по числу заведённых, предел
 *   пяти, доступ из кэша, порядок причин и наличие строк каталога;
 * — **экранный стенд** (`feature-settings/.../Sk121ProfileScreenSteps`) — вёрстку: поле, ряд
 *   цветов, переключатель, причину над кнопкой, **приглушение** кнопки и метку «Про» на строке;
 * — **`ProfileFormViewModelTest`** — поведение ViewModel: первый свободный цвет, включённое
 *   дублирование, состояние кнопки по `block`, событие `showcase` вместо создания, перечитывание
 *   доступа по `refreshProAccess`;
 * — **поход** (`ProfilesJourneyTest`) — тот же путь на устройстве, включая точку-повод, которую
 *   выбирает `app`.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions", "LargeClass")
class Sk121ProfileSteps(private val world: AcceptanceWorld) {

    /** Имя в черновике мастера: пустая строка — «человек ещё не назвал профиль». */
    private var draftName: String = ""

    /** Цвет в черновике мастера; `null` — мастер не открывали. */
    private var draftColour: Int? = null

    /** Вопрос о дублировании в черновике мастера (по умолчанию включено, Р-29). */
    private var draftMirror: Boolean = false

    /** Доступ «Про», прочитанный формой при открытии (ПРФ-33, Р-84). `null` — мастер не открывали. */
    private var formAccess: Boolean? = null

    /** Чем форма заблокировала завершение в последний раз; `null` — не блокировала. */
    private var submitBlock: String? = null

    /** Сколько профилей было до действия: им проверяется «профиля не прибавилось». */
    private var profilesBeforeSubmit: Int? = null

    /** Имя владельца, поставленное строкой правки: им проверяется «владелец назван «Анна»». */
    private var renamedOwnerTo: String? = null

    /** Имя третьего профиля, поставленное строкой правки: им проверяется «новое имя сохранено». */
    private var renamedThirdTo: String? = null

    // --- Предыстория: состав профилей -------------------------------------------------------

    /**
     * Сколько профилей заведено на устройстве.
     *
     * Владельца «Я» уже завела Предыстория («активен профиль «Я»»); остальные —
     * чужие с именами вида «Подопечный N». Профиль появляется и в мире, и в хранилище:
     * `addForeign` кладёт его туда же, куда положат настоящие сценарии домена.
     */
    @Допустим("^на устройстве (один|два|три|пять) профил\\S*$")
    fun theDeviceHasThatManyProfiles(word: String) {
        val target = when (word) {
            "один" -> ONE
            "два" -> TWO
            "три" -> THREE
            else -> FIVE
        }
        while (world.profiles.all().size < target) {
            val index = world.profiles.all().size + 1
            world.profileManagement.addForeign("Подопечный $index", PALETTE[index % PALETTE.size])
        }
        assertEquals(target, world.profiles.all().size, "the premise asks for $target profiles")
    }

    /**
     * Владелец «Я» и подопечный с названным цветом из закрытой палитры (ПРФ-15).
     *
     * Цвет приходит словом сценария («Терракота», «Олива», «Плюм»…): в базу уходит ARGB,
     * а палитру знает интерфейс — здесь её пять значений, сверенных с `Palette.Profile*`.
     */
    @Допустим("^у профиля владельца имя «([^»]+)», а у подопечного — «([^»]+)» с цветом «([^»]+)»$")
    fun theOwnerAndTheWardAreNamed(ownerName: String, wardName: String, colour: String) {
        val owner = requireNotNull(world.profiles.self()) { "the scenario must open the owner profile first" }
        assertTrue(sameName(ownerName, owner.name), "the owner must be named «$ownerName», not «${owner.name}»")
        world.profileManagement.addForeign(wardName, colourOf(colour))
    }

    /**
     * Дублирование напоминаний у подопечного как данность Предыстории (ПРФ-5).
     *
     * Это не действие человека: строка описывает, с чем сценарий начинается. Действие
     * («человек выключает дублирование») идёт настоящим `SetMirrorRemindersUseCase`.
     */
    @Допустим("^у подопечного «([^»]+)» дублирование включено$")
    fun theWardMirrorsReminders(name: String) {
        // Строка называет и самого подопечного, и его состояние: сценарий, начинающийся
        // «с дублирования», вправе не повторять, кем заведён этот профиль. Профиль чужой
        // и получает первый свободный цвет набора — ровно так его завёл бы мастер (ПРФ-15).
        val ward = wardProfile(name) ?: world.profileManagement.addForeign(name, firstFreeColour())
        world.profiles.setMirrorReminders(ward.name, true)
    }

    // --- Действия: заведение, мастер, граница ----------------------------------------------

    /**
     * Заведение профиля настоящим `CreateProfileUseCase` (ПРФ-9).
     *
     * `mirrorReminders = true` — решение Р-29: присмотр без напоминаний себе смысла почти
     * не имеет, и спрашивают об этом в мастере, а не здесь.
     */
    @Когда("^человек заводит профиль «([^»]+)» с цветом «([^»]+)»$")
    fun thePersonCreatesAProfile(name: String, colour: String) {
        val outcome = runBlocking { world.profileManagement.create(name, colourOf(colour), true) }
        assertTrue(outcome is CreateProfileOutcome.Created, "the profile must be created, not $outcome")
    }

    /**
     * Человек открыл мастер добавления профиля (ПРФ-9, ОВ-3).
     *
     * Форма заполнена тем, что человек получил бы при открытии: имя пустое, цвет — первый
     * свободный из палитры, дублирование включено (Р-29), а доступ «Про» прочитан
     * у того же сценария, что у экрана (ПРФ-33). Завершать пока нечем — блок не поставлен.
     */
    @Когда("^человек открывает мастер добавления профиля$")
    fun thePersonOpensTheCreationMaster() {
        openMaster()
    }

    /** Имя и цвет выбраны в открытом мастере; дальше завершение решает граница по числу заведённых. */
    @Когда("^(?:человек )?заполняет имя профиля «([^»]+)» и цвет «([^»]+)»$")
    fun thePersonFillsTheNameAndTheColour(name: String, colour: String) {
        draftName = name
        draftColour = colourOf(colour)
    }

    /**
     * Завершение мастера с пустым именем: профиль не заводится, причина названа (ПРФ-9).
     *
     * Форма блокирует завершение, а не зовёт домен с пустым именем: `CreateProfileUseCase`
     * пустое имя тоже отвергнет, но человеку нужен не отказ сценария, а слова у поля.
     */
    @Когда("^человек завершает мастер с пустым именем$")
    fun thePersonFinishesWithAnEmptyName() {
        openMaster()
        submit()
    }

    /**
     * Нажатие завершения в мастере (ПРФ-32, ПРФ-33, Р-84).
     *
     * Сначала граница по числу заведённых, потом имя:
     *
     * - [ProfileCreationDecision.BlockedByLimit] — пять профилей: предел не продаётся,
     *   витрина не открывается вовсе (ПРФ-34);
     * - [ProfileCreationDecision.BlockedByPro] — два и больше без доступа: причина платная,
     *   нажатие ведёт в витрину из точки [ProPoint.PROFILES] (Р-67 п. 2);
     * - [ProfileCreationDecision.Allowed] и пустое имя — профиль не заводится, сказано «нужно имя»;
     * - [ProfileCreationDecision.Allowed] — профиль заводит настоящий `CreateProfileUseCase`.
     */
    @Когда("^человек нажимает завершение$")
    fun thePersonFinishesTheMaster() {
        submit()
    }

    /**
     * Покупка в уже открытой витрине: подтверждение магазина, доступ, возврат (SKL-122, Р-68).
     *
     * Сценарий говорит «подписка становится активной», то есть магазин подтверждает покупку,
     * а не отказывает и не молчит: исход покупки задаётся здесь явно — по умолчанию магазин
     * мира ничего не подтверждает. После возврата форма **перечитывает доступ** тем же
     * сценарием, что и продукт (`MainActivity`/`ProfileFormViewModel.refreshProAccess`):
     * иначе третий профиль остался бы закрытым при оплаченной подписке.
     *
     * Сам путь перечитывания — продуктовый, и здесь он только **повторяется**: у сценарного
     * прогона нет ни `MainActivity`, ни ViewModel. Доказывают его `ProfileFormViewModelTest`
     * (`a purchase after the showcase lets the same press create the profile` — перечитывание
     * и завершение тем же нажатием) и поход (`ProfilesJourneyTest`: возврат из витрины
     * застаёт форму открытой, причина уходит, создание завершает та же кнопка).
     */
    @Когда("^подписка «Склянка Про» становится активной до (.+)$")
    fun theSubscriptionBecomesActiveUntil(until: String) {
        world.pro.confirmsPurchase(ProProductId.MONTHLY, world.dateOf(until))
        world.pro.session.select(ProProductId.MONTHLY)
        runBlocking { world.pro.session.buy() }
        world.pro.session.dismiss()
        formAccess = world.pro.status().included
        assertTrue(formAccess == true, "the store confirmed the purchase, the access must be there")
    }

    /** Правка имени владельца и цвета подопечного — настоящими сценариями домена (ПРФ-27, Р-84 п. 5). */
    @Когда("^человек меняет имя владельца на «([^»]+)» и цвет «([^»]+)» на «([^»]+)»$")
    fun thePersonRenamesTheOwnerAndRecoloursTheWard(ownerName: String, wardName: String, colour: String) {
        val owner = requireNotNull(world.profiles.self()) { "the scenario must have the owner profile" }
        val renamed = runBlocking { world.profileManagement.rename(owner.id, ownerName) }
        assertTrue(renamed is RenameProfileOutcome.Applied, "the owner must be renamed, not $renamed")
        renamedOwnerTo = ownerName

        val ward = requireNotNull(wardProfile(wardName)) { "the scenario must have the ward profile" }
        val recoloured = runBlocking { world.profileManagement.colour(ward.id, colourOf(colour)) }
        assertTrue(recoloured is SetProfileColourOutcome.Applied, "the ward must be recoloured, not $recoloured")
    }

    /** Переименование третьего профиля сценария: правка заведённого лимитом не ограничена (Р-22, Р-28). */
    @Когда("^человек переименовывает третий профиль$")
    fun thePersonRenamesTheThirdProfile() {
        val third = world.profiles.all().getOrNull(THIRD_INDEX)
        requireNotNull(third) { "the scenario must have a third profile" }
        val outcome = runBlocking { world.profileManagement.rename(third.id, THIRD_NEW_NAME) }
        assertTrue(outcome is RenameProfileOutcome.Applied, "the third profile must be renamed, not $outcome")
        renamedThirdTo = THIRD_NEW_NAME
    }

    /**
     * Удаление третьего профиля настоящим `DeleteProfileUseCase` (ПРФ-28, ПРФ-31).
     *
     * Переключение активного на владельца — штатное поведение сценария (ПРФ-31), и здесь
     * оно не обходится: удаление заведённого сверх лимита лимитом не ограничено (Р-22).
     */
    @Когда("^человек удаляет третий профиль$")
    fun thePersonDeletesTheThirdProfile() {
        val third = world.profiles.all().getOrNull(THIRD_INDEX)
        requireNotNull(third) { "the scenario must have a third profile" }
        val outcome = runBlocking { world.profileManagement.delete(third.id) }
        assertTrue(outcome is DeleteProfileOutcome.Deleted, "the third profile must be deleted, not $outcome")
    }

    /** Дублирование выключается настоящим `SetMirrorRemindersUseCase` (ПРФ-5, НАП-12). */
    @Когда("^человек выключает дублирование в профиле «([^»]+)»$")
    fun thePersonTurnsOffMirroring(name: String) {
        val ward = requireNotNull(wardProfile(name)) { "the scenario must have the profile «$name»" }
        val outcome = runBlocking { world.profileManagement.mirror(ward.id, false) }
        assertTrue(outcome is MirrorRemindersOutcome.Applied, "the mirroring must be turned off, not $outcome")
    }

    /**
     * Загрузка файла переноса с тремя профилями — тем же путём, что в SKL-19 (ПЕР-19).
     *
     * Файл собирает `TransferFileBuilder`, применяет настоящий `ApplyTransferUseCase` в режиме
     * слияния: профили файла сопоставляются по нормализованному имени (ПЕР-19), и один
     * из трёх — новый. Перенос лимитом не ограничен (ПЕР-19), а вот **добавление** после
     * него закрыто, если заведённых стало больше двух.
     */
    @Когда("^человек загружает файл переноса с тремя профилями$")
    fun thePersonLoadsATransferFileWithThreeProfiles() {
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        world.profiles.all().firstOrNull { !it.isSelf }?.let { builder.profile(it.name) }
        builder.profile(FILE_THIRD)
        world.transfer.useMode(TransferMode.MERGE)
        world.transfer.prepare(builder.build(exportedAt = world.clock.now()))
        val outcome = world.transfer.apply()
        assertTrue(outcome is TransferOutcome.Applied, "the transfer must be applied, not $outcome")
    }

    // --- Проверки мастера и границы ---------------------------------------------------------

    /**
     * Завершение не приглушено: у платной границы кнопка доступна, причина названа словами
     * (П-7, §5.10, §5.13).
     *
     * **Сама недоступность кнопки — свойство вёрстки, и здесь она не проверяется**: у сценарного
     * прогона нет композиции, а `ProfileCreationGate` внутри шага доказывал бы сам себя. Признак
     * `Disabled` и нажимаемость кнопки доказывают экранный стенд (`Sk121ProfileScreenSteps`:
     * «кнопка завершения не приглушена» и «завершение недоступно») и `ProfileFormViewModelTest`
     * (состояние кнопки по `block`). Сценарий держит то, чего у них нет: что решение принято по
     * **числу заведённых** и что причина у границы — платная, а не предел.
     */
    @Тогда("^завершение не приглушено$")
    fun theFinishIsNotDisabled() {
        assertEquals(
            BLOCK_PRO,
            visibleReason(),
            "the reason at the paid boundary must be the paid one, not the limit (П-7)",
        )
        assertWords("profile_add_blocked_pro")
    }

    /**
     * Над кнопкой названа причина платной границы (П-7, Р-84 пп. 1–3).
     *
     * Причина видна до нажатия: слова берутся из каталога формы — `profile_add_blocked_pro`,
     * и ключ без слов словами не считается (П-7). Саму строку над кнопкой рисует вёрстка, и
     * её текст сверяет экранный стенд («над кнопкой названа причина платной границы»);
     * сценарный прогон в чистом JVM слов на экране не видит и держит каталог.
     */
    @Тогда("^над кнопкой названа причина платной границы$")
    fun thePaidReasonIsNamedAboveTheButton() {
        assertWords("profile_add_blocked_pro")
    }

    /**
     * Предел пяти закрывает завершение (ПРФ-32, ПРФ-34).
     *
     * Приглушение кнопки здесь не проверяется — это вёрстка (её держат экранный стенд и
     * `ProfileFormViewModelTest`); сценарий утверждает, что причина у кнопки — именно предел,
     * а не платная граница, и что слова предела есть в каталоге.
     */
    @Тогда("^завершение недоступно$")
    fun theFinishIsDisabled() {
        assertEquals(
            BLOCK_LIMIT,
            visibleReason(),
            "five profiles close the creation at any subscription (ПРФ-32)",
        )
        assertWords("profile_add_blocked_limit")
    }

    /**
     * Причина названа словами до нажатия: предел не продаётся, и человек видит, почему закрыто
     * (ПРФ-34, П-7).
     */
    @Тогда("^причина названа словами до нажатия$")
    fun theReasonIsNamedBeforeTheTap() {
        assertWords("profile_add_blocked_limit")
    }

    /** Нажатие при пределе пяти не ведёт в витрину: предел не продаётся (ПРФ-34). */
    @Тогда("^витрина не открывается$")
    fun theShowcaseDoesNotOpen() {
        assertFalse(world.pro.state.visible, "the limit of five must not lead to the showcase (ПРФ-34)")
    }

    /** Пустое имя не заводит профиль: человек остаётся с тем же составом (ПРФ-9). */
    @Тогда("^профиль не создан$")
    fun theProfileIsNotCreated() {
        val before = requireNotNull(profilesBeforeSubmit) { "the scenario must open the master first" }
        assertEquals(before, created(), "an empty name must not create a profile")
    }

    /** Мастер говорит словами, что нужно имя (П-7): блокирует завершение и называет причину. */
    @Тогда("^сказано, что нужно имя$")
    fun theNameIsRequired() {
        assertEquals(BLOCK_NAME, submitBlock, "finishing with an empty name must be blocked by the name")
        assertWords("profile_blocked_name")
    }

    /**
     * Мастер спрашивает о дублировании (ПРФ-9): вопрос есть, и он назван словами (П-7).
     *
     * Проверяется, что мастер открыт (`formAccess` прочитан при открытии) и что строка вопроса
     * — `profile_switch_mirror` — существует и непуста. Вёрстку переключателя держит экранный
     * прогон.
     */
    @Тогда("^мастер спрашивает, дублировать ли напоминания этому профилю$")
    fun theMasterAsksAboutMirroring() {
        assertNotNull(formAccess, "the scenario must open the creation master first")
        assertWords("profile_switch_mirror")
    }

    /** Вопрос объясняет, чей телефон зазвонит (П-7): у дублирования есть подсказка словами. */
    @Тогда("^объясняет, чей телефон зазвонит$")
    fun theQuestionExplainsWhosePhoneRings() {
        assertWords("profile_switch_mirror_hint")
    }

    /**
     * Дублирование включено по умолчанию (Р-29): таково предложение мастера.
     *
     * «Включено» — решение **продукта**, а не этого шага: значение по умолчанию ставит форма
     * (`ProfileFormViewModel` — `mirrorReminders = true`), и доказывают его `ProfileFormViewModelTest`
     * (`the add form starts empty with the first free colour and mirroring on`) и поход (мастер
     * показывает включённый переключатель). Сценарный прогон держит то, чего у них нет: слова
     * вопроса и подсказки в каталоге.
     */
    @Тогда("^дублирование включено по умолчанию$")
    fun theMirroringIsOnByDefault() {
        assertWords("profile_switch_mirror")
        assertWords("profile_switch_mirror_hint")
    }

    /** Витрина открылась на пункте-поводе «Больше профилей», и он стоит первым (Р-67 п. 2, Р-75). */
    @Тогда("^открывается витрина с пунктом «Больше профилей» первым$")
    fun theShowcaseOpensWithTheProfilesPointFirst() {
        assertTrue(world.pro.state.visible, "the paid boundary must open the showcase")
        assertEquals(ProPoint.PROFILES, world.pro.state.point, "the showcase must come from the profiles entry")
        assertEquals(
            ProBenefit.PROFILES,
            ProPoint.PROFILES.benefitOrder().first(),
            "the entry point must be the first benefit row (Р-75 п. 3)",
        )
    }

    /** Профиля не прибавилось: до оплаты третий профиль не заводится (ПРФ-33, Р-84 п. 3). */
    @Тогда("^профиля не прибавилось$")
    fun theProfileCountDidNotGrow() {
        val before = requireNotNull(profilesBeforeSubmit) { "the scenario must submit the master first" }
        assertEquals(before, created(), "a closed creation must not add a profile")
    }

    /** С активным доступом заведение разрешено: граница считает доступ, а не одно число (Р-84 п. 4). */
    @Тогда("^создание профиля доступно$")
    fun theCreationIsAvailable() {
        assertEquals(ProfileCreationDecision.Allowed, gate(), "the paid access must open the creation")
    }

    // --- Проверки правки заведённого --------------------------------------------------------

    /** Имя владельца и цвет подопечного сохранены: правка заведённого лимитом не ограничена (Р-22, Р-28). */
    @Тогда("^владелец назван «([^»]+)», а «([^»]+)» носит цвет «([^»]+)»$")
    fun theOwnerIsNamedAndTheWardWearsTheColour(ownerName: String, wardName: String, colour: String) {
        val owner = requireNotNull(world.profiles.self()) { "the scenario must have the owner profile" }
        assertTrue(sameName(ownerName, owner.name), "the owner must be named «$ownerName», not «${owner.name}»")
        renamedOwnerTo?.let { assertEquals(it, owner.name, "the owner must carry the new name") }

        val ward = requireNotNull(wardProfile(wardName)) { "the scenario must have the ward profile" }
        assertEquals(colourOf(colour), ward.color, "the ward must wear the chosen colour")
    }

    /**
     * Новое имя владельца попадает в отчёт врачу и в файл переноса (ПРФ-27, ПЕР-19).
     *
     * Отчёт собирает настоящий `BuildDoctorReportUseCase`: имя профиля — часть документа
     * (ПР-10), и оно обязано быть новым. «Файл переноса» здесь — снимок того же состава
     * профилей (`world.transfer.snapshot()`), из которого выгрузка пишется настоящим
     * `ExportDataUseCase`: в модуле приёмки нет кодека `core-data`, и второй разбор JSON
     * заводить запрещено — проверяется источник имени, а не провод (SKL-19, `TDD.md §2`).
     */
    @Тогда("^новое имя владельца попадает в отчёт врачу и в файл переноса$")
    fun theNewOwnerNameReachesTheReportAndTheTransfer() {
        val owner = requireNotNull(world.profiles.self()) { "the scenario must have the owner profile" }
        val report = world.history.buildReport(YearMonth.from(world.clock.today()))
        assertNotNull(report, "the doctor report must be built for the active profile")
        assertEquals(owner.name, requireNotNull(report).profileName, "the report must carry the new owner name")

        val snapshot = world.transfer.snapshot()
        assertTrue(
            snapshot.profiles.any { it.isSelf && it.name == owner.name },
            "the transfer snapshot must carry the owner under the new name: ${snapshot.profiles.map { it.name }}",
        )
    }

    /**
     * Напоминания подопечного больше не дублируются (ПРФ-5, ПРФ-6, НАП-12).
     *
     * Флаг снят тем же сценарием домена, что и в продукте, а перепланирование — его
     * неотъемлемая часть: `SetMirrorRemindersUseCase` зовёт `ProfileSetReplanner`
     * (`world.reminders.replanner`) немедленно. Счётчика будильников мир не даёт, поэтому
     * наблюдаемый след один — запись журнала `PROFILE_MIRROR_CHANGED`, оставленная тем же
     * вызовом.
     */
    @Тогда("^напоминания «([^»]+)» больше не дублируются$")
    fun theRemindersAreNoLongerMirrored(name: String) {
        val ward = requireNotNull(wardProfile(name)) { "the scenario must have the profile «$name»" }
        assertFalse(ward.mirrorReminders, "the mirroring must be off")
        assertTrue(
            world.log.events.contains(OpEvent.PROFILE_MIRROR_CHANGED),
            "the change must leave the journal record of the same call: ${world.log.events}",
        )
    }

    /**
     * У профиля владельца флага дублирования нет (ПРФ-26).
     *
     * Правило доменное, а не разметочное: `SetMirrorRemindersUseCase` отвечает `OwnProfile`
     * на попытку переключить флаг владельцу — у него такого флага не бывает, и строка
     * тумблера ему не показывается. Модуль приёмки не видит `feature-settings`, поэтому
     * источник истины здесь — сам сценарий домена, а не собранное состояние формы.
     */
    @Тогда("^в профиле владельца флага дублирования нет$")
    fun theOwnerHasNoMirroringFlag() {
        val owner = requireNotNull(world.profiles.self()) { "the scenario must have the owner profile" }
        val outcome = runBlocking { world.profileManagement.mirror(owner.id, true) }
        assertTrue(outcome is MirrorRemindersOutcome.OwnProfile, "the owner has no mirroring flag (ПРФ-26): $outcome")
    }

    /** Новое имя третьего профиля сохранено — правка заведённого прошла (Р-22, Р-28). */
    @Тогда("^новое имя сохранено$")
    fun theNewNameIsSaved() {
        val expected = requireNotNull(renamedThirdTo) { "the scenario must rename the third profile first" }
        val third = world.profiles.all().getOrNull(THIRD_INDEX)
        requireNotNull(third) { "the scenario must have a third profile" }
        assertEquals(expected, third.name, "the third profile must carry the new name")
    }

    /** Добавление закрыто: заведённых два и больше без активного доступа (ПРФ-33, Р-84 пп. 1–3). */
    @Тогда("^добавление профиля закрыто$")
    fun theCreationIsClosed() {
        assertTrue(gate() != ProfileCreationDecision.Allowed, "two profiles without access close the creation")
    }

    /** После удаления заведённого добавление остаётся закрытым: удаление лишнего границу не отменяет. */
    @Тогда("^добавление профиля по-прежнему закрыто$")
    fun theCreationIsStillClosed() {
        assertTrue(gate() != ProfileCreationDecision.Allowed, "the creation stays closed after a deletion")
    }

    /** Профилей снова два: удаление сверхлимитного прошло (ПРФ-32, ОВ-7). */
    @Тогда("^профилей снова два$")
    fun thereAreTwoProfilesAgain() {
        assertEquals(TWO, created(), "after deleting the third profile there must be two again")
    }

    // --- Проверки переноса ------------------------------------------------------------------

    /** Профили из файла приняты: загрузка прошла, и новый профиль есть и в мире, и в хранилище. */
    @Тогда("^профили из файла приняты$")
    fun theProfilesFromTheFileAreAccepted() {
        val third = world.profiles.all().firstOrNull { it.name == FILE_THIRD }
        assertNotNull(third, "the third profile must come from the file: ${world.profiles.all().map { it.name }}")
        assertNotNull(
            runBlocking { world.repositories.profileStore.get(requireNotNull(third).id) },
            "the imported profile must be visible to the storage too",
        )
    }

    /** Перенос принёс больше двух профилей, и добавление после него закрыто без доступа (ПЕР-19, ПРФ-33). */
    @Тогда("^добавление новых профилей закрыто, потому что их стало больше двух$")
    fun theCreationIsClosedBecauseThereAreMoreThanTwo() {
        assertTrue(created() > ProfileCreationGate.FREE, "the transfer must bring more than two profiles")
        assertEquals(
            ProfileCreationDecision.BlockedByPro,
            gate(),
            "more than two profiles without access close the creation (ПРФ-33)",
        )
    }

    /**
     * Профиль появился в списке профилей — и в мире, и в хранилище (ПРФ-9).
     *
     * Список профилей — единственная сущность, которую законно читать без фильтра (ПРФ-4):
     * заведённый профиль обязан быть виден и сценарию, и хранилищу, иначе проверка
     * «появляется в списке» смотрела бы на копию состава.
     */
    @Тогда("^профиль «([^»]+)» появляется в списке профилей$")
    fun theProfileAppearsInTheList(name: String) {
        val profile = world.profiles.all().firstOrNull { it.name == name }
        assertNotNull(profile, "«$name» must appear in the list: ${world.profiles.all().map { it.name }}")
        assertNotNull(
            runBlocking { world.repositories.profileStore.get(requireNotNull(profile).id) },
            "the profile must be visible to the storage too",
        )
    }

    /** Строка «Добавить профиль» не несёт метки «Про»: метка есть только у платного исхода (Р-71). */
    @Тогда("^строка «Добавить профиль» не несёт метки «Про»$")
    fun theAddProfileRowCarriesNoProMark() {
        assertTrue(
            gate() != ProfileCreationDecision.BlockedByPro,
            "the row carries the mark only when the creation is paid (Р-71)",
        )
        assertWords("profile_add_action")
    }

    // --- Обвязка ----------------------------------------------------------------------------

    /** Мастер открыт и заполнен тем, что человек получил бы при открытии (Р-29, ПРФ-33). */
    private fun openMaster() {
        draftName = ""
        draftColour = firstFreeColour()
        draftMirror = true
        formAccess = world.pro.status().included
        submitBlock = null
        profilesBeforeSubmit = created()
    }

    /** Нажатие завершения: граница и имя, затем либо профиль, либо причина. */
    private fun submit() {
        profilesBeforeSubmit = created()
        // Порядок тот же, что у продукта: предел → пустое имя → платная граница
        // (`ProfileFormUiState.block`, §5.13). Придел закрывает создание при любой подписке,
        // пустое имя называет себя, и только потом слово о плате.
        submitBlock = when {
            gate() == ProfileCreationDecision.BlockedByLimit -> BLOCK_LIMIT
            draftName.isBlank() -> BLOCK_NAME
            gate() == ProfileCreationDecision.BlockedByPro -> BLOCK_PRO
            else -> null
        }
        when (submitBlock) {
            BLOCK_LIMIT, BLOCK_NAME -> Unit
            // Точку-повод называет продукт: форму просит `app`, и он открывает витрину
            // с `ProPoint.PROFILES` (`SettingsSection`). В чистом JVM-модуле `app` нет,
            // поэтому вызов повторяет его здесь; что точку выбирает **продукт**, доказывают
            // поход (`ProfilesJourneyTest`: первая строка выгод — «Больше профилей») и
            // `ProfileFormViewModelTest` (нажатие испускает `showcase`, а не создаёт профиль).
            BLOCK_PRO -> runBlocking { world.pro.session.open(ProPoint.PROFILES) }
            else -> {
                val colour = requireNotNull(draftColour) { "the master has no colour chosen" }
                val outcome = runBlocking { world.profileManagement.create(draftName, colour, draftMirror) }
                assertTrue(outcome is CreateProfileOutcome.Created, "the profile must be created, not $outcome")
            }
        }
    }

    /** Граница создания по числу заведённых и активному доступу (ПРФ-32…ПРФ-34). */
    private fun gate(): ProfileCreationDecision =
        ProfileCreationGate.decide(created(), formAccess ?: world.pro.status().included)

    /** Видимая причина у кнопки: предел — приглушение, платная граница — названная причина (П-7). */
    private fun visibleReason(): String? = when (gate()) {
        ProfileCreationDecision.BlockedByLimit -> BLOCK_LIMIT
        ProfileCreationDecision.BlockedByPro -> BLOCK_PRO
        else -> null
    }

    private fun created(): Int = runBlocking { world.profileManagement.count() }

    /** Первый цвет палитры, которого нет ни у одного заведённого профиля. */
    private fun firstFreeColour(): Int {
        val used = world.profiles.all().mapTo(mutableSetOf()) { it.color }
        return PALETTE.firstOrNull { it !in used } ?: PALETTE.first()
    }

    /** Чужой профиль по имени сценария; владелец и незнакомое имя — отказ шага, а не молчание. */
    private fun wardProfile(name: String): Profile? {
        val resolved = RussianNames.resolve(name, world.profiles.all().map { it.name }) ?: name
        return world.profiles.all().firstOrNull { it.name == resolved && !it.isSelf }
    }

    /** Имя подопечного из текста шага в том же падеже, в каком его поставила фраза. */
    private fun wardName(name: String): String {
        val resolved = RussianNames.resolve(name, world.profiles.all().map { it.name })
        return requireNotNull(resolved) { "unknown profile: $name" }
    }

    /** Цвет закрытой палитры по слову сценария (ПРФ-15): в базу уходит ARGB. */
    private fun colourOf(word: String): Int = requireNotNull(COLOURS[word]) {
        "unknown profile colour: $word (known: ${COLOURS.keys})"
    }

    /** Одно ли это имя: падеж («Анна» — «Анной») не делает имя другим. */
    private fun sameName(spoken: String, stored: String): Boolean = spoken == stored ||
        RussianNames.resolve(spoken, listOf(stored)) == stored ||
        stem(spoken) == stem(stored)

    /** Основа имени: падежные окончания отбрасываются так же, как у имён лекарств (RussianNames). */
    private fun stem(name: String): String = name.trim().trimEnd { it in CASE_ENDINGS }

    /** Слова есть, если ключ существует и его значение непусто (П-7). */
    private fun assertWords(key: String) {
        val words = CATALOG[key]
        assertNotNull(words, "the form catalogue has no key $key")
        assertTrue(requireNotNull(words).isNotBlank(), "the key $key has no words")
    }

    private companion object {

        const val ONE = 1
        const val TWO = 2
        const val THREE = 3
        const val FIVE = 5

        /** Индекс третьего профиля: владелец первый, за ним — подопечные. */
        const val THIRD_INDEX = 2

        const val BLOCK_PRO = "pro"
        const val BLOCK_LIMIT = "limit"
        const val BLOCK_NAME = "name"

        const val THIRD_NEW_NAME = "Бабушка"
        const val FILE_SELF = "файловый свой"
        const val FILE_THIRD = "Файл третий"

        /** Пять цветов закрытой палитры в порядке выбора (`Palette.Profile*`), сверены с core-ui. */
        val PALETTE: List<Int> = ScenarioProfileManagement.PALETTE.toList()

        /**
         * Слова палитры из сценария: канон зовёт сливу «Плюм», а каталог форм — «Слива».
         * Оба слова — одно значение ARGB: сценарий остаётся дословным, а палитра — закрытой.
         */
        val COLOURS: Map<String, Int> = mapOf(
            "Терракота" to 0xFF8F3E23.toInt(),
            "Бирюза" to 0xFF1F5F6B.toInt(),
            "Олива" to 0xFF3C5014.toInt(),
            "Слива" to 0xFF6B4A6B.toInt(),
            "Плюм" to 0xFF6B4A6B.toInt(),
            "Индиго" to 0xFF4555B1.toInt(),
        )

        /** Падежные окончания имён сценария: основа сравнивается без них. */
        val CASE_ENDINGS: CharArray = "аяуюыиеоёйАЯУЮЫИЕОЁЙ".toCharArray()

        /** Каталог слов формы добавления профиля: тот же, что показывает человеку (§10 нормы дизайна). */
        val CATALOG: Map<String, String> by lazy {
            val file = File(RepositoryLayout.root, "feature-settings/src/main/res/values/strings.xml")
            Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""")
                .findAll(file.readText())
                .associate { match -> match.groupValues[1] to match.groupValues[2] }
        }
    }
}
