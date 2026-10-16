package ru.stohastika.sklyanka.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.appearance.AppearancePreferences

/**
 * Состав изменений, за которыми следит плита (SKL-120, §5.11, Р-23).
 *
 * Плита читает акцентную тему в момент отрисовки, поэтому выбор новой темы обязан попадать
 * в поток изменений: без него приложение перекрасилось бы, а плита несла бы прежний акцент
 * до следующего события данных — до ближайшей отметки или правки лекарства.
 *
 * **Названный предел.** Проверка держит **состав** зависимостей источника (`AppearancePreferences`
 * среди них), но не сам факт испускания: для него нужны подставные хранилища профиля,
 * лекарств и приёмов, и это отдельное ремесло. Испускание проверяет поход: смена темы
 * на устройстве перерисовывает плиту.
 */
class WidgetSourcesAppearanceTest {

    @Test
    fun theChangesFollowTheAppearance() {
        val parameters = RepositoryWidgetSources::class.java.declaredConstructors.single().parameterTypes.toList()
        assertTrue(
            "the plate must be redrawn when the theme changes: $parameters",
            parameters.contains(AppearancePreferences::class.java),
        )
    }
}
