#!/usr/bin/env python3
"""Контактный лист happy-path походов «Склянки».

Кадры походов лежат в build/emulator/api<API>/ui-screens как
`<состояние>__<Класс>.<метод>.png`. Скрипт выбирает по одному ключевому кадру
на поход и собирает один PNG, который можно открыть глазами.

    python3 contact-sheet.py <каталог кадров> <выходной.png>

Зависимость — Pillow. Скрипт не проверяет пиксели и не сравнивает с эталоном:
это свидетельство для человека, а не проверка.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

PAPER = (248, 246, 239)
INK = (20, 24, 27)
MUTED = (94, 90, 82)
BORDER = (216, 210, 196)

# Порядок — путь человека, а не алфавит классов.
JOURNEYS: list[tuple[str, str, str]] = [
    ("FirstLaunchJourneyTest", "today-again", "Первый запуск"),
    ("MedicationCreationJourneyTest", "card", "Новое лекарство"),
    ("TodayMarkingJourneyTest", "day-taken", "Отметки дня"),
    ("StockJourneyTest", "today-purchase-warning", "Остаток и пополнение"),
    ("ArchiveAndDeletionJourneyTest", "archive-card", "Архив и удаление"),
    ("HistoryAndReportJourneyTest", "report-ready", "История и отчёт"),
    ("DataTransferJourneyTest", "overview", "Перенос файлом"),
    ("ProfilesJourneyTest", "profiles-form-free", "Профили"),
    ("ReminderJourneyTest", "reminder-morning", "Напоминания"),
    ("MedicationEditJourneyTest", "card-edited", "Правка лекарства"),
    ("MedicationScheduleJourneyTest", "weekdays-selected", "Недельная схема"),
    ("DiagnosticsJourneyTest", "check-result", "Проверка доставки"),
    ("SurvivalPhaseTwoJourneyTest", "survival-read", "Смерть процесса"),
]

FRAME_RE = re.compile(r"^(?P<state>.+)__(?P<cls>[^.]+)\.")


def font(size: int) -> ImageFont.FreeTypeFont:
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/TTF/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ):
        if Path(path).is_file():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def frames_by_class(directory: Path) -> dict[str, list[Path]]:
    found: dict[str, list[Path]] = {}
    for path in sorted(directory.glob("*.png")):
        match = FRAME_RE.match(path.name)
        if match:
            found.setdefault(match.group("cls"), []).append(path)
    return found


def pick(found: dict[str, list[Path]], cls: str, preferred: str) -> Path | None:
    candidates = found.get(cls, [])
    for path in candidates:
        if path.name.startswith(preferred + "__"):
            return path
    return candidates[-1] if candidates else None


def wrap(draw: ImageDraw.ImageDraw, text: str, width: int, fnt: ImageFont.FreeTypeFont) -> list[str]:
    words = text.split()
    lines: list[str] = []
    current = ""
    for word in words:
        candidate = f"{current} {word}".strip()
        if draw.textlength(candidate, font=fnt) <= width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("frames", type=Path, help="каталог ui-screens")
    parser.add_argument("out", type=Path, help="куда сохранить PNG")
    parser.add_argument("--columns", type=int, default=4)
    parser.add_argument("--frame-width", type=int, default=260)
    args = parser.parse_args()

    found = frames_by_class(args.frames)
    selected: list[tuple[Path, str]] = []
    for cls, preferred, label in JOURNEYS:
        path = pick(found, cls, preferred)
        if path is None:
            print(f"нет кадра для {cls}", file=sys.stderr)
        else:
            selected.append((path, label))
    if not selected:
        print("кадров не найдено", file=sys.stderr)
        return 1

    title_font = font(34)
    subtitle_font = font(18)
    label_font = font(17)
    probe = Image.new("RGB", (10, 10))
    probe_draw = ImageDraw.Draw(probe)

    margin, gutter, label_height = 24, 16, 52
    columns = max(1, args.columns)
    cell_width = args.frame_width
    first = Image.open(selected[0][0]).convert("RGB")
    cell_height = round(first.height * cell_width / first.width)

    rows = (len(selected) + columns - 1) // columns
    title_height = 96
    width = margin * 2 + columns * cell_width + (columns - 1) * gutter
    height = title_height + margin + rows * (cell_height + label_height) + (rows - 1) * gutter + margin
    sheet = Image.new("RGB", (width, height), PAPER)
    draw = ImageDraw.Draw(sheet)

    title = "Склянка · happy-path на эмуляторе, API 35 · free-сборка"
    subtitle = f"{len(selected)} ключевых кадров; кадры походов — свидетельство, не эталоны"
    draw.text((margin, 20), title, font=title_font, fill=INK)
    draw.text((margin, 62), subtitle, font=subtitle_font, fill=MUTED)

    for index, (path, label) in enumerate(selected):
        row, column = divmod(index, columns)
        x = margin + column * (cell_width + gutter)
        y = title_height + margin + row * (cell_height + label_height + gutter)
        image = Image.open(path).convert("RGB")
        image = image.resize((cell_width, cell_height), Image.Resampling.LANCZOS)
        sheet.paste(image, (x, y))
        draw.rectangle((x, y, x + cell_width - 1, y + cell_height - 1), outline=BORDER, width=1)
        label_lines = wrap(draw, label, cell_width, label_font)[:2]
        for line_index, line in enumerate(label_lines):
            draw.text(
                (x, y + cell_height + 7 + line_index * 19),
                line,
                font=label_font,
                fill=INK,
            )

    args.out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.out)
    print(args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
