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
        /**
         * Размер упаковки, если задан (ОВ-45); `null` — размер не задан.
         *
         * Значение по умолчанию — чтобы построение панели без размера (старые данные,
         * тесты SKL-08) не менялось.
         */
        val packageSize: BigDecimal? = null,
    ) : StockPanel {

        /**
         * Число для подписи «хватит на N дней» или `null`, если подписи нет.
         *
         * Подпись — часть предупреждения, а не постоянная строка карточки: при остатке
         * на полтора месяца она только шумит. Текст рядом с полосой обязателен всегда,
         * когда полоса тревожная: цветом одним статус не передаётся (НФТ-33).
         */
        val captionDays: Int? get() = daysLeft.takeIf { warning }

        /**
         * Доля остатка от размера упаковки для полосы; `null` — полосы нет.
         *
         * Полосы нет в двух случаях: размер не задан и остаток больше упаковки. Доля
         * больше целого не существует, а «90 из 60» читалось бы как ошибка; запрещать
         * такой запас форме нельзя — две упаковки законны. Размер ≤ 0 (битая запись
         * из чужого файла) тоже даёт `null`: делить не на что.
         */
        val packageFraction: Float?
            get() {
                val size = packageSize ?: return null
                if (size <= BigDecimal.ZERO || unitsLeft > size) return null
                return unitsLeft.toFloat() / size.toFloat()
            }
    }

    companion object {

        fun of(medication: Medication): StockPanel {
            val stock = medication.stockLeft ?: return Hidden
            return Visible(
                unitsLeft = stock,
                daysLeft = (StockForecast.of(medication) as? StockForecast.Days)?.value,
                warning = StockWarning.of(medication) != null,
                packageSize = medication.packageSize,
            )
        }
    }
}
