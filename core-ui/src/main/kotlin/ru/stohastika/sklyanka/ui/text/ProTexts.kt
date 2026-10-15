package ru.stohastika.sklyanka.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.pro.ProNotice
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.locale.interfaceLocale
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Слова витрины «Склянки Про»: ключ домена → строка каталога (SKL-122, Р-70 п. 3).
 *
 * Сопоставление живёт данными, а не разметкой кода: таблица ниже читается глазами рядом
 * с таблицей `docs/norm/06-design.md` §10, и её полнота — предмет проверки
 * (`ProTextsTest` требует, чтобы у каждого ключа домена была строка). Пока сопоставление
 * было ветвлением `when`, оно оставалось исчерпывающим по компилятору, но перестало
 * помещаться в меру сложности, которую проект считает читаемой.
 *
 * Все `pro_*` строки живут в каталогах `core-ui`, потому что их читают два модуля —
 * витрина (`feature-pro`) и строка блока в настройках (`feature-settings`); дублировать
 * один и тот же текст по модулям значило бы однажды получить две разные формулировки
 * у одного состояния (П-7).
 */
@Composable
fun proText(notice: ProNotice, vararg args: Any): String = stringResource(id = PRO_STRINGS.getValue(notice), *args)

/**
 * Дата подписки словами: «13 ноября 2026» (НФТ-43а).
 *
 * Шаблон берётся из ресурса, локаль — из конфигурации контекста, а не из статической локали
 * процесса: язык интерфейса задаёт приложение, и при его смене форматтер обязан смениться
 * вместе с каталогом строк, а не после перезапуска (НФТ-43, НФТ-44).
 */
@Composable
fun proDate(date: LocalDate): String {
    val pattern = stringResource(R.string.pro_date_pattern)
    // Локаль — тем же помощником, что у остальных дат интерфейса: язык задаёт приложение,
    // и второй способ его спросить разошёлся бы с первым при смене языка (НФТ-43).
    val locale = interfaceLocale()
    val formatter = remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
    return formatter.format(date)
}

/** Ключ домена → ключ каталога: одна строка на каждое слово поверхности. */
private val PRO_STRINGS: Map<ProNotice, Int> = mapOf(
    ProNotice.SHEET_TITLE to R.string.pro_sheet_title,
    ProNotice.BENEFIT_SCHEDULES to R.string.pro_benefit_schedules_short,
    ProNotice.BENEFIT_SCHEDULES_EXAMPLE to R.string.pro_benefit_schedules_example,
    ProNotice.BENEFIT_WIDGETS to R.string.pro_benefit_widgets_short,
    ProNotice.BENEFIT_REPORT to R.string.pro_benefit_report_short,
    ProNotice.BENEFIT_ACCENT to R.string.pro_benefit_accent_short,
    ProNotice.BENEFIT_EXPORT to R.string.pro_benefit_export_short,
    ProNotice.BENEFIT_PROFILES to R.string.pro_benefit_profiles_short,
    ProNotice.PERIOD_MONTH to R.string.pro_period_month,
    ProNotice.PERIOD_YEAR to R.string.pro_period_year,
    ProNotice.PERIOD_MONTH_SHORT to R.string.pro_period_month_short,
    ProNotice.PERIOD_YEAR_SHORT to R.string.pro_period_year_short,
    ProNotice.YEAR_RIBBON to R.string.pro_year_ribbon,
    ProNotice.YEAR_CHEAPER to R.string.pro_year_cheaper,
    ProNotice.ACTION_BUY to R.string.pro_action_buy,
    ProNotice.ACTION_RESTORE to R.string.pro_action_restore,
    ProNotice.ACTION_MANAGE to R.string.pro_action_manage,
    ProNotice.ACTION_CANCEL to R.string.pro_action_cancel,
    ProNotice.TERMS_ROW to R.string.pro_terms_row,
    ProNotice.RENEW_AUTO to R.string.pro_renew_auto,
    ProNotice.RENEW_TERMS_SHORT to R.string.pro_renew_terms_short,
    ProNotice.RENEW_PRICE to R.string.pro_renew_price,
    ProNotice.RENEW_CHARGE to R.string.pro_renew_charge,
    ProNotice.RENEW_CANCEL to R.string.pro_renew_cancel,
    ProNotice.DISCLOSURE_MORE to R.string.pro_disclosure_more,
    ProNotice.STATE_ACTIVE_UNTIL to R.string.pro_state_active_until,
    ProNotice.STATE_INACTIVE to R.string.pro_state_inactive,
    ProNotice.STORE_UNAVAILABLE to R.string.pro_store_unavailable,
    ProNotice.MARK to R.string.pro_mark,
    ProNotice.PURCHASE_FAILED_NETWORK to R.string.pro_purchase_failed_network,
    ProNotice.PURCHASE_FAILED_UNAVAILABLE to R.string.pro_purchase_failed_unavailable,
    ProNotice.PURCHASE_FAILED_DECLINED to R.string.pro_purchase_failed_declined,
    ProNotice.PURCHASE_FAILED_PRODUCT to R.string.pro_purchase_failed_product,
    ProNotice.PURCHASE_FAILED_STORE to R.string.pro_purchase_failed_store,
    ProNotice.RESTORE_FAILED to R.string.pro_restore_failed,
    ProNotice.RESTORE_NOTHING_FOUND to R.string.pro_restore_nothing_found,
    ProNotice.PURCHASE_CONFIRMING to R.string.pro_purchase_confirming,
    ProNotice.RESTORE_NOT_ACTIVE to R.string.pro_restore_not_active,
    ProNotice.MANAGE_FAILED to R.string.pro_manage_failed,
)

/** Ключи, у которых есть слова: полнота проверяется тестом, а не обещанием. */
internal val proStringKeys: Set<ProNotice> get() = PRO_STRINGS.keys
