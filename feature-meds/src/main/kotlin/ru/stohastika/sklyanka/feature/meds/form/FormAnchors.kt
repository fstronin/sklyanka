package ru.stohastika.sklyanka.feature.meds.form

import androidx.compose.runtime.Stable
import ru.stohastika.sklyanka.domain.medication.DraftField

/**
 * Где на форме находится блок каждого поля.
 *
 * Нужно ради одного: при отказе экран обязан прокрутиться к первому нарушению.
 * Форма высотой в полтора экрана телефона, и сообщение об ошибке, оставшееся за
 * пределами видимой области, равносильно молчаливому отказу.
 *
 * Положения собираются самой разметкой при раскладке, а не задаются списком: список
 * номеров блоков разошёлся бы с формой на первом же перемещении блока — например,
 * на том, которым переключатель режима поднят выше расписания (Д-2).
 *
 * Координаты хранятся в системе окна, а не прокручиваемого содержимого: у блока нет
 * способа узнать своё место внутри содержимого, пока не известно место самого
 * содержимого. Разница вычисляется в [offsetOf] и складывается с текущей прокруткой.
 */
@Stable
class FormAnchors {

    private val offsets = mutableMapOf<DraftField, Int>()
    private var containerTop: Int = 0

    /** Положение блока в окне. */
    fun put(field: DraftField, windowY: Int) {
        offsets[field] = windowY
    }

    /** Положение прокручиваемого содержимого в окне. */
    fun putContainerTop(windowY: Int) {
        containerTop = windowY
    }

    /**
     * Куда прокрутить, чтобы блок оказался вверху видимой области.
     *
     * @param scrollValue текущее значение прокрутки: положение блока измерено при нём.
     */
    fun offsetOf(field: DraftField, scrollValue: Int): Int? =
        offsets[field]?.let { windowY -> (windowY - containerTop + scrollValue).coerceAtLeast(0) }
}
