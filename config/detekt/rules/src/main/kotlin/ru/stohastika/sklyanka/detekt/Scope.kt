package ru.stohastika.sklyanka.detekt

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
    // detekt отдаёт в KtFile.name путь, а не голое имя файла, поэтому сравниваем
    // по последнему сегменту — иначе исключение по имени молча не срабатывает.
    val simpleName = file.name.substringAfterLast('/').substringAfterLast('\\')
    val fileAllowed = allowedFiles.any { name -> name.isNotBlank() && simpleName == name }
    return packageAllowed || fileAllowed
}
