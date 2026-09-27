#!/usr/bin/env python3
"""Генератор синтетической нагрузки прогона (SKL-27).

Пишет файл переноса данных «Склянки» (ПЕР-2, ПЕР-6: опознаватель `sklyanka-export`,
версия формата 1) с нагрузкой испытания И-16: пять лекарств, восемь приёмов в сутки.

Названия синтетические и нейтральные: в файл не попадает ничего медицинского
(НФТ-19), потому что файл потом лежит на образе и уезжает в результаты прогона.

Два режима:
  --mode day   восемь приёмов по суткам, включая 23:55 и 00:05 — под испытание И-9;
  --mode fast  восемь приёмов с шагом в минутах от «сейчас» — ускоренный прогон И-16,
               который проверяет механику и НЕ закрывает гейт НАП-3.
"""

import argparse
import json
from datetime import datetime, timedelta

MARKER = "sklyanka-export"
FORMAT_VERSION = 1
DAY_TIMES = ["06:00", "08:00", "11:00", "14:00", "17:00", "20:00", "23:55", "00:05"]


def times_for(mode: str, step_minutes: int, count: int) -> list[str]:
    if mode == "day":
        return DAY_TIMES[:count]
    start = datetime.now() + timedelta(minutes=2)
    return [(start + timedelta(minutes=step_minutes * i)).strftime("%H:%M") for i in range(count)]


def build(medications: int, times: list[str], profile_id: str) -> dict:
    today = datetime.now().date()
    meds = []
    for index in range(1, medications + 1):
        meds.append(
            {
                "id": f"med-{index}",
                "profile_id": profile_id,
                "name": f"Стенд {index}",
                "form": "tablet",
                "dose_amount": 1,
                "dose_unit": "tablet",
                "food_relation": "any",
                "schedule_mode": "scheduled",
                "times": times,
                "start_date": today.isoformat(),
                "stock_left": 500,
                "stock_threshold_days": 3,
            }
        )
    return {
        "format": MARKER,
        "format_version": FORMAT_VERSION,
        "exported_at": datetime.now().astimezone().isoformat(timespec="seconds"),
        "app": {"version_name": "0.1.0", "version_code": 1, "platform": "android"},
        "counts": {"profiles": 1, "medications": len(meds), "archived": 0, "intakes": 0},
        "profiles": [{"id": profile_id, "name": "Стенд", "is_self": True, "mirror_reminders": False}],
        "medications": meds,
        # Приёмы не перечисляются: их порождает планировщик приёмов (SKL-06) по окну
        # вперёд — ровно тот механизм, который и проверяется прогоном.
        "intakes": [],
        "settings": {
            "reminders": {"persistent_enabled": True, "snooze_minutes": 10},
            "watch": {"enabled": False, "snooze_button": False},
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Синтетическая нагрузка прогона на эмуляторе")
    parser.add_argument("--mode", choices=("day", "fast"), default="day")
    parser.add_argument("--medications", type=int, default=5)
    parser.add_argument("--times", type=int, default=8)
    parser.add_argument("--step-minutes", type=int, default=6)
    parser.add_argument("--profile-id", default="profile-stand")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    snapshot = build(args.medications, times_for(args.mode, args.step_minutes, args.times), args.profile_id)
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(snapshot, handle, ensure_ascii=False, indent=2)
    print(args.out)


if __name__ == "__main__":
    main()
