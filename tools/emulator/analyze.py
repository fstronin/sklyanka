#!/usr/bin/env python3
"""Разбор результатов прогона в форме, годной для гейта (SKL-27, SKL-03/TDD §7).

Читает журнал напоминаний приложения (таблица `reminder_log`) и отдаёт два файла:
машинно читаемый `results.json` и человеческую сводку `summary.txt`.

Что считается и почему именно так:

* отклонение по каждому напоминанию — `T2 − T0`, где `T0` — плановый момент,
  `T2` — предъявление уведомления (`04-reminders.md` §1, НАП-2). Отметка получения
  управления приёмником (`T1`, запись `fired`) сохраняется отдельно: по разнице
  `T2 − T1` виден диагноз «задержал будильник» против «задержал показ»
  (SKL-03/TDD §4.1, §10.1);
* «вовремя» — отклонение от 0 до +120 секунд включительно. Отрицательное отклонение
  вовремя НЕ считается: срабатывание раньше планового времени — дефект планирования;
* перерыв прогона отличается от отказа напоминания по записям журнала: отрезок между
  последней записью перед перерывом и пересборкой окна по причине `boot` считается
  перерывом, и приёмы, попавшие в него, исключаются из знаменателя доли отдельным числом
  (SKL-03/TDD §6.3);
* тихий отказ — есть `fired`, нет `shown`: самый опасный класс, выносится числом.

Чего скрипт НЕ делает: он не объявляет гейт НАП-3 пройденным. Гейт считается за 14 суток
непрерывно на живом эталонном устройстве Huawei; эмулятор — AOSP, не EMUI (см. сводку).

Из журнала читаются только разрешённые колонки: идентификаторы, моменты, отклонения
и служебные коды. Названий лекарств и дозировок в журнале нет по построению (НФТ-19),
и запрос их не запрашивает даже случайно — список колонок задан явно.
"""

# Аннотации не вычисляются при разборе файла: разбор написан в виде `str | None`,
# а такая запись существует с Python 3.10. Системный python3 macOS — 3.9, и без этой
# строки скрипт падал при загрузке, ещё не прочитав ни одной записи журнала.
from __future__ import annotations

import argparse
import json
import os
import sqlite3
import statistics
from datetime import datetime, timezone

# НАП-2: границы окна «вовремя», в секундах.
ON_TIME_FROM = 0
ON_TIME_TO = 120

# Явный список колонок: никаких SELECT * — журнал может пополниться колонкой,
# которой в результатах прогона делать нечего.
COLUMNS = (
    "id",
    "occurred_at",
    "event",
    "intake_id",
    "medication_id",
    "profile_id",
    "scheduled_at",
    "fired_at",
    "deviation_seconds",
    "channel",
    "delivered",
    "reason",
    "alarms_cancelled",
    "alarms_scheduled",
    "attempt",
)

DISCLAIMER = (
    "Эмулятор — AOSP, а не EMUI. Испытания И-12 и И-13 (автозапуск и энергосбережение "
    "Huawei) на нём не воспроизводятся, матрица НФТ-40 им не покрывается, и гейт НАП-3 "
    "(95 % вовремя за 14 суток непрерывно) этим прогоном НЕ закрыт: он остаётся за живым "
    "эталонным устройством Huawei — SKL-03."
)


def read_journal(db_path: str) -> list[dict]:
    """Записи журнала с приведённым к нижнему регистру видом записи.

    Вид записи приложение хранит именем константы (`SHOWN`), а печатает в выгрузке
    служебным кодом (`shown`). Разбор сравнивает с кодом, поэтому написание приводится
    здесь, один раз: без этого ни одна запись не попадала ни в один счёт, и прогон,
    в котором напоминания исправно приходили, давал нулевую долю и пустые числа гейта.
    """
    connection = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    columns = ", ".join(COLUMNS)
    rows = connection.execute(f"SELECT {columns} FROM reminder_log ORDER BY occurred_at").fetchall()
    connection.close()
    entries = []
    for row in rows:
        entry = dict(row)
        if entry["event"] is not None:
            entry["event"] = str(entry["event"]).lower()
        entries.append(entry)
    return entries


def read_marks(path: str | None) -> list[dict]:
    """Отметки оператора: какое испытание шло в какой момент."""
    if not path or not os.path.isfile(path):
        return []
    marks = []
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3 and parts[0].isdigit():
                marks.append({"at": int(parts[0]), "trial": parts[1], "state": parts[2]})
    return marks


def read_notifications(path: str | None) -> list[dict]:
    """Независимое подтверждение показа от notify-watch.sh."""
    if not path or not os.path.isfile(path):
        return []
    seen = []
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 2 and parts[0].isdigit():
                seen.append({"at": int(parts[0]), "key": parts[1]})
    return seen


