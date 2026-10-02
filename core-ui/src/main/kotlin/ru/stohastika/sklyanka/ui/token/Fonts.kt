package ru.stohastika.sklyanka.ui.token

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ru.stohastika.sklyanka.ui.R

/**
 * Шрифтовые семейства приложения.
 *
 * Файлы лежат в `res/font` и подключаются напрямую. Загружаемые шрифты Google Fonts
 * не используются: им нужна сеть и провайдер, а сеть у «Склянки» отсутствует не
 * временно, а по устройству продукта (НФТ-14, НФТ-15, ПЛ-3). Приложение обязано
 * набирать кириллицу в режиме полёта при первом же запуске.
 *
 * Начертания взяты ровно те, что встречаются в макетах: Golos Text 400/500/600/700
 * и Literata 600. Literata Regular не подключается — её в макетах нет ни разу, а
 * каждый лишний файл это вес установочного пакета и время холодного старта (НФТ-21).
 *
 * Файлы собраны из вариативных исходников Google Fonts (OFL): вариативная ось
 * зафиксирована по начертанию, набор символов подрезан до кириллицы целиком,
 * базовой латиницы, цифр и пунктуации. Кириллица не подрезается ни в одном
 * начертании (критерий 4). Тексты лицензий — в `assets/licenses`.
 */
val GolosText: FontFamily = FontFamily(
    Font(R.font.golos_text_regular, FontWeight.Normal),
    Font(R.font.golos_text_medium, FontWeight.Medium),
    Font(R.font.golos_text_semibold, FontWeight.SemiBold),
    Font(R.font.golos_text_bold, FontWeight.Bold),
)

/**
 * Заголовочное семейство.
 *
 * Правило пары шрифтов (§2.3): Literata — только заголовки, числовые показатели и
 * время на напоминании. Обратного смешения в макетах нет ни разу, и держится оно
 * тем, что экран не собирает `TextStyle` сам, а берёт готовый из [SklyankaType].
 */
val Literata: FontFamily = FontFamily(
    Font(R.font.literata_semibold, FontWeight.SemiBold),
)

/**
 * Literata для цифр тайм-пикера (ОВ-118/ОВ-119): те же глифы и начертание 600, но
 * вертикальные метрики сведены к строке 1,125 em — 72 dp при кегле 64 sp, с чернилами
 * цифры по центру строки (ascent 0,915 em).
 *
 * Причина отдельного файла — не каприз шрифта, а поведение `LineHeightStyle` Compose:
 * высота строки меньше натуральной не сдвигает чернила, а лишь добавляет компенсирующий
 * отступ, и цифры остаются прижатыми к низу (проверено снимком). Подложка слота 96×80 dp
 * центрирует строку 72 dp, поле ввода 96×72 dp совпадает с ней, и зазоры над и под
 * цифрой равны: 16,6 dp в слоте, 12,6 dp в поле. Размер цифр (`Display` 64 sp) и потолок
 * масштаба 100 % не меняются (ОВ-89/ОВ-116).
 */
val LiterataTime: FontFamily = FontFamily(
    Font(R.font.literata_time, FontWeight.SemiBold),
)
