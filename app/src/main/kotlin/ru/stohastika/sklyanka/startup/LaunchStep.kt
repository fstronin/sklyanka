package ru.stohastika.sklyanka.startup

/**
 * Что показывается первым после диалога политики (SKL-165, АН-1, ПМ-25).
 *
 * Порядок первого запуска — часть требования, а не следствие ветвлений в `MainActivity`:
 * «политика → аналитика → надёжность». Правило вынесено сюда, чтобы его можно было
 * проверить без активности и чтобы следующая правка маршрута не переставила шаги молча.
 *
 * Диалог политики в правило не входит: он показывается **поверх** любого экрана и до него
 * (ПВ-1), поэтому вызывающий передаёт [analyticsConsentUnseen] уже с учётом того, что
 * политика принята.
 */
internal enum class LaunchStep {
    /** Вопрос об аналитике — до онбординга разрешений (АН-1). */
    ANALYTICS_CONSENT,

    /** Настройка надёжности (НАП-19). */
    ONBOARDING,

    /** Корень приложения: «Сегодня» и всё остальное. */
    ROOT,
}

/**
 * Выбрать первый экран запуска.
 *
 * @param analyticsConsentUnseen решение об аналитике ещё не принято и политика уже принята.
 * @param onboardingDestination маршрут запуска говорит «с разрешениями непорядок».
 * @param addMedicationRequested человек пришёл по ярлыку «Добавить лекарство»: явное действие
 *   сильнее самопоказа и вопроса об аналитике (ПМ-25 п. 5, SKL-80). Вопрос дождётся
 *   следующего обычного запуска.
 */
internal fun launchStep(
    analyticsConsentUnseen: Boolean,
    onboardingDestination: Boolean,
    onboardingUnseen: Boolean,
    onboardingDone: Boolean,
    addMedicationRequested: Boolean,
): LaunchStep = when {
    analyticsConsentUnseen && !addMedicationRequested -> LaunchStep.ANALYTICS_CONSENT
    onboardingDestination && onboardingUnseen && !onboardingDone && !addMedicationRequested ->
        LaunchStep.ONBOARDING
    else -> LaunchStep.ROOT
}