def outage_windows(entries: list[dict]) -> list[dict]:
    """Перерывы прогона: отрезки, ограниченные загрузкой устройства.

    Признак перерыва — пересборка окна по причине `boot` (устройство загрузилось) либо
    запись `outage` (приложение само обнаружило, что его останавливали, НАП-38). Начало
    отрезка — предыдущая запись журнала: журнал молчит ровно тогда, когда устройства нет.
    """
    windows = []
    previous_at = None
    for entry in entries:
        boot = entry["event"] == "rescheduled" and entry["reason"] == "boot"
        outage = entry["event"] == "outage"
        if (boot or outage) and previous_at is not None:
            gap = entry["occurred_at"] - previous_at
            if gap > 0:
                windows.append(
                    {
                        "from": previous_at,
                        "to": entry["occurred_at"],
                        "seconds": gap // 1000,
                        "kind": "boot" if boot else "outage",
                    }
                )
        previous_at = entry["occurred_at"]
    return windows


def in_any_window(moment: int | None, windows: list[dict]) -> bool:
    if moment is None:
        return False
    return any(window["from"] <= moment <= window["to"] for window in windows)


def build_reminders(entries: list[dict], windows: list[dict]) -> list[dict]:
    """Одна строка на напоминание: T0, T1, T2, отклонение, попадание в окно НАП-2."""
    by_key: dict[tuple, dict] = {}
    for entry in entries:
        if entry["event"] not in ("fired", "shown", "repeated", "suppressed"):
            continue
        key = (entry["intake_id"], entry["attempt"])
        row = by_key.setdefault(
            key,
            {
                "intake_id": entry["intake_id"],
                "medication_id": entry["medication_id"],
                "profile_id": entry["profile_id"],
                "attempt": entry["attempt"],
                "scheduled_at": entry["scheduled_at"],
                "fired_at": None,
                "shown_at": None,
                "suppressed_at": None,
                "suppress_reason": None,
                "channel": None,
            },
        )
        if entry["scheduled_at"] is not None:
            row["scheduled_at"] = entry["scheduled_at"]
        if entry["event"] == "fired":
            row["fired_at"] = entry["fired_at"] or entry["occurred_at"]
        elif entry["event"] in ("shown", "repeated"):
            row["shown_at"] = entry["fired_at"] or entry["occurred_at"]
            row["channel"] = entry["channel"]
        elif entry["event"] == "suppressed":
            row["suppressed_at"] = entry["occurred_at"]
            row["suppress_reason"] = entry["reason"]

    reminders = []
    for row in by_key.values():
        scheduled = row["scheduled_at"]
        deviation = None
        control_deviation = None
        if scheduled is not None and row["shown_at"] is not None:
            deviation = (row["shown_at"] - scheduled) / 1000.0
        if scheduled is not None and row["fired_at"] is not None:
            control_deviation = (row["fired_at"] - scheduled) / 1000.0
        row["deviation_seconds"] = deviation
        row["control_deviation_seconds"] = control_deviation
        # T2 − T1: задержал будильник или задержал показ (SKL-03/TDD §10.1).
        row["present_delay_seconds"] = (
            None if deviation is None or control_deviation is None else round(deviation - control_deviation, 3)
        )
        row["on_time"] = deviation is not None and ON_TIME_FROM <= deviation <= ON_TIME_TO
        row["early"] = deviation is not None and deviation < ON_TIME_FROM
        row["silent_failure"] = row["fired_at"] is not None and row["shown_at"] is None and row["suppressed_at"] is None
        row["during_outage"] = in_any_window(scheduled, windows)
        reminders.append(row)
    reminders.sort(key=lambda item: (item["scheduled_at"] or 0, item["intake_id"] or "", item["attempt"] or 0))
    return reminders


def percentile(values: list[float], share: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round(share * (len(ordered) - 1))))
    return ordered[index]


def summarize(reminders: list[dict], windows: list[dict]) -> dict:
    counted = [row for row in reminders if not row["during_outage"]]
    excluded = [row for row in reminders if row["during_outage"]]
    deviations = [row["deviation_seconds"] for row in counted if row["deviation_seconds"] is not None]
    on_time = [row for row in counted if row["on_time"]]
    return {
        "planned": len(reminders),
        "excluded_for_outage": len(excluded),
        "counted": len(counted),
        "presented": len(deviations),
        "on_time": len(on_time),
        "on_time_share": (len(on_time) / len(counted)) if counted else None,
        "silent_failures": sum(1 for row in counted if row["silent_failure"]),
        "suppressed": sum(1 for row in counted if row["suppressed_at"] is not None),
        "early": sum(1 for row in counted if row["early"]),
        "deviation_seconds": {
            "median": statistics.median(deviations) if deviations else None,
            "p90": percentile(deviations, 0.90),
            "p99": percentile(deviations, 0.99),
            "max": max(deviations) if deviations else None,
            "min": min(deviations) if deviations else None,
        },
        "outage_windows": len(windows),
        "outage_seconds": sum(window["seconds"] for window in windows),
    }


