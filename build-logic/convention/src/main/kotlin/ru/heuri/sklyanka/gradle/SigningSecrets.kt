package ru.heuri.sklyanka.gradle

import org.gradle.api.Project
import java.io.File
import java.util.Properties

/**
 * Секреты релизной подписи: путь к хранилищу и пароли.
 *
 * Источник — файл keystore.properties вне контроля версий либо переменные окружения.
 * Молчаливый откат на отладочный ключ запрещён: он даёт сборку, которая выглядит
 * релизной и не работает с часами — Wear Engine привязан к отпечатку подписи (ЧАС-9).
 */
internal data class SigningSecrets(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
) {
    companion object {
        const val PROPERTIES_FILE = "keystore.properties"

        private const val ENV_STORE_FILE = "SKLYANKA_KEYSTORE_FILE"
        private const val ENV_STORE_PASSWORD = "SKLYANKA_KEYSTORE_PASSWORD"
        private const val ENV_KEY_ALIAS = "SKLYANKA_KEY_ALIAS"
        private const val ENV_KEY_PASSWORD = "SKLYANKA_KEY_PASSWORD"

        /** Причина, по которой секреты не найдены, — для внятного сообщения об отказе. */
        fun describeMissing(project: Project): String {
            val file = project.rootProject.file(PROPERTIES_FILE)
            return buildString {
                appendLine("Release signing secrets are missing, so the release build is refused.")
                appendLine("A silent fallback to the debug key is not an option: Wear Engine is")
                appendLine("bound to the release signature fingerprint (WATCH-9, PLATFORM-5).")
                appendLine()
                appendLine("Provide either:")
                appendLine("  1) ${file.absolutePath} with the keys")
                appendLine("     storeFile, storePassword, keyAlias, keyPassword")
                appendLine("     (see keystore.properties.example), or")
                appendLine("  2) the environment variables")
                appendLine("     $ENV_STORE_FILE, $ENV_STORE_PASSWORD, $ENV_KEY_ALIAS, $ENV_KEY_PASSWORD.")
                appendLine()
                appendLine("The keystore itself is created by SKL-01 and never committed.")
                val problems = collectProblems(project)
                if (problems.isNotEmpty()) {
                    appendLine("What is wrong right now:")
                    problems.forEach { appendLine("  - $it") }
                }
            }
        }

        fun resolve(project: Project): SigningSecrets? {
            val values = readValues(project) ?: return null
            val storeFile = project.rootProject.file(values.getValue("storeFile"))
            if (!storeFile.isFile) return null
            return SigningSecrets(
                storeFile = storeFile,
                storePassword = values.getValue("storePassword"),
                keyAlias = values.getValue("keyAlias"),
                keyPassword = values.getValue("keyPassword"),
            )
        }

        private fun collectProblems(project: Project): List<String> {
            val problems = mutableListOf<String>()
            val file = project.rootProject.file(PROPERTIES_FILE)
            if (!file.isFile) {
                problems += "${file.name} not found in the repository root"
            }
            val values = readValues(project)
            if (values == null) {
                problems += "no complete set of storeFile/storePassword/keyAlias/keyPassword found"
            } else {
                val storeFile = project.rootProject.file(values.getValue("storeFile"))
                if (!storeFile.isFile) {
                    problems += "keystore file '${storeFile.absolutePath}' does not exist"
                }
            }
            return problems
        }

        private fun readValues(project: Project): Map<String, String>? {
            val fromEnv = mapOf(
                "storeFile" to System.getenv(ENV_STORE_FILE),
                "storePassword" to System.getenv(ENV_STORE_PASSWORD),
                "keyAlias" to System.getenv(ENV_KEY_ALIAS),
                "keyPassword" to System.getenv(ENV_KEY_PASSWORD),
            )
            if (fromEnv.values.all { !it.isNullOrBlank() }) {
                return fromEnv.mapValues { (_, value) -> requireNotNull(value) }
            }

            val file = project.rootProject.file(PROPERTIES_FILE)
            if (!file.isFile) return null
            val properties = Properties().apply { file.inputStream().use { load(it) } }
            val fromFile = REQUIRED_KEYS.associateWith { key -> properties.getProperty(key) }
            if (fromFile.values.any { it.isNullOrBlank() }) return null
            return fromFile.mapValues { (_, value) -> requireNotNull(value) }
        }

        private val REQUIRED_KEYS = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    }
}
