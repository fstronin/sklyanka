package ru.stohastika.sklyanka.ui.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * Языки, на которых разговаривает приложение (НФТ-43, НФТ-43а, `ADR-0008`, ПМ-14).
 *
 * Язык интерфейса принадлежит приложению, а не телефону. Это не оформительское решение:
 * Android выбирает форму множественного числа по локали **конфигурации**, то есть по языку
 * устройства, а не по языку, на котором написана строка. Приложение с русскими строками на
 * телефоне с английской системой получает русские слова с английскими правилами —
 * «15 минуты» и «0 лекарства». Человек видит в этом не опечатку, а признак неисправности.
 *
 * Объявление одно на всё приложение: разложенное по местам, оно разойдётся на первой же
 * правке, и разойдётся молча — на устройстве разработчика с русской системой разницы
 * не видно вовсе.
 */
object InterfaceLocale {

    /**
     * Запасной язык: на нём говорит приложение, если язык телефона не объявлен (ПМ-14).
     *
     * Русский остаётся базовым набором ресурсов (`values/`), поэтому незнакомый язык
     * получает его целиком — вместе со склонениями и словами внутри дат.
     */
    val FALLBACK: Locale = Locale.forLanguageTag("ru")

    /** Прежнее имя запасного языка: им пользуются проверки SKL-28/45 (НФТ-43а). */
    val VALUE: Locale = FALLBACK

    /**
     * Объявленные языки интерфейса (ПМ-14, ПМ-23).
     *
     * Порядок — от запасного к остальным; поиск идёт по языку, а не по стране, поэтому
     * `en-GB` находит `en-US`, а `zh-TW` — упрощённый `zh-CN`: язык интерфейса один,
     * и диалектного выбора у человека нет.
     */
    val SUPPORTED: List<Locale> = listOf(
        FALLBACK,
        Locale.forLanguageTag("en-US"),
        Locale.forLanguageTag("es"),
        Locale.forLanguageTag("de"),
        Locale.forLanguageTag("zh-CN"),
        Locale.forLanguageTag("kk"),
    )

    /**
     * Язык интерфейса для набора локалей устройства.
     *
     * Первый язык устройства, у которого есть объявленный «родственник», побеждает —
     * порядок предпочтений человека сохраняется. Если объявленного языка в списке нет,
     * выбирается [FALLBACK], и именно целиком: иначе ресурсы упали бы в русский
     * `values/`, а правила склонения система взяла бы чужие — дефект SKL-27.
     */
    fun resolve(deviceLocales: LocaleList): Locale {
        for (index in 0 until deviceLocales.size()) {
            val supported = supportedFor(deviceLocales[index])
            if (supported != null) return supported
        }
        return FALLBACK
    }

    private fun supportedFor(device: Locale): Locale? =
        SUPPORTED.firstOrNull { supported -> supported.language == device.language }
}

/**
 * Контекст, чья конфигурация говорит на языке интерфейса.
 *
 * Подменяется именно конфигурация, а не локаль процесса: от конфигурации зависят сразу три
 * вещи — выбор формы множественного числа, набор строковых ресурсов и локаль, которую
 * читают форматтеры дат экранов. Локаль процесса (`Locale.setDefault`) не даёт первого
 * и заодно навязала бы русский язык слою хранения, которому нужна нейтральная локаль:
 * время в базе — машинное значение, и от языка телефона оно зависеть не имеет права.
 *
 * Вызывается в `attachBaseContext` приложения и каждой активности — до того, как кто-либо
 * успел спросить у контекста строку.
 */
fun Context.withInterfaceLocale(): Context =
    withInterfaceLocale(InterfaceLocale.resolve(resources.configuration.locales))

/** Тот же контекст с явно названным языком: точка входа проверок и явных вызовов. */
fun Context.withInterfaceLocale(locale: Locale): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocales(LocaleList(locale))
    return createConfigurationContext(configuration)
}

/** Язык, которым живёт этот контекст: у закреплённого — объявленный, а не язык телефона. */
val Context.interfaceLocale: Locale
    get() = resources.configuration.locales[0]

/** Язык интерфейса внутри композиции: тот же, что у закреплённого контекста экрана. */
@Composable
fun interfaceLocale(): Locale = LocalConfiguration.current.locales[0]