def human(summary: dict, reminders: list[dict], windows: list[dict], marks: list[dict], platform: str,
          notifications: list[dict]) -> str:
    def moment(value: int | None) -> str:
        if value is None:
            return "—"
        return datetime.fromtimestamp(value / 1000, tz=timezone.utc).isoformat(timespec="seconds")

    share = summary["on_time_share"]
    share_text = "нет данных" if share is None else f"{share * 100:.1f} %"
    lines = [
        "Прогон напоминаний на эмуляторе",
        f"Платформа: {platform or 'не записана'}",
        "",
        f"Запланировано напоминаний:            {summary['planned']}",
        f"Исключено за перерыв прогона:         {summary['excluded_for_outage']}",
        f"В знаменателе доли:                   {summary['counted']}",
        f"Предъявлено человеку:                 {summary['presented']}",
        f"Вовремя (0 … +120 с по НАП-2):        {summary['on_time']} — {share_text}",
        f"Тихих отказов (есть fired, нет shown):{summary['silent_failures']:>4}",
        f"Показ подавлен приложением:           {summary['suppressed']}",
        f"Сработало раньше планового времени:   {summary['early']}",
        "",
        "Отклонение предъявления от плана, секунды:",
        f"  медиана {summary['deviation_seconds']['median']}  p90 {summary['deviation_seconds']['p90']}  "
        f"p99 {summary['deviation_seconds']['p99']}  максимум {summary['deviation_seconds']['max']}",
        "",
        f"Перерывов прогона: {summary['outage_windows']} на {summary['outage_seconds']} с",
    ]
    for window in windows:
        lines.append(f"  {moment(window['from'])} … {moment(window['to'])} ({window['kind']}, {window['seconds']} с)")
    lines += ["", f"Независимых подтверждений показа от наблюдателя уведомлений: {len(notifications)}"]
    if not notifications:
        lines.append(
            "  наблюдатель не работал: отметка предъявления взята из журнала приложения, "
            "то есть подтверждена самим приложением, а не системой (SKL-03/TDD §4.2)"
        )
    if marks:
        lines += ["", "Отметки испытаний:"]
        for mark in marks:
            lines.append(f"  {moment(mark['at'])}  {mark['trial']}  {mark['state']}")
    worst = sorted(
        (row for row in reminders if row["deviation_seconds"] is not None),
        key=lambda row: row["deviation_seconds"],
        reverse=True,
    )[:10]
    if worst:
        lines += ["", "Худшие десять отклонений (приём, план, предъявление, отклонение):"]
        for row in worst:
            lines.append(
                f"  {row['intake_id']}  {moment(row['scheduled_at'])}  {moment(row['shown_at'])}  "
                f"{row['deviation_seconds']} с"
            )
    lines += ["", "ГРАНИЦА ПРОГОНА", DISCLAIMER]
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description="Разбор журнала напоминаний после прогона на эмуляторе")
    parser.add_argument("--db", required=True, help="снятая копия sklyanka.db")
    parser.add_argument("--marks", help="trial-marks.tsv от trials.sh")
    parser.add_argument("--notifications", help="notifications.tsv от notify-watch.sh")
    parser.add_argument("--platform", default="", help="строка платформы образа (НАП-40)")
    parser.add_argument("--out-dir", required=True)
    args = parser.parse_args()

    entries = read_journal(args.db)
    windows = outage_windows(entries)
    reminders = build_reminders(entries, windows)
    summary = summarize(reminders, windows)
    marks = read_marks(args.marks)
    notifications = read_notifications(args.notifications)

    os.makedirs(args.out_dir, exist_ok=True)
    results = {
        "generated_at": datetime.now(tz=timezone.utc).isoformat(timespec="seconds"),
        "platform": args.platform,
        "gate": {
            "requirement": "НАП-3 / НФТ-1: не менее 95 % вовремя за 14 суток непрерывно",
            "closed_by_this_run": False,
            "why": DISCLAIMER,
        },
        "summary": summary,
        "reminders": reminders,
        "outage_windows": windows,
        "trial_marks": marks,
        "independent_notifications": notifications,
        "journal_entries": len(entries),
    }
    json_path = os.path.join(args.out_dir, "results.json")
    with open(json_path, "w", encoding="utf-8") as handle:
        json.dump(results, handle, ensure_ascii=False, indent=2)
    text_path = os.path.join(args.out_dir, "summary.txt")
    report = human(summary, reminders, windows, marks, args.platform, notifications)
    with open(text_path, "w", encoding="utf-8") as handle:
        handle.write(report)
    print(report)
    print(f"машинно читаемый результат: {json_path}")


if __name__ == "__main__":
    main()
