package ru.heuri.sklyanka.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Разделение профилей на уровне **контракта**, а не выборки (ПРФ-4, ПР-10).
 *
 * Страж, а не проверка поведения. Поведение проверяет инструментальный тест на настоящей
 * базе; здесь утверждается то, что поведением не проверишь: выборки медицинских данных
 * без профиля в интерфейсе репозиториев **не существует**. Разница принципиальная — тест
 * поведения ловит ошибку в написанном запросе, а этот ловит запрос, который кто-то
 * добавит завтра.
 *
 * Цена ошибки та же, ради которой написана вся задача: один недосмотр в интерфейсе
 * выводит на экран чужие медицинские данные, а человек им верит, потому что экран
 * подписан именем его профиля.
 *
 * Список исключений — именно список, а не «эти просто так работают»: каждое исключение
 * названо и объяснено, а новый метод без профиля обязан либо получить профиль, либо
 * попасть в этот список осознанно.
 */
class ProfileIsolationTest {

    private val repositoryRoot: File = findRepositoryRoot()

    @Test
    fun everyReadOfIntakesAndMedicationsDeclaresAProfile() {
        val offenders = medicalReads()
            .filterNot { (name, _) -> name in CROSS_PROFILE_READS }
            .filterNot { (_, parameters) -> parameters.contains(PROFILE_PARAMETER) }
            .map { (name, _) -> name }

        assertTrue(
            "Every read of intakes, medications and history must be scoped to a profile (PROFILE-4, DR-10). " +
                "Either add profileId or name the exception in CROSS_PROFILE_READS: $offenders",
            offenders.isEmpty(),
        )
    }

    /**
     * Исключения не размножаются молча.
     *
     * Список, который можно пополнить, не заметив, — это не список исключений, а разрешение.
     * Поэтому проверяется и его состав: новое исключение обязано появиться в этом тесте
     * вместе с обоснованием.
     */
    @Test
    fun theCrossProfileReadsAreExactlyTheOnesNamedHere() {
        val present = medicalReads()
            .map { (name, _) -> name }
            .filter { it in CROSS_PROFILE_READS }
            .toSet()

        assertEquals(CROSS_PROFILE_READS, present)
    }

    /**
     * Страж, который ничего не нашёл, — это не пройденная проверка, а молчание.
     *
     * Разбор исходника регулярным выражением ломается тихо: одна правка форматирования
     * интерфейса, и список читаемых методов становится пустым, а оба теста выше остаются
     * зелёными навсегда.
     */
    @Test
    fun theScanActuallyFindsTheReadsItIsSupposedToGuard() {
        val found = medicalReads().map { (name, _) -> name }.toSet()

        assertTrue("the guard found no reads at all: the source scan is broken", found.isNotEmpty())
        assertTrue(
            "the guard must see the screen reads it exists for, found: $found",
            found.containsAll(EXPECTED_READS),
        )
    }

    /** Пары «интерфейс.метод → список параметров» для методов, возвращающих медицинские данные. */
    private fun medicalReads(): List<Pair<String, String>> = repositoryFiles().flatMap { file ->
        val source = file.readText()
        DECLARATION.findAll(source)
            .filter { match -> MEDICAL_RETURN.containsMatchIn(match.groupValues[RETURN_GROUP]) }
            .map { match -> "${file.nameWithoutExtension}.${match.groupValues[1]}" to match.groupValues[2] }
            .toList()
    }

    private fun repositoryFiles(): List<File> = repositoryRoot
        .resolve("core-domain/src/main/kotlin/$REPOSITORY_PACKAGE_PATH")
        .listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension == "kt" }
        .sortedBy { it.name }

    private fun findRepositoryRoot(): File {
        val startedAt = requireNotNull(System.getProperty("user.dir")) { "Working directory is unknown" }
        var candidate: File? = File(startedAt).absoluteFile
        while (candidate != null && !candidate.resolve("settings.gradle.kts").isFile) {
            candidate = candidate.parentFile
        }
        return requireNotNull(candidate) { "Repository root not found above $startedAt" }
    }

    private companion object {
        const val REPOSITORY_PACKAGE_PATH = "ru/heuri/sklyanka/domain/repository"
        const val PROFILE_PARAMETER = "profileId"
        const val RETURN_GROUP = 3

        /**
         * Чтения, которые профиля не принимают, и почему это законно.
         *
         * `IntakeRepository.get` и `MedicationRepository.get` адресуются по первичному
         * ключу: ключ уникален, и принадлежность проверяет слой выше, где активный профиль
         * известен. То же обоснование, что у стража DAO.
         *
         * `IntakeRepository.expiredPending` — проход по просроченным приёмам **всех**
         * профилей (ПР-1): автоматический перевод в «пропущено» идёт независимо от того,
         * какой экран открыт, иначе история чужого профиля замирала бы, пока в него
         * не зайдут.
         */
        val CROSS_PROFILE_READS = setOf(
            "IntakeRepository.get",
            "IntakeRepository.expiredPending",
            "MedicationRepository.get",
        )

        /**
         * Чтения, ради которых страж существует: приёмы дня и периода, лекарства профиля.
         *
         * Список нарочно короткий и состоит из того, что видно на экранах: если разбор
         * исходника перестанет их находить, проверка обязана упасть, а не промолчать.
         */
        val EXPECTED_READS = setOf(
            "IntakeRepository.observeDay",
            "IntakeRepository.observePeriod",
            "MedicationRepository.observeActive",
            "MedicationRepository.observeArchived",
        )

        /** Объявление функции интерфейса: имя, параметры, тип результата до конца строки. */
        val DECLARATION = Regex("""fun\s+(\w+)\s*\(([^)]*)\)\s*:\s*([^\n]+)""", RegexOption.DOT_MATCHES_ALL)

        /** Возвращает медицинские данные — приёмы, лекарства или срез истории. */
        val MEDICAL_RETURN = Regex("""\b(Intake|Medication|History\w*)\b""")
    }
}
