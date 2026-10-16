package ru.stohastika.sklyanka.ui.token

import androidx.compose.ui.graphics.Color

/**
 * Тонирование поверхностей чужого профиля.
 *
 * В макете `MainProfile.dc.html` восемь оттенков, которых нет в §9: подкрашенные
 * фон, шапка, три линии, рамка. `docs/norm/06-design.md` §1.2 прямо требует
 * вычислять их функцией от цвета профиля, а не заводить константами: иначе на
 * каждый цвет палитры пришлось бы завести по восемь констант, и добавление пятого
 * цвета рассогласовало бы набор.
 *
 * Почему файл лежит в пакете токенов, а не в `theme`, как говорит TDD §3.
 * Результат тонирования — это значение токена, и получается он вызовом `Color(…)`.
 * Вызов `Color(…)` вне пакета токенов валит сборку (НФТ-47), и ослаблять это
 * правило ради одного файла нельзя. Тема остаётся тонкой: она вызывает
 * [tintedColors] и ничего не вычисляет сама.
 *
 * Доли подобраны обратным счётом от макета при цвете профиля `#8F3E23` и палитрой Р-20
 * не менялись: они выражают, сколько цвета профиля подмешано, а не абсолютный оттенок.
 * Макет `MainProfile.dc.html` нарисован в прежней палитре, поэтому эталоном теста
 * §11.1 служат значения, посчитанные от текущих базовых токенов; допуск по каналу остался.
 */
/** Мягкий акцент: подложка выбранного чипа, плашка иконки, фон баннера. */
internal const val TINT_SOFT = 0.14f

/** Рамка элемента, требующего действия. */
internal const val TINT_BORDER = 0.20f

/** Подкрашенный фон экрана: `#F4F0E8` от `#F8F6EF`. */
internal const val TINT_BACKGROUND = 0.035f

/** Подкрашенная шапка: `#EBE0D7` от `#F8F6EF`. */
internal const val TINT_HEADER = 0.12f

/** Линии на подкрашенном фоне: `#E0D3C6` от `#E6DED2`. */
internal const val TINT_LINE = 0.07f

/**
 * Название лекарства в архивной карточке: `#4A4540` от `#241F1B` на архивном фоне.
 *
 * `docs/norm/06-design.md` §1.2 помечает этот цвет производным — «приглушённый
 * `Text` для архива: архив намеренно тише активного списка». Производный цвет вычисляется,
 * а не заводится константой, иначе он разойдётся с `Text` при первой же правке токена.
 */
internal const val TINT_ARCHIVE_TEXT = 0.18f

/**
 * Смешивание в пространстве sRGB, покомпонентно.
 *
 * Именно так считались оттенки макета, и только так они воспроизводятся: смешивание
 * в линейном пространстве или в Oklab (как это делает `Color.lerp` из Compose) даёт
 * при тех же долях расхождение до пятнадцати пунктов на канал — подкраска перестаёт
 * читаться как та же самая.
 */
internal fun tint(base: Color, profile: Color, ratio: Float): Color = Color(
    red = base.red + (profile.red - base.red) * ratio,
    green = base.green + (profile.green - base.green) * ratio,
    blue = base.blue + (profile.blue - base.blue) * ratio,
    alpha = base.alpha,
)

/**
 * Светлый набор, пересчитанный под цвет чужого профиля.
 *
 * Что намеренно **не** тонируется: `taken` и `takenSoft` — семантика «принято» не
 * зависит от того, чей профиль; `text` и `textMuted` — контраст важнее оттенка;
 * `archiveBackground` — архив в контексте профиля не подкрашивается.
 */
internal fun tintedColors(base: SklyankaColors, profile: Color, dark: Boolean): SklyankaColors {
    // Среда уходит из подложки и чернил, но не из акцента (§2.2, формула семьи):
    // 1. подложка семьи — «бумага»: цвет профиля, разбавленный к белой поверхности 0,14,
    //    одно значение в обеих средах — как у подложки темы, которая тоже светлая всегда;
    // 2. чернила на ней — цвет профиля, затемнённый к основному тексту 0,50, тоже одно значение;
    // 3. рамка лежит на поверхности **среды**, поэтому разбавляется к ней 0,20 — светлая рамка
    //    вокруг тёмной карточки читалась бы браком;
    // 4. сам акцент (и полоса профиля) в тёмной среде поднимается к светлому значению 0,55 —
    //    к тому же #F8F6EF, каким тёмная среда набирает текст (§9, «фон и текст меняются
    //    местами»): цвет профиля на тёмном фоне даёт 2,4–3,2 и ролью текста быть не может.
    val raised = tint(Palette.Background, profile, PROFILE_RAISED_RATIO)
    return base.copy(
        accent = if (dark) raised else profile,
        accentSoft = tint(Palette.Surface, profile, PROFILE_SOFT_RATIO),
        onAccentSoft = onAccentSoftOf(profile),
        accentBorder = tint(base.surface, profile, PROFILE_BORDER_RATIO),
        background = tint(base.background, profile, TINT_BACKGROUND),
        profileBackground = tint(base.background, profile, TINT_BACKGROUND),
        profileHeader = tint(base.background, profile, TINT_HEADER),
        line = tint(base.line, profile, TINT_LINE),
        lineOnSurface = tint(base.lineOnSurface, profile, TINT_LINE),
        lineInteractive = tint(base.lineInteractive, profile, TINT_LINE),
        profileStripe = if (dark) raised else profile,
    )
}

/** Доля подложки чужого профиля: цвет профиля, разбавленный к белой поверхности (§2.2). */
private const val PROFILE_SOFT_RATIO = 0.14f

/** Доля рамки чужого профиля: к поверхности **среды** — рамка лежит на ней (§2.2). */
private const val PROFILE_BORDER_RATIO = 0.20f

/** Доля поднятия акцента и полосы чужого профиля в тёмной среде (§2.2, §9). */
private const val PROFILE_RAISED_RATIO = 0.55f

/** Доля затемнения нажатого элемента. Подобрана так, чтобы нажатие было видно, но не мигало. */
private const val PRESSED_RATIO = 0.16f

/**
 * Цвет нажатого элемента.
 *
 * Нажатие — состояние каталога компонентов (§4.3), а не только рябь Material:
 * состояние, которого нет в каталоге, не существует. Вычисление живёт в пакете
 * токенов по той же причине, что и [tint]: это получение значения цвета.
 */
fun pressedTint(color: Color): Color = tint(color, Palette.Text, PRESSED_RATIO)
