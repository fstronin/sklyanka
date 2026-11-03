#!/usr/bin/env python3
"""Пересобирает кадры карточки магазина под 9:16 без потери интерфейса.

RuStore принимает кадры телефона в пропорции 9:16 и обрезает всё, что шире
рекомендованной рамки: кадр 1080×2340 (19,5:9) теряет 420 px по высоте.
Обрезка «на глаз» срезает шапку или нижнюю панель, поэтому кадр вписывается
по высоте и дополняется фоном страницы по бокам — интерфейс остаётся целиком.

Использование:
    python3 tools/release/fit_store_shots.py [источник] [приёмник]

По умолчанию источник — docs/release/site/screenshots, приёмник —
build/rustore-shots (артефакт сборки, в git не попадает).
"""

import glob
import os
import sys

from PIL import Image

WIDTH, HEIGHT = 1080, 1920
DEFAULT_SRC = "docs/release/site/screenshots"
DEFAULT_DST = "build/rustore-shots"


def page_background(image: Image.Image) -> tuple[int, int, int]:
    """Цвет фона страницы: пиксель в левом поле выше первой карточки."""
    return image.getpixel((4, min(300, image.height - 1)))


def fit(path: str, dst_dir: str) -> str:
    image = Image.open(path).convert("RGB")
    scaled = image.resize((round(image.width * HEIGHT / image.height), HEIGHT), Image.LANCZOS)
    canvas = Image.new("RGB", (WIDTH, HEIGHT), page_background(image))
    canvas.paste(scaled, ((WIDTH - scaled.width) // 2, 0))
    target = os.path.join(dst_dir, os.path.basename(path))
    canvas.save(target, "PNG")
    return target


def main(argv: list[str]) -> int:
    src = argv[1] if len(argv) > 1 else DEFAULT_SRC
    dst = argv[2] if len(argv) > 2 else DEFAULT_DST
    paths = sorted(glob.glob(os.path.join(src, "0[1-9]-*.png")))
    if not paths:
        print(f"нет кадров в {src}", file=sys.stderr)
        return 1
    os.makedirs(dst, exist_ok=True)
    for path in paths:
        target = fit(path, dst)
        print(f"{target} {WIDTH}×{HEIGHT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
