package ru.stohastika.sklyanka.startup

/**
 * Что показывается первым после диалога политики (НАП-19).
 *
 * Порядок первого запуска — часть требования, а не следствие ветвлений в `MainActivity`.
 * Правило вынесено сюда, чтобы его можно было проверить без активности и чтобы следующая
 * правка маршрута не переставила шаги молча.
 *
 * Диалог политики в правило не входит: он показывается **поверх** любого экрана и до него
 * (ПВ-1), поэтому вызывающий решает шаг уже с учётом того, что политика принята.
 */
internal enum class LaunchStep {
    /** Настройка надёжности (НАП-19). */
    ONBOARDING,

    /** Корень приложения: «Сегодня» и всё остальное. */
    ROOT,
}

/**
 * Выбрать первый экран запуска.
 *
 * @param onboardingDestination маршрут запуска говорит «с разрешениями непорядок».
 * @param onboardingUnseen человек ещё не видел экран разрешений.
 * @param onboardingDone экран разрешений уже пройден на этом запуске.
 * @param addMedicationRequested человек пришёл по ярлыку «Добавить лекарство»: явное действие
 *   сильнее самопоказа (SKL-80). Разрешения дождутся следующего обычного запуска.
 */
internal fun launchStep(
    onboardingDestination: Boolean,
    onboardingUnseen: Boolean,
    onboardingDone: Boolean,
    addMedicationRequested: Boolean,
): LaunchStep = when {
    onboardingDestination && onboardingUnseen && !onboardingDone && !addMedicationRequested ->
        LaunchStep.ONBOARDING
    else -> LaunchStep.ROOT
}
