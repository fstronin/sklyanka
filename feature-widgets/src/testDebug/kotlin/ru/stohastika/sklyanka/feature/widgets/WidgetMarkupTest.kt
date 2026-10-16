package ru.stohastika.sklyanka.feature.widgets

import androidx.glance.LocalContext
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.history.DayState
import ru.stohastika.sklyanka.domain.today.IntakePresentation
import ru.stohastika.sklyanka.domain.widget.WidgetDaySummary
import ru.stohastika.sklyanka.domain.widget.WidgetDose
import ru.stohastika.sklyanka.domain.widget.WidgetIntake
import ru.stohastika.sklyanka.domain.widget.WidgetProfileIndication
import ru.stohastika.sklyanka.feature.widgets.glance.DayPlate
import ru.stohastika.sklyanka.feature.widgets.glance.DayPlateState
import ru.stohastika.sklyanka.feature.widgets.glance.IntakeRows
import ru.stohastika.sklyanka.feature.widgets.glance.Plate
import ru.stohastika.sklyanka.feature.widgets.glance.PlateHeader
import ru.stohastika.sklyanka.feature.widgets.glance.PlateQuietLine
import ru.stohastika.sklyanka.feature.widgets.glance.WeekStrip
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetLook
import ru.stohastika.sklyanka.feature.widgets.receiver.MarkIntakeAction
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Разметка плит на JVM (SKL-118, `TDD.md §10.3`).
 *
 * Glance собирает RemoteViews, и собрать их в JVM можно стендом `glance-appwidget-testing`:
 * проверяются те части плиты, которые видит человек, — слова состояний, порядок строк,
 * кружки недели и индикация профиля. Кадры типов этот стенд не снимает: размер плиты
 * на RemoteViews задаёт хост, и снимок плиты целиком остаётся за устройством.
 */
