#!/usr/bin/env python3
"""Самопроверка `personal.py` на образце `personal-sample.csv` (SKL-03, личный пилот).

Запуск: `python3 tools/spike/test_personal.py` — без зависимостей, только стандартная
библиотека. Тест гоняет инструмент целиком (как это делает человек) и сверяет числа
`results.json` с посчитанными вручную по образцу.

Числа образца (зона UTC, 2026-10-01 … 2026-10-05):

* med-a, 08:00 ежедневно: 10-01 +20 с, 10-02 +25 с, 10-03 без записи (пропуск
  восстановлен по регулярности), 10-04 +400 с, 10-05 −20 с;
* med-b, 20:00 ежедневно: 10-01 `fired` без `shown` (тихий отказ), 10-02
  `suppressed`/`notifications_disabled`, 10-03 +15 с, 10-04 +18 с, 10-05 +22 с;
* med-c, 23:00 ежедневно: 10-01 +12 с, 10-02 без записи — момент попадает в окно
  перерыва (`boot`) и исключается, 10-03 +8 с, 10-04 +10 с, 10-05 +11 с;
* med-d, 18:00 ежедневно: 10-01 +30 с, 10-02 без записи — запись `outage` называет
  первый несостоявшийся момент, 10-03 +9 с, 10-04 +7 с, 10-05 +13 с;
* перерывы: `outage` 10-02 18:10 (первый момент 18:00, пропущен 1) и `boot`
  10-03 07:00; исключённых строк — 2, несостоявшихся приёмов без двойного счёта — 2;
* испорченная строка `not-a-time` в образце не роняет разбор (35 записей журнала
  после отбрасывания строки без разобранного времени).

Счёт: приёмов 20 (17 записей + 3 восстановленных пропуска), за перерыв исключено 2,
в знаменателе 18; вовремя 13 → 13/18 = 72,22 %; опоздание 1 (400 с), раньше плана 1
(−20 с), не дошло до показа 2 (подавлено 1 + пропуск 1), сработало-но-не-показано 1.
Отклонения предъявлений (15 значений): медиана 13,0, p90 30,0, p99 400,0,
минимум −20, максимум 400.
"""

import json
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOL = os.path.join(HERE, "personal.py")
SAMPLE = os.path.join(HERE, "personal-sample.csv")

EXPECTED_SUMMARY = {
    "planned": 20,
    "excluded_for_outage": 2,
    "counted": 18,
    "on_time": 13,
    "late": 1,
    "early": 1,
    "suppressed": 1,
    "missing": 1,
    "not_delivered": 2,
    "silent_failures": 1,
    "outage_windows": 2,
    "outage_records": 1,
    "outage_records_missed": 1,
    "outage_missed_total": 2,
    "irregular_series": 0,
    "repeated_presentations": 0,
}

EXPECTED_SHARE = 13 / 18

EXPECTED_DEVIATION = {
    "median": 13.0,
    "p90": 30.0,
    "p99": 400.0,
    "min": -20.0,
    "max": 400.0,
}


def main() -> int:
    failures = []
    with tempfile.TemporaryDirectory() as out_dir:
        process = subprocess.run(
            [sys.executable, TOOL, "--csv", SAMPLE, "--zone", "UTC", "--out-dir", out_dir],
            capture_output=True,
            text=True,
        )
        if process.returncode != 0:
            print("personal.py упал:", process.stderr, file=sys.stderr)
            return 1
        with open(os.path.join(out_dir, "results.json"), encoding="utf-8") as handle:
            results = json.load(handle)

    summary = results["summary"]
    for field, wanted in EXPECTED_SUMMARY.items():
        actual = summary[field]
        if actual != wanted:
            failures.append(f"{field}: получено {actual!r}, ожидалось {wanted!r}")
    share = summary["shares"]["on_time"]
    if share is None or abs(share - EXPECTED_SHARE) > 1e-9:
        failures.append(f"shares.on_time: получено {share!r}, ожидалось {EXPECTED_SHARE!r}")
    for field, wanted in EXPECTED_DEVIATION.items():
        actual = summary["deviation_seconds"][field]
        if actual is None or abs(actual - wanted) > 1e-9:
            failures.append(f"deviation_seconds.{field}: получено {actual!r}, ожидалось {wanted!r}")

    outliers = results["outliers"]
    checks = (
        ("late", ["a4"]),
        ("early", ["a5"]),
        ("silent_failure", ["b1"]),
        ("suppressed", ["b2"]),
        ("missing", ["missing@2026-10-03T08:00:00+00:00"]),
        ("outage", ["missing@2026-10-02T18:00:00+00:00", "missing@2026-10-02T23:00:00+00:00"]),
    )
    for kind, wanted_ids in checks:
        actual_ids = [row["intake_id"] for row in outliers[kind]]
        if actual_ids != wanted_ids:
            failures.append(f"outliers.{kind}: получено {actual_ids!r}, ожидалось {wanted_ids!r}")
    if len(outliers["outage"]) != summary["excluded_for_outage"]:
        failures.append("outliers.outage не совпадает с excluded_for_outage")
    if results["source"]["journal_entries"] != 35:
        failures.append(f"journal_entries: получено {results['source']['journal_entries']!r}, ожидалось 35")

    if failures:
        print("САМОПРОВЕРКА НЕ ПРОЙДЕНА:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1
    print(
        "САМОПРОВЕРКА ПРОЙДЕНА: приёмов 20, исключено за перерыв 2, знаменатель 18, "
        "вовремя 13 (72,2 %), опоздание 1, раньше плана 1, не дошло 2, тихий отказ 1; "
        "медиана 13,0 p90 30,0 p99 400,0, минимум −20, максимум 400."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
