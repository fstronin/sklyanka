#!/usr/bin/env python3
"""Попиксельное сравнение листа с эталоном (SKL-92).

Считает значимыми пиксели, у которых максимальная разница каналов превышает порог:
антиалиасинг и округление рендера дают разницу в считанные значения канала, и без
порога любой повторный прогон выглядел бы изменением.

Пишет текущий лист с красной подсветкой значимых пикселей. Если размеры различаются,
попиксельное сравнение бессмысленно — сохраняется вертикальная пара «эталон / текущий»
и различие помечается значимым.

Коды возврата: 0 — в пределах допуска, 1 — есть значимые различия, 2 — ошибка.
"""

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageChops


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Сравнить лист с эталоном попиксельно")
    parser.add_argument("--baseline", required=True, help="PNG эталона")
    parser.add_argument("--current", required=True, help="PNG текущего прогона")
    parser.add_argument("--out", required=True, help="куда сохранить картинку различий")
    parser.add_argument("--threshold", type=int, default=8, help="порог разницы канала")
    return parser.parse_args()


def save(image: Image.Image, path: str) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def dimension_mismatch(baseline: Image.Image, current: Image.Image, out: str) -> int:
    height = baseline.height + current.height + 8
    width = max(baseline.width, current.width)
    canvas = Image.new("RGB", (width, height), "white")
    canvas.paste(baseline, (0, 0))
    canvas.paste(current, (0, baseline.height + 8))
    save(canvas, out)
    print(
        f"dims={baseline.width}x{baseline.height}->{current.width}x{current.height} "
        f"changed_pixels=? ratio=? max_delta=? significant=yes"
    )
    return 1


def main() -> int:
    args = parse_args()
    try:
        with Image.open(args.baseline) as baseline_image, Image.open(args.current) as current_image:
            baseline = baseline_image.convert("RGB")
            current = current_image.convert("RGB")
        if baseline.size != current.size:
            return dimension_mismatch(baseline, current, args.out)

        channels = ImageChops.difference(baseline, current).split()
        max_delta = ImageChops.lighter(ImageChops.lighter(channels[0], channels[1]), channels[2])
        mask = max_delta.point(lambda value: 255 if value > args.threshold else 0)
        total = baseline.width * baseline.height
        changed_pixels = mask.histogram()[255]
        ratio = 100.0 * changed_pixels / total if total else 0.0
        peak = max_delta.getextrema()[1]
        overlay = Image.new("RGB", baseline.size, (255, 0, 0))
        save(Image.composite(overlay, current, mask), args.out)
        significant = "yes" if changed_pixels > 0 else "no"
        print(
            f"dims={baseline.width}x{baseline.height}->{current.width}x{current.height} "
            f"changed_pixels={changed_pixels} ratio={ratio:.4f}% "
            f"max_delta={peak} significant={significant}"
        )
        return 1 if changed_pixels > 0 else 0
    except Exception as error:
        print(f"error={error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
