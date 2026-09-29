package ru.stohastika.sklyanka.domain.stock

import ru.stohastika.sklyanka.domain.model.Medication
import java.math.BigDecimal

/**
 * Блок «Остаток» в карточке лекарства — то, что экран показывает, без единого текста.
 *
 * Тексты и склонения живут в ресурсах (НФТ-42, НФТ-44), вёрстка — в SKL-10; здесь
 * задаются только числа и признаки, по которым блок собирается. Это и есть граница
 * задачи: SKL-12 отвечает за то, что показывать, SKL-08 и SKL-10 — за то, как.
 */
sealed interface StockPanel {

    /** Учёт остатка не ведётся: блока в карточке нет вовсе, а не «есть, но пустой». */
    data object Hidden : StockPanel

    data class Visible(
        val unitsLeft: BigDecimal,
        /** Прогноз запаса; `null` — суточный расход неизвестен (приём по потребности, ОВ-39). */
        val daysLeft: Int?,
        /** Порог сработал: полоса и подпись показываются в тревожном виде (ПР-13). */
        val warning: Boolean,
    ) : StockPanel {

        /**
         * Число для подписи «хватит на N дней» или `null`, если подписи нет.
         *
         * Подпись — часть предупреждения, а не постоянная строка карточки: при остатке
         * на полтора месяца она только шумит. Текст рядом с полосой обязателен всегда,
         * когда полоса тревожная: цветом одним статус не передаётся (НФТ-33).
         */
        val captionDays: Int? get() = daysLeft.takeIf { warning }
    }

    companion object {

        fun of(medication: Medication): StockPanel {
            val stock = medication.stockLeft ?: return Hidden
            return Visible(
                unitsLeft = stock,
                daysLeft = (StockForecast.of(medication) as? StockForecast.Days)?.value,
                warning = StockWarning.of(medication) != null,
            )
        }
    }
}
