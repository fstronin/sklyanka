package ru.heuri.sklyanka.detekt

import org.jetbrains.kotlin.psi.KtElement

/**
 * Область действия правила задаётся пакетами и именами файлов, а не путями:
 * путь зависит от того, откуда запущен detekt, пакет — нет.
 */
internal fun KtElement.inAllowedScope(
    allowedPackages: List<String>,
    allowedFiles: List<String>,
): Boolean {
    val file = containingKtFile
    val packageName = file.packageFqName.asString()
    val packageAllowed = allowedPackages.any { prefix ->
        prefix.isNotBlank() && (packageName == prefix || packageName.startsWith("$prefix."))
    }
    val fileAllowed = allowedFiles.any { name -> name.isNotBlank() && file.name == name }
    return packageAllowed || fileAllowed
}
