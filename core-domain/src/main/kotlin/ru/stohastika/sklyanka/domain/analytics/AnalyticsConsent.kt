package ru.stohastika.sklyanka.domain.analytics

/**
 * Решение человека по аналитике и версия предмета согласия (ПМ-27).
 *
 * Согласие — одно на установку, живёт локально и файлом переноса не переносится.
 * Версия сравнивается с текущей [AnalyticsConsentVersion.MATERIAL]: после существенного
 * изменения предмета согласия решение перестаёт действовать, пока человек не примет
 * новое, — и до него события не отправляются.
 */
data class AnalyticsConsent(val decision: AnalyticsConsentDecision, val materialVersion: Int) {

    /**
     * Можно ли отправлять события при текущей версии предмета согласия.
     *
     * Сохранённая версия, отличная от текущей (в том числе большая — откат релиза),
     * трактуется как «решение устарело»: безопасное умолчание — молчать.
     */
    fun sendingAllowed(currentMaterialVersion: Int): Boolean =
        decision == AnalyticsConsentDecision.ACCEPTED && materialVersion == currentMaterialVersion

    /**
     * Нужно ли спросить решение заново при текущей версии предмета согласия (ПМ-27).
     *
     * «Не спрошено», а также решение любой давности, кроме текущей, — повод показать
     * экран: после существенного изменения текста старое решение не действует, пока
     * человек не примет новое. Обычная редактура формулировок версию не меняет
     * и повторного показа не вызывает.
     */
    fun needsDecision(currentMaterialVersion: Int): Boolean =
        decision == AnalyticsConsentDecision.NOT_ASKED || materialVersion != currentMaterialVersion

    /**
     * Каким источником назвать показ экрана, если решение нужно заново (ПМ-27).
     *
     * «Не спрошено» — первый запуск (или другой вход по решению хозяина экрана);
     * решение при устаревшей версии предмета — повторное информирование, и событие
     * показа обязано называть именно его (проверка «доля отказа» иначе смешала бы
     * первый запуск и смену политики). `null` — решение текущее, показывать нечего.
     */
    fun pendingSource(currentMaterialVersion: Int): ConsentSource? = when {
        decision == AnalyticsConsentDecision.NOT_ASKED -> ConsentSource.FIRST_LAUNCH
        materialVersion != currentMaterialVersion -> ConsentSource.POLICY_UPDATE
        else -> null
    }

    companion object {
        /** Состояние по умолчанию, в том числе после обновления приложения (ОВ-123). */
        val NOT_ASKED = AnalyticsConsent(AnalyticsConsentDecision.NOT_ASKED, 0)
    }
}

/** Состояние решения по согласию на аналитику. */
enum class AnalyticsConsentDecision {
    NOT_ASKED,
    ACCEPTED,
    DECLINED,
}
