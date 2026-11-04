package ru.stohastika.sklyanka.gradle

import org.gradle.api.Project
import java.io.File
import java.util.Properties

/**
 * Секреты релизной подписи: путь к хранилищу и пароли.
 *
 * Источник — файл keystore.properties вне контроля версий либо переменные окружения.
 * Когда секретов нет, релизный артефакт остаётся неподписанным: отладочный ключ вместо
 * релизного не подставляется, иначе сборка выглядела бы релизной, но не встала бы
 * поверх опубликованной.
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
