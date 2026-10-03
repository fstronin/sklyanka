package ru.stohastika.sklyanka.domain.experiment

/**
 * Каталог A/B-экспериментов (ПМ-20, ПМ-26; SKL-83 TDD §6).
 *
 * Закрытый список: эксперимент существует, только если назван здесь решением
 * продакт-менеджера. Поля каталога — только перечисления; ни строк, ни чисел, ни доменных
 * типов в нём нет конструктивно, поэтому эксперимент не может унести медицинские данные
 * (НФТ-16/НФТ-19). Новый эксперимент — это запись с допустимыми вариантами, строки
 * варианта и точка применения; адаптер и гейт не меняются: имя параметра в консоли AGC
 * выводится из имени записи (`ExperimentCodes`).
 */
enum class ExperimentId(val variants: Set<VariantKey>) {

    /** Текст пустого состояния «Сегодня»: формулировка первого действия. */
    TODAY_EMPTY_STATE(setOf(VariantKey.BASE, VariantKey.A)),

    /** Подпись строки «Перенос данных» в настройках. */
    SETTINGS_TRANSFER_HINT(setOf(VariantKey.BASE, VariantKey.A)),
    ;

    /** Базовый вариант — текущее поведение продукта; действует при любом сомнении. */
    val base: VariantKey get() = VariantKey.BASE

    init {
        require(base in variants) { "$name must keep the base variant" }
    }
}

/** Закрытый вариант эксперимента: значения каталога, а не строки вендора. */
enum class VariantKey { BASE, A, B, C }