@RunWith(AndroidJUnit4::class)
// Язык — часть условий: доза и слова состояний читаются из ресурсов (НФТ-43а).
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class WidgetMarkupTest {

    @Test
    fun theIntakeRowNamesTheThreeStatesWithWords() = runGlanceAppWidgetUnitTest {
        // Контекст даёт стенду ресурсы: доза, время и слова состояний собираются из них.
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable {
            WidgetLook.accent(null, AccentTheme.DEFAULT).let { accent ->
                Plate(profile = null, accent = accent, onClick = MARK) {
                    PlateHeader(subject = product(R.string.widget_type_today), profileLabel = null, accent = accent)
                    IntakeRows(
                        items = listOf(
                            intake("i-due", LocalTime.of(14, 0), IntakePresentation.DUE_NOW, "Magnesium B6"),
                            intake("i-ahead", LocalTime.of(21, 0), IntakePresentation.UPCOMING, "Loristan"),
                            intake("i-missed", LocalTime.of(8, 0), IntakePresentation.MISSED, "Euthyrox"),
                            intake("i-taken", LocalTime.of(9, 0), IntakePresentation.TAKEN, "Vitamin D3"),
                        ),
                        accent = accent,
                        resources = LocalContext.current.resources,
                        mark = MarkIntakeAction::action,
                    )
                }
            }
        }

        onNode(hasText(product(R.string.widget_now))).assertExists()
        onNode(hasText(product(UiR.string.status_missed))).assertExists()
        onNode(hasText("14:00")).assertExists()
        onNode(hasText("Magnesium B6")).assertExists()
        // Подпись дозы собирает общий форматтер продукта (НФТ-44): проверяется текст
        // из того же ресурса, что читает продукт, — вместе с неразрывным пробелом.
        onAllNodes(hasText(doseLine(), true)).assertCountEquals(3)
        // Принятый приём строки не занимает: его число называет счётчик (§5.11).
        onNode(hasText("Vitamin D3")).assertDoesNotExist()
    }

    @Test
    fun theWeekStripCarriesTheStatesOfTheNorm() = runGlanceAppWidgetUnitTest {
        // Контекст даёт стенду ресурсы: доза, время и слова состояний собираются из них.
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable {
            WeekStrip(
                days = listOf(
                    summary(LocalDate.of(2026, 10, 12), DayState.ALL_TAKEN),
                    summary(LocalDate.of(2026, 10, 13), DayState.AHEAD),
                ),
                resources = LocalContext.current.resources,
                zone = ZONE,
            )
        }

        onNode(hasContentDescription(product(R.string.widget_day_state_taken), true)).assertExists()
        onNode(hasContentDescription(product(R.string.widget_day_state_today), true)).assertExists()
    }

    @Test
    fun theForeignProfileCarriesNameColourAndNoProMark() = runGlanceAppWidgetUnitTest {
        // Контекст даёт стенду ресурсы: доза, время и слова состояний собираются из них.
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable {
            val profile = WidgetProfileIndication(name = "mother", color = FOREIGN_COLOUR)
            val accent = WidgetLook.accent(profile, AccentTheme.DEFAULT)
            Plate(profile = profile, accent = accent, onClick = MARK) {
                PlateHeader(
                    subject = product(R.string.widget_type_today),
                    profileLabel = profileLabelFor("mother"),
                    accent = accent,
                )
            }
        }

        onNode(hasText(profileLabelFor("mother"))).assertExists()
        // Метки «Про» на плите нет: виджет — уже открытая платная зона (§5.11).
        onNode(hasTextEqualTo("Про")).assertDoesNotExist()
        // Акцент плиты приходит ролью палитры профиля (ПРФ-15): у чужого профиля он
        // не совпадает с акцентом владельца — цвет в дереве узлов не виден, поэтому
        // проверяется его источник, а кадр плиты с цветом сверяет визуальная проверка.
        assertNotEquals(
            WidgetLook.accent(null, AccentTheme.DEFAULT),
            WidgetLook.accent(WidgetProfileIndication(name = "mother", color = FOREIGN_COLOUR), AccentTheme.DEFAULT),
        )
    }

    @Test
    fun theStaleDayPlateKeepsQuietInsteadOfYesterday() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        provideComposable {
            val accent = WidgetLook.accent(null, AccentTheme.DEFAULT)
            DayPlate(
                state = DayPlateState(
                    subject = product(R.string.widget_type_today),
                    profileLabel = null,
                    profile = null,
                    date = YESTERDAY,
                    today = TODAY,
                    items = listOf(
                        intake("i-yesterday", LocalTime.of(8, 0), IntakePresentation.UPCOMING, "Euthyrox"),
                    ),
                    taken = 0,
                    planned = 1,
                    showCounter = true,
                ),
                accent = accent,
                open = android.content.Intent(),
                quiet = { PlateQuietLine(product(R.string.widget_day_empty)) },
            )
        }

        // Вчерашний день как сегодняшний не рисуется (П-7): строк вчерашних суток нет.
        onNode(hasText("Euthyrox")).assertDoesNotExist()
        onNode(hasText("08:00")).assertDoesNotExist()
        onNode(hasText(product(R.string.widget_type_today))).assertExists()
    }

    @Test
    fun thePlateNeverCarriesTheWordsOfAProfileKind() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable {
            val profile = WidgetProfileIndication(name = "mother", color = FOREIGN_COLOUR)
            val accent = WidgetLook.accent(profile, AccentTheme.DEFAULT)
            Plate(profile = profile, accent = accent, onClick = MARK) {
                PlateHeader(
                    subject = product(R.string.widget_type_today),
                    profileLabel = profileLabelFor("mother"),
                    accent = accent,
                )
                IntakeRows(
                    items = listOf(intake("i-due", LocalTime.of(14, 0), IntakePresentation.DUE_NOW)),
                    accent = accent,
                    resources = LocalContext.current.resources,
                    mark = MarkIntakeAction::action,
                )
            }
        }

        onAllNodes(hasText(FOREIGN_WORD, true)).assertCountEquals(0)
        onAllNodes(hasText(WARD_WORD, true)).assertCountEquals(0)
    }

    @Test
    fun theOwnerPlateCarriesNoProfileLabel() = runGlanceAppWidgetUnitTest {
        // Контекст даёт стенду ресурсы: доза, время и слова состояний собираются из них.
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable {
            val accent = WidgetLook.accent(null, AccentTheme.DEFAULT)
            Plate(profile = null, accent = accent, onClick = MARK) {
                PlateHeader(subject = product(R.string.widget_type_today), profileLabel = null, accent = accent)
            }
        }

        onNode(hasText(product(R.string.widget_type_today))).assertExists()
        onNode(hasContentDescription("Профиль", true)).assertDoesNotExist()
    }

    /** Слово интерфейса из ресурса продукта: текста в коде тестов нет (НФТ-42). */
    private fun product(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>()
        .withInterfaceLocale(RUSSIAN)
        .getString(id)

    /** Подпись дозы — из того же ресурса, что читает продукт (НФТ-44). */
    private fun doseLine(): String = ApplicationProvider.getApplicationContext<android.content.Context>()
        .withInterfaceLocale(RUSSIAN)
        .resources
        .getQuantityString(UiR.plurals.dose_tablets, DOSE_UNITS, DOSE)

    private fun intake(
        id: String,
        time: LocalTime,
        presentation: IntakePresentation,
        name: String = "Magnesium B6",
    ): WidgetIntake = WidgetIntake(
        intakeId = id,
        time = time,
        medicationName = name,
        dose = WidgetDose(amount = BigDecimal("2"), unit = "tablet", strength = null, strengthUnit = null),
        presentation = presentation,
    )

    private fun summary(date: LocalDate, state: DayState): WidgetDaySummary =
        WidgetDaySummary(date = date, state = state, taken = 0, planned = 0)

    /** Надпись профиля — из ресурса продукта, а не литералом теста (§10 нормы). */
    private fun profileLabelFor(name: String): String =
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .withInterfaceLocale(RUSSIAN)
            .getString(UiR.string.profile_label, name)

    private companion object {
        /** Слова, которых на плите быть не должно (ПР-24): собраны escape-ами, ибо их нет в ресурсах. */
        const val FOREIGN_WORD = "\u0447\u0443\u0436\u043E\u0439"
        const val WARD_WORD = "\u043F\u043E\u0434\u043E\u043F\u0435\u0447\u043D"

        const val DOSE_UNITS = 2
        const val DOSE = "2"

        val TODAY: LocalDate = LocalDate.of(2026, 10, 13)
        val YESTERDAY: LocalDate = TODAY.minusDays(1)
        val RUSSIAN: java.util.Locale = java.util.Locale.forLanguageTag("ru")
        val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
        const val FOREIGN_COLOUR = 0x8F3E23
        val MARK: androidx.glance.action.Action = MarkIntakeAction.action("i-due")
    }
}
