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
