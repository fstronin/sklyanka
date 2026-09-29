package ru.stohastika.sklyanka.domain.model

/** Связь приёма с едой (docs/spec/02-data-model.md §5). На расчёт времени не влияет. */
enum class FoodRelation {
    ANY,
    BEFORE_MEAL,
    WITH_MEAL,
    AFTER_MEAL,
}
