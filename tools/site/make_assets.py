#!/usr/bin/env python3
"""Сборка ассетов страницы приложения на сайте студии.

Источники:
- знак — `design/screens/SklyankaIcon.dc.html`, холодный вариант (Р-56, Р-57);
- кадры — эталонные снимки экранов Roborazzi (390x844), увеличиваются в 2 раза
  до 780x1688; это предпросмотр для вёрстки, финальные кадры магазина снимаются
  с релизной сборки на устройстве;
- палитра — «олива на бумаге» (Р-20): бумага #F8F6EF, чернила #14181B,
  олива #3F4A33, медь #A86A22, холодный знак #C4F0F7 / #111A1E.

Запуск из корня репозитория:
    python3 tools/site/make_assets.py
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "release" / "site"

PAPER = (248, 246, 239, 255)
INK = (20, 24, 27, 255)
OLIVE = (63, 74, 51, 255)
MUTED = (95, 86, 77, 255)
COPPER = (168, 106, 34, 255)
TEAL = (196, 240, 247)
TILE = (17, 26, 30, 255)

BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BRAND_TITLE = ROOT / "core-ui/src/main/res/font/literata_semibold.ttf"
BRAND = ROOT / "core-ui/src/main/res/font/golos_text_regular.ttf"
BRAND_MEDIUM = ROOT / "core-ui/src/main/res/font/golos_text_medium.ttf"
BRAND_BOLD = ROOT / "core-ui/src/main/res/font/golos_text_semibold.ttf"

# Основной источник — кадры StoreShotsCapture с настоящего приложения (1080×2340),
# снятые с выданным разрешением на уведомления. Если их нет, берётся эталонный экран
# (390×844) и увеличивается; это только аварийный предпросмотр.
STORE_DIR = ROOT / "build" / "store-assets" / "ru-RU"

SCREENS = {
    "01-today": (
        "store-01-today__StoreShotsCapture.storeToday.png",
        "feature-today/screenshots/screen/grouped-time.png",
        "Сегодня",
        "Today",
    ),
    "02-reminder": (
        "store-06-reminder__StoreShotsCapture.storeReminderCard.png",
        "feature-reminder/screenshots/screen/reminder-single.png",
        "Напоминание",
        "Reminder",
    ),
    "03-meds": (
        "store-02-meds__StoreShotsCapture.storeMedicationList.png",
        "feature-meds/screenshots/screen/meds-list.png",
        "Лекарства и остаток",
        "Medications and stock",
    ),
    "04-med-card": (
        "store-03-med-card__StoreShotsCapture.storeMedicationCard.png",
        "feature-meds/screenshots/screen/meds-card.png",
        "Карточка лекарства",
        "Medication card",
    ),
    "05-history": (
        "store-04-history__StoreShotsCapture.storeHistoryForTheMonth.png",
        "feature-history/screenshots/screen/history.png",
        "История",
        "History",
    ),
    "06-form": (
        "store-05-med-form__StoreShotsCapture.storeFilledMedicationForm.png",
        "feature-meds/screenshots/screen/meds-form-preset.png",
        "Новое лекарство",
        "New medication",
    ),
}


def rounded_mask(size: int, radius: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return mask


def render_icon(size: int, simplified: bool = False) -> Image.Image:
    """Холодный знак на тёмной плите, геометрия из макета SklyankaIcon.dc.html.

    На 48 px и меньше два внутренних кольца убираются, как в 48 dp-варианте макета:
    в мелком размере они превращаются в грязь.
    """
    ss = 4
    s = size * ss
    img = Image.new("RGBA", (s, s), TILE)
    draw = ImageDraw.Draw(img)

    # Поле знака: 150 из 180 плитки, как в макете; внешнее кольцо не касается края.
    sign = s * 150 / 180
    scale = sign / 200
    ox = oy = (s - sign) / 2

    def px(x: float, y: float) -> tuple[float, float]:
        return ox + x * scale, oy + y * scale

    def box(cx: float, cy: float, r: float) -> tuple[float, float, float, float]:
        return ox + (cx - r) * scale, oy + (cy - r) * scale, ox + (cx + r) * scale, oy + (cy + r) * scale

    # Свечение плиты и медная тень под знаком.
    glow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse(box(100, 100, 86), fill=(*TEAL, 22))
    gd.ellipse(box(68, 65, 58), fill=(*TEAL, 18))
    gd.ellipse(box(100, 163, 58), fill=(*COPPER[:3], 55))
    glow = glow.filter(ImageFilter.GaussianBlur(max(1, s // 90)))
    img.alpha_composite(glow)

    # Кольца: снаружи внутрь; толщины из макета.
    draw.ellipse(box(100, 100, 86), outline=(*TEAL, 120), width=max(1, round(3.5 * scale)))
    if not simplified:
        draw.ellipse(box(100, 100, 66), outline=(*TEAL, 34), width=max(1, round(7 * scale)))
        draw.ellipse(box(100, 100, 47), outline=(*TEAL, 66), width=max(1, round(2 * scale)))
    draw.ellipse(box(100, 100, 28), outline=(*TEAL, 128), width=max(1, round(2.5 * scale)))

    # Сектор-полумесяц в верхней левой четверти: внешний радиус 76, внутренний 67.
    outer = [(px(100 + 76 * math.cos(math.radians(a)), 100 + 76 * math.sin(math.radians(a)))) for a in range(180, 271)]
    inner = [(px(100 + 67 * math.cos(math.radians(a)), 100 + 67 * math.sin(math.radians(a)))) for a in range(270, 179, -1)]
    draw.polygon(outer + inner, fill=(*TEAL, 209))

    # Медная дуга по нижней правой стороне внешнего кольца.
    draw.arc(box(100, 100, 86), start=20, end=80, fill=COPPER, width=max(1, round(4.5 * scale)))

    # Точка в центре.
    draw.ellipse(box(100, 100, 8), fill=(*TEAL, 235))

    return img.resize((size, size), Image.LANCZOS)


def render_small_icon(size: int) -> Image.Image:
    """48 dp-редукция знака: тёмные штрихи на светлой плите, без внутренних колец.

    Геометрия — из 48 dp-варианта `design/screens/SklyankaIcon.dc.html`: на мелком
    размере полный знак с полупрозрачными кольцами превращается в грязь.
    """
    ss = 8
    s = size * ss
    img = Image.new("RGBA", (s, s), (231, 240, 244, 255))
    draw = ImageDraw.Draw(img)
    sign = s * 150 / 180
    scale = sign / 200
    ox = oy = (s - sign) / 2
    ink = (11, 40, 49)

    def px(x: float, y: float) -> tuple[float, float]:
        return ox + x * scale, oy + y * scale

    def box(cx: float, cy: float, r: float) -> tuple[float, float, float, float]:
        return ox + (cx - r) * scale, oy + (cy - r) * scale, ox + (cx + r) * scale, oy + (cy + r) * scale

    draw.ellipse(box(100, 100, 86), outline=(*ink, 140), width=max(1, round(9 * scale)))
    draw.ellipse(box(100, 100, 28), outline=(*ink, 107), width=max(1, round(6 * scale)))
    outer = [(px(100 + 76 * math.cos(math.radians(a)), 100 + 76 * math.sin(math.radians(a)))) for a in range(180, 271)]
    inner = [(px(100 + 67 * math.cos(math.radians(a)), 100 + 67 * math.sin(math.radians(a)))) for a in range(270, 179, -1)]
    draw.polygon(outer + inner, fill=(*ink, 230))
    draw.ellipse(box(100, 100, 9), fill=(*ink, 204))
    return img.resize((size, size), Image.LANCZOS)


def make_icons() -> None:
    master = render_icon(1024)
    master.save(OUT / "icon-1024.png")
    render_icon(512).save(OUT / "icon-512.png")

    # На 48/32 px полный знак с полупрозрачными кольцами не читается: берём
    # светлую 48 dp-редукцию из макета. Брендовая тёмная плита — 512/1024/192;
    # фактическая adaptive icon приложения лежит отдельно в icon-app-512.
    app_icon = STORE_DIR / "icon-512.png"
    if app_icon.is_file():
        Image.open(app_icon).convert("RGBA").save(OUT / "icon-app-512.png")
    render_icon(192).save(OUT / "icon-192.png")
    render_small_icon(48).save(OUT / "icon-48.png")
    render_small_icon(32).save(OUT / "icon-32.png")
    master.resize((180, 180), Image.LANCZOS).save(OUT / "apple-touch-icon-180.png")


def clean_site_copy(name: str, src: Image.Image) -> Image.Image:
    """Убрать технические имена фикстур `med-a`/`med-b` из кадров для сайта.

    Эталоны не трогаются: это копия для сайта. Имена заменяются демонстрационными
    (из тех же эталонов: «Магний B6», «Эутирокс»), чтобы страница не показывала
    внутренние имена стенда. Координаты сняты с кадров 390×844/390×1100.
    """
    if name == "02-reminder":
        out = src.copy()
        draw = ImageDraw.Draw(out)
        # Фон под именем — вертикальный градиент; берём чистую левую полосу
        # построчно, чтобы заплатка не отличалась от фона.
        for row in range(455, 501):
            draw.line((95, row, 300, row), fill=out.getpixel((40, row)))
        font = ImageFont.truetype(str(ROOT / "core-ui/src/main/res/font/literata_semibold.ttf"), 30)
        text = "Магний B6"
        left, top, right, bottom = draw.textbbox((0, 0), text, font=font)
        x = (out.width - (right - left)) // 2 - left
        y = 466 - top
        draw.text((x, y), text, font=font, fill=(232, 228, 220))
        return out
    if name == "05-history":
        out = src.copy()
        draw = ImageDraw.Draw(out)
        font = ImageFont.truetype(str(ROOT / "core-ui/src/main/res/font/golos_text_semibold.ttf"), 15)
        for old, new, y in (("med-a", "Магний B6", 636), ("med-b", "Эутирокс", 681)):
            draw.rectangle((26, y - 4, 190, y + 20), fill=(255, 255, 255))
            draw.text((36, y), new, font=font, fill=(20, 24, 27))
        return out
    return src


def make_screenshots() -> None:
    for name, (store_name, fallback, _ru, _en) in SCREENS.items():
        store = STORE_DIR / store_name
        if store.is_file():
            Image.open(store).convert("RGB").save(OUT / "screenshots" / f"{name}.png")
            continue
        src = clean_site_copy(name, Image.open(ROOT / fallback).convert("RGB"))
        width, height = src.size
        target_w = 780
        target_h = round(height * target_w / width)
        src.resize((target_w, target_h), Image.LANCZOS).save(OUT / "screenshots" / f"{name}.png")


def make_contact_sheet() -> None:
    cols, width = 3, 300
    phone_h = round(844 * width / 390)
    gap, margin, title_h = 30, 40, 130
    rows = (len(SCREENS) + cols - 1) // cols
    sheet_w = margin * 2 + cols * width + (cols - 1) * gap
    sheet_h = title_h + margin + rows * (phone_h + 52) + (rows - 1) * gap
    sheet = Image.new("RGB", (sheet_w, sheet_h), PAPER)
    draw = ImageDraw.Draw(sheet)
    draw.text((margin, 28), "Склянка · кадры для сайта", font=ImageFont.truetype(BOLD, 34), fill=INK)
    draw.text((margin, 80), "кадры с эмулятора, демонстрационные данные · финал — с релизной сборки", font=ImageFont.truetype(REGULAR, 18), fill=MUTED)

    for index, (name, (_store, _fallback, caption, _en)) in enumerate(SCREENS.items()):
        row, col = divmod(index, cols)
        x = margin + col * (width + gap)
        y = title_h + row * (phone_h + 52 + gap)
        image = Image.open(OUT / "screenshots" / f"{name}.png").convert("RGB")
        ratio = min(width / image.width, phone_h / image.height)
        shot = image.resize((round(image.width * ratio), round(image.height * ratio)), Image.LANCZOS)
        px = x + (width - shot.width) // 2
        py = y + (phone_h - shot.height) // 2
        mask = rounded_mask(shot.width, 22).resize(shot.size)
        sheet.paste(shot, (px, py), mask)
        draw.rounded_rectangle((px, py, px + shot.width - 1, py + shot.height - 1), radius=22, outline=(216, 212, 200), width=1)
        draw.text((x, y + phone_h + 12), caption, font=ImageFont.truetype(REGULAR, 16), fill=MUTED)

    sheet.save(OUT / "screenshots" / "contact-sheet.png")


def make_og() -> None:
    og = Image.new("RGB", (1200, 630), PAPER)
    draw = ImageDraw.Draw(og)

    icon = render_icon(160)
    og.paste(icon, (80, 86), rounded_mask(160, 38))

    draw.text((280, 92), "Склянка", font=ImageFont.truetype(str(BRAND_TITLE), 76), fill=INK)
    draw.text((282, 196), "Учёт лекарств без сети", font=ImageFont.truetype(str(BRAND_MEDIUM), 34), fill=OLIVE)
    draw.text(
        (282, 250),
        "Напоминания, история и отчёт врачу.\nБесплатно. Без рекламы и учётной записи.",
        font=ImageFont.truetype(str(BRAND), 24),
        fill=MUTED,
        spacing=14,
    )
    draw.text((282, 420), "Android 8.0+  ·  шесть языков", font=ImageFont.truetype(str(BRAND), 20), fill=OLIVE)
    draw.text((282, 452), "Данные остаются на устройстве", font=ImageFont.truetype(str(BRAND), 20), fill=OLIVE)
    draw.text((282, 548), "stohastika.ru/sklyanka", font=ImageFont.truetype(str(BRAND_BOLD), 24), fill=COPPER)

    phone = Image.open(OUT / "screenshots" / "01-today.png").convert("RGB")
    phone_w = 250
    phone_h = round(phone.height * phone_w / phone.width)
    phone = phone.resize((phone_w, phone_h), Image.LANCZOS)
    x, y = 900, 45
    shadow = Image.new("RGBA", (phone_w + 40, phone_h + 40), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((20, 20, 20 + phone_w, 20 + phone_h), radius=26, fill=(20, 24, 27, 40))
    shadow = shadow.filter(ImageFilter.GaussianBlur(14))
    og.paste(shadow, (x - 20, y - 20), shadow)
    og.paste(phone, (x, y), rounded_mask(phone_w, 26).resize((phone_w, phone_h)))

    og.save(OUT / "og-1200x630.png")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "screenshots").mkdir(parents=True, exist_ok=True)
    make_icons()
    make_screenshots()
    make_contact_sheet()
    make_og()
    print(f"готово: {OUT}")


if __name__ == "__main__":
    main()
