#!/usr/bin/env python3
"""Расчёт чисел гейта НАП-3 по журналу напоминаний (SKL-03, TDD.md §7).

Зачем отдельный скрипт, а не `tools/emulator/analyze.py`. Тот читает базу эмулятора
и объявляет свой прогон не-гейтом; он принадлежит приёмке SKL-27, и его правка под роль
гейта размыла бы чужой артефакт. Формулы и признаки здесь те же самые, до констант:
отклонение `T2 − T0`, окно 0…+120 секунд, исключение перерывов, тихие отказы.

Вход — то, что заказчик приносит с устройства:

* выгруженный приложением CSV (`sklyanka-reminder-journal-ГГГГ-ММ-ДД.csv`), основной путь;
* снятая копия базы `sklyanka.db` (резервный путь отладочной сборки);
* отметки сценариев `marks.tsv` — строки `момент<TAB>код<TAB>состояние`;
* строка платформы: модель, EMUI, версия Android — из ранбука (НАП-40).

Что считается:

* **одно напоминание — один приём** (`intake_id`), а не каждая попытка показа: настойчивый
  повтор — политика поверх плана, а не второе запланированное напоминание (НАП-25/26).
  Отклонение берётся по **первому** предъявлению приёма;
* `T0` — плановое время (`scheduled_at`), `T1` — первая запись `fired`, `T2` — первое
  предъявление (`shown`/`repeated`). Гейт — по `T2`, `T1` — для диагноза;
* «вовремя» — отклонение от 0 до +120 с включительно; отрицательное — дефект планирования,
  в долю не засчитывается;
* перерыв — отрезок от предыдущей записи журнала до пересборки с причиной `boot` или
  до записи `outage`; приёмы внутри него исключаются из знаменателя отдельным числом;
* тихий отказ — есть `fired`, нет ни `shown`, ни `suppressed`;
* разбивка по сценариям — по окнам отметок оператора; для каждого называются признаки
  (`boot`, `timezone_changed`, `outage` и т. д.), которые сценарий обязан оставить.

Чего скрипт НЕ делает: не объявляет гейт пройденным. Порог 95 % он применяет и называет
долю, но решение принимает человек по условиям прогона (14 суток непрерывно, настроенный
эталон, зафиксированная прошивка). В журнале нет медицинских данных (НФТ-19) — скрипт
читает только идентификаторы, моменты и служебные коды.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import sqlite3
import statistics
from datetime import datetime, timedelta, timezone

# НАП-2: границы окна «вовремя», в секундах.
ON_TIME_FROM = 0
ON_TIME_TO = 120

# Минимум непрерывного прогона гейта, сутки (НАП-3).
GATE_DAYS = 14
GATE_SHARE = 0.95

# Колонки журнала, читаемые из базы: никаких SELECT * — журнал может пополниться
# колонкой, которой в результатах делать нечего.
DB_COLUMNS = (
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
    "snooze_minutes",
)

# Колонки, которых в результатах не должно быть никогда (НФТ-19): наличие в CSV
# колонки с названием или дозировкой — отказ разбора, а не тихий проход.
FORBIDDEN_COLUMNS = ("name", "dose", "strength", "medication_name", "profile_name")


def parse_instant(value) -> int | None:
    """Момент в epoch-миллисекундах: ISO-8601 из CSV либо число из базы."""
    if value is None or value == "":
        return None
    if isinstance(value, (int, float)):
        return int(value)
    text = str(value).strip()
    if not text:
        return None
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    try:
        moment = datetime.fromisoformat(text)
    except ValueError:
        return None
    if moment.tzinfo is None:
        moment = moment.replace(tzinfo=timezone.utc)
    return int(moment.timestamp() * 1000)


def read_csv_journal(path: str) -> list[dict]:
    """Журнал из выгруженного приложением CSV."""
    with open(path, encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        header = [column.strip() for column in reader.fieldnames or []]
        forbidden = [column for column in header if column in FORBIDDEN_COLUMNS]
        if forbidden:
            raise SystemExit(f"в выгрузке есть медицинские поля: {', '.join(forbidden)}")
        entries = []
        for row in reader:
            if not row or row.get("event") in (None, ""):
                continue
            entries.append(
                {
                    "occurred_at": parse_instant(row.get("occurred_at")),
                    "event": str(row.get("event", "")).strip().lower() or None,
                    "intake_id": row.get("intake_id") or None,
                    "medication_id": row.get("medication_id") or None,
                    "profile_id": row.get("profile_id") or None,
                    "scheduled_at": parse_instant(row.get("scheduled_at")),
                    "fired_at": parse_instant(row.get("fired_at")),
                    "deviation_seconds": to_int(row.get("deviation_seconds")),
                    "channel": row.get("channel") or None,
                    "delivered": to_bool(row.get("delivered")),
                    "reason": row.get("reason") or None,
                    "alarms_cancelled": to_int(row.get("alarms_cancelled")),
                    "alarms_scheduled": to_int(row.get("alarms_scheduled")),
                    "attempt": to_int(row.get("attempt")),
                }
            )
    return [entry for entry in entries if entry["occurred_at"] is not None]


def read_db_journal(path: str) -> list[dict]:
    """Журнал из снятой копии базы (резервный путь)."""
    connection = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    columns = ", ".join(DB_COLUMNS)
    rows = connection.execute(f"SELECT {columns} FROM reminder_log ORDER BY occurred_at").fetchall()
    connection.close()
    entries = []
    for row in rows:
        entry = dict(row)
        entry["event"] = str(entry["event"]).lower() if entry["event"] is not None else None
        for field in ("occurred_at", "scheduled_at", "fired_at"):
            entry[field] = parse_instant(entry[field])
        entry["delivered"] = to_bool(entry["delivered"])
        entries.append(entry)
    return [entry for entry in entries if entry["occurred_at"] is not None]


def to_int(value) -> int | None:
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def to_bool(value) -> bool | None:
    if value is None or value == "":
        return None
    text = str(value).strip().lower()
    if text in ("true", "1", "yes"):
        return True
    if text in ("false", "0", "no"):
        return False
    return None


def read_marks(path: str | None, zone) -> list[dict]:
    """Отметки оператора: `момент<TAB>код<TAB>состояние`.

    Момент — epoch-миллисекунды (как пишет `mark.sh` по часам устройства) либо ISO-8601.
    """
    if not path or not os.path.isfile(path):
        return []
    marks = []
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 3:
                continue
            at = parse_mark_moment(parts[0], zone)
            if at is None:
                continue
            marks.append({"at": at, "trial": parts[1].strip(), "state": parts[2].strip()})
    marks.sort(key=lambda mark: mark["at"])
    return marks


def parse_mark_moment(value: str, zone) -> int | None:
    text = value.strip()
    if text.isdigit():
        return int(text)
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    try:
        moment = datetime.fromisoformat(text)
    except ValueError:
        return None
    if moment.tzinfo is None:
        moment = moment.replace(tzinfo=zone)
    return int(moment.timestamp() * 1000)


def outage_windows(entries: list[dict]) -> list[dict]:
    """Перерывы: отрезки, ограниченные загрузкой устройства или обнаружением остановки."""
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


def parse_times(text: str | None) -> list[tuple[int, int]]:
    """Времена слотов «ЧЧ:ММ», через запятую. Пусто — сетку выведем из журнала."""
    if not text:
        return []
    times = []
    for part in text.split(","):
        part = part.strip()
        if not part:
            continue
        hour, _, minute = part.partition(":")
        times.append((int(hour), int(minute)))
    return times


def observed_local_moments(entries: list[dict], zone) -> list[datetime]:
    """Плановые моменты, о которых журнал что-то знает, в местном времени устройства."""
    moments = {
        entry["scheduled_at"]
        for entry in entries
        if entry["event"] in ("fired", "shown", "repeated", "suppressed") and entry["scheduled_at"] is not None
    }
    return sorted(
        datetime.fromtimestamp(moment / 1000, tz=zone) for moment in moments
    )


def expected_schedule(
    entries: list[dict], zone, times: list[tuple[int, int]], per_slot: int
) -> list[tuple[int, int]]:
    """Сетка ожидаемых моментов и сколько приёмов обязано быть в каждом.

    Зачем. Напоминание, у которого нет ни одной записи журнала (будильник потерян —
    диагноз «ни T1, ни T2» из TDD §11.1), по одному журналу невидимо: продукт записывает
    только то, что произошло. Без сетки знаменатель доли состоял бы из одних состоявшихся
    событий, и полная потеря будильников улучшала бы долю. Сетка собирается из расписания
    стенда: пять лекарств в каждом из восьми слотов суток, все слоты одинаковы.

    Края обрезаются по первому и последнему наблюдённым моментам: слоты до заведения
    нагрузки и после снятия журнала не были запланированы и в знаменатель не входят.
    """
    observed = observed_local_moments(entries, zone)
    if not observed or per_slot <= 0:
        return []
    if not times:
        times = sorted({(moment.hour, moment.minute) for moment in observed})
    first, last = observed[0], observed[-1]
    grid = []
    date = first.date()
    while date <= last.date():
        for hour, minute in times:
            moment = datetime(date.year, date.month, date.day, hour, minute, tzinfo=zone)
            if moment < first or moment > last:
                continue
            grid.append((int(moment.timestamp() * 1000), per_slot))
        date = date + timedelta(days=1)
    return sorted(grid)


def lost_reminders(entries: list[dict], grid: list[tuple[int, int]], windows: list[dict]) -> list[dict]:
    """Приёмы, которые были запланированы, но не оставили в журнале ни одной записи."""
    observed: dict[int, set] = {}
    for entry in entries:
        if entry["event"] in ("fired", "shown", "repeated", "suppressed") and entry["scheduled_at"] is not None:
            observed.setdefault(entry["scheduled_at"], set()).add(entry["intake_id"])
    lost = []
    for moment, expected in grid:
        seen = len(observed.get(moment, ()))
        for index in range(max(0, expected - seen)):
            lost.append(
                {
                    "intake_id": f"lost@{moment}#{index + 1}",
                    "medication_id": None,
                    "profile_id": None,
                    "scheduled_at": moment,
                    "fired_at": None,
                    "shown_at": None,
                    "suppressed_at": None,
                    "suppress_reason": None,
                    "channel": None,
                    "presented_attempt": None,
                    "repeat_shows": 0,
                    "lost": True,
                    "during_outage": in_any_window(moment, windows),
                    "deviation_seconds": None,
                    "control_deviation_seconds": None,
                    "present_delay_seconds": None,
                    "on_time": False,
                    "early": False,
                    "silent_failure": False,
                    "failure": True,
                }
            )
    return lost


def build_reminders(entries: list[dict], windows: list[dict]) -> list[dict]:
    """Одна строка на приём: T0, T1, T2, отклонение, попадание в окно НАП-2."""
    by_intake: dict[str, dict] = {}
    for entry in entries:
        if entry["event"] not in ("fired", "shown", "repeated", "suppressed"):
            continue
        intake_id = entry["intake_id"]
        if intake_id is None:
            continue
        row = by_intake.setdefault(
            intake_id,
            {
                "intake_id": intake_id,
                "medication_id": entry["medication_id"],
                "profile_id": entry["profile_id"],
                "scheduled_at": entry["scheduled_at"],
                "fired_at": None,
                "shown_at": None,
                "suppressed_at": None,
                "suppress_reason": None,
                "channel": None,
                "presented_attempt": None,
                "repeat_shows": 0,
                "lost": False,
            },
        )
        if entry["scheduled_at"] is not None:
            row["scheduled_at"] = entry["scheduled_at"]
        if entry["event"] == "fired" and row["fired_at"] is None:
            row["fired_at"] = entry["fired_at"] or entry["occurred_at"]
        elif entry["event"] in ("shown", "repeated"):
            show_at = entry["fired_at"] or entry["occurred_at"]
            if row["shown_at"] is None or show_at < row["shown_at"]:
                row["shown_at"] = show_at
                row["channel"] = entry["channel"]
                row["presented_attempt"] = entry["attempt"]
            else:
                row["repeat_shows"] += 1
        elif entry["event"] == "suppressed" and row["suppressed_at"] is None:
            row["suppressed_at"] = entry["occurred_at"]
            row["suppress_reason"] = entry["reason"]

    reminders = []
    for row in by_intake.values():
        scheduled = row["scheduled_at"]
        deviation = None
        control_deviation = None
        if scheduled is not None and row["shown_at"] is not None:
            deviation = (row["shown_at"] - scheduled) / 1000.0
        if scheduled is not None and row["fired_at"] is not None:
            control_deviation = (row["fired_at"] - scheduled) / 1000.0
        row["deviation_seconds"] = deviation
        row["control_deviation_seconds"] = control_deviation
        row["present_delay_seconds"] = (
            None if deviation is None or control_deviation is None else round(deviation - control_deviation, 3)
        )
        row["on_time"] = deviation is not None and ON_TIME_FROM <= deviation <= ON_TIME_TO
        row["early"] = deviation is not None and deviation < ON_TIME_FROM
        row["silent_failure"] = row["fired_at"] is not None and row["shown_at"] is None and row["suppressed_at"] is None
        row["failure"] = row["shown_at"] is None
        row["during_outage"] = in_any_window(scheduled, windows)
        reminders.append(row)
    reminders.sort(key=lambda item: (item["scheduled_at"] or 0, item["intake_id"]))
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
    scheduled_moments = [row["scheduled_at"] for row in reminders if row["scheduled_at"] is not None]
    return {
        "planned": len(reminders),
        "excluded_for_outage": len(excluded),
        "counted": len(counted),
        "presented": len(deviations),
        "on_time": len(on_time),
        "on_time_share": (len(on_time) / len(counted)) if counted else None,
        "failed": sum(1 for row in counted if row["failure"]),
        "lost_without_any_record": sum(1 for row in counted if row.get("lost")),
        "silent_failures": sum(1 for row in counted if row["silent_failure"]),
        "suppressed": sum(1 for row in counted if row["suppressed_at"] is not None),
        "early": sum(1 for row in counted if row["early"]),
        "repeated_presentations": sum(row["repeat_shows"] for row in counted),
        "first_scheduled_at": min(scheduled_moments) if scheduled_moments else None,
        "last_scheduled_at": max(scheduled_moments) if scheduled_moments else None,
        "span_days": (
            (max(scheduled_moments) - min(scheduled_moments)) / 86_400_000.0 if scheduled_moments else None
        ),
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


def scenario_evidence(entries: list[dict], start: int, end: int) -> dict:
    """Признаки сценария внутри окна отметок: что журнал обязан был оставить."""
    events: dict[str, int] = {}
    reasons: dict[str, int] = {}
    suppress_reasons: dict[str, int] = {}
    zero_alarms = 0
    for entry in entries:
        at = entry["occurred_at"]
        if at < start or at > end:
            continue
        event = entry["event"] or "unknown"
        events[event] = events.get(event, 0) + 1
        if event == "rescheduled" and entry["reason"]:
            reasons[entry["reason"]] = reasons.get(entry["reason"], 0) + 1
            if entry["alarms_scheduled"] == 0:
                zero_alarms += 1
        if event == "suppressed" and entry["reason"]:
            suppress_reasons[entry["reason"]] = suppress_reasons.get(entry["reason"], 0) + 1
    return {
        "events": events,
        "reschedule_reasons": reasons,
        "suppress_reasons": suppress_reasons,
        "rescheduled_without_alarms": zero_alarms,
    }


def scenario_breakdown(reminders: list[dict], entries: list[dict], marks: list[dict]) -> list[dict]:
    """Разбивка по сценариям: окно отметок → числа и признаки."""
    windows: dict[str, dict] = {}
    for mark in marks:
        window = windows.setdefault(mark["trial"], {"start": mark["at"], "end": mark["at"], "states": []})
        window["start"] = min(window["start"], mark["at"])
        window["end"] = max(window["end"], mark["at"])
        window["states"].append(mark["state"])
    scenarios = []
    for code in sorted(windows):
        window = windows[code]
        start, end = window["start"], window["end"]
        members = [row for row in reminders if row["scheduled_at"] is not None and start <= row["scheduled_at"] <= end]
        counted = [row for row in members if not row["during_outage"]]
        deviations = [row["deviation_seconds"] for row in counted if row["deviation_seconds"] is not None]
        on_time = sum(1 for row in counted if row["on_time"])
        scenarios.append(
            {
                "trial": code,
                "from": start,
                "to": end,
                "states": window["states"],
                "planned": len(members),
                "excluded_for_outage": len(members) - len(counted),
                "presented": len(deviations),
                "on_time": on_time,
                "on_time_share": (on_time / len(counted)) if counted else None,
                "deviations": deviations,
                "evidence": scenario_evidence(entries, start, end),
            }
        )
    return scenarios


def moment_text(value: int | None) -> str:
    if value is None:
        return "—"
    return datetime.fromtimestamp(value / 1000, tz=timezone.utc).isoformat(timespec="seconds")


def human(summary: dict, reminders: list[dict], windows: list[dict], scenarios: list[dict],
          platform: str, run_kind: str) -> str:
    share = summary["on_time_share"]
    share_text = "нет данных" if share is None else f"{share * 100:.1f} %"
    span = summary["span_days"]
    span_text = "нет данных" if span is None else f"{span:.2f} суток"
    lines = [
        f"Прогон напоминаний: {run_kind}",
        f"Платформа: {platform or 'не записана'}",
        f"Окно прогона по плановым моментам: {moment_text(summary['first_scheduled_at'])} … "
        f"{moment_text(summary['last_scheduled_at'])} ({span_text})",
        "",
        f"Запланировано приёмов:                {summary['planned']}",
        f"Исключено за перерыв прогона:         {summary['excluded_for_outage']}",
        f"В знаменателе доли:                   {summary['counted']}",
        f"Предъявлено человеку:                 {summary['presented']}",
        f"Вовремя (0 … +120 с по НАП-2):        {summary['on_time']} — {share_text}",
        f"Не предъявлено вовсе:                 {summary['failed']}",
        f"  из них без единой записи (потерян):  {summary['lost_without_any_record']}",
        f"Тихих отказов (есть fired, нет shown):{summary['silent_failures']:>4}",
        f"Показ подавлен приложением:           {summary['suppressed']}",
        f"Сработало раньше планового времени:   {summary['early']}",
        f"Повторных предъявлений (настойчивость):{summary['repeated_presentations']:>3}",
        "",
        "Отклонение предъявления от плана, секунды:",
        f"  медиана {summary['deviation_seconds']['median']}  p90 {summary['deviation_seconds']['p90']}  "
        f"p99 {summary['deviation_seconds']['p99']}  максимум {summary['deviation_seconds']['max']}",
        "",
        f"Перерывов прогона: {summary['outage_windows']} на {summary['outage_seconds']} с",
    ]
    for window in windows:
        lines.append(f"  {moment_text(window['from'])} … {moment_text(window['to'])} ({window['kind']}, {window['seconds']} с)")
    if scenarios:
        lines += ["", "Разбивка по сценариям (окна отметок оператора):"]
        for scenario in scenarios:
            scenario_share = scenario["on_time_share"]
            scenario_text = "нет данных" if scenario_share is None else f"{scenario_share * 100:.1f} %"
            evidence = scenario["evidence"]
            reasons = ", ".join(f"{key}:{value}" for key, value in sorted(evidence["reschedule_reasons"].items()))
            suppressed = ", ".join(f"{key}:{value}" for key, value in sorted(evidence["suppress_reasons"].items()))
            lines.append(
                f"  {scenario['trial']}: приёмов {scenario['planned']} "
                f"(исключено за перерыв {scenario['excluded_for_outage']}), "
                f"предъявлено {scenario['presented']}, "
                f"вовремя {scenario['on_time']} ({scenario_text})"
            )
            lines.append(
                f"    признаки: пересборки {reasons or '—'}; подавления {suppressed or '—'}; "
                f"пересборок без будильников {evidence['rescheduled_without_alarms']}"
            )
    else:
        lines += ["", "Отметки сценариев не найдены: разбивки по И-1 … И-16 в этом разборе нет."]
    advice = (
        f"Порог гейта НАП-3: не менее {GATE_SHARE * 100:.0f} % и не менее {GATE_DAYS} суток "
        "непрерывного прогона на настроенном эталоне. Скрипт называет числа; гейт объявляет человек."
    )
    lines += [
        "",
        "УСЛОВИЯ РЕШЕНИЯ ПО ГЕЙТУ",
        advice,
        "Проверьте до решения: 14 суток непрерывно; сборка не переустанавливалась; состояние",
        "настройки устройства зафиксировано письменно до старта (НАП-40, критерии 4–5).",
    ]
    worst = sorted(
        (row for row in reminders if row["deviation_seconds"] is not None),
        key=lambda row: row["deviation_seconds"],
        reverse=True,
    )[:10]
    if worst:
        lines += ["", "Худшие десять отклонений (приём, план, предъявление, отклонение):"]
        for row in worst:
            lines.append(
                f"  {row['intake_id']}  {moment_text(row['scheduled_at'])}  {moment_text(row['shown_at'])}  "
                f"{row['deviation_seconds']} с"
            )
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description="Расчёт чисел гейта НАП-3 по журналу напоминаний")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--csv", help="выгруженный приложением журнал (основной путь)")
    source.add_argument("--db", help="снятая копия sklyanka.db (резервный путь)")
    parser.add_argument("--marks", help="marks.tsv с отметками сценариев")
    parser.add_argument("--platform", default="", help="строка платформы: модель, EMUI, Android (НАП-40)")
    parser.add_argument("--run-kind", default="эталонный Huawei", help="название прогона для сводки")
    parser.add_argument(
        "--times",
        help="времена слотов нагрузки «ЧЧ:ММ,ЧЧ:ММ,…»; без них сетка выводится из журнала",
    )
    parser.add_argument(
        "--per-slot",
        type=int,
        default=0,
        help="сколько приёмов обязано быть в каждом слоте (пять лекарств стенда); 0 — сетку не строить",
    )
    parser.add_argument("--zone", default="", help="часовой пояс устройства (IANA); по умолчанию — местный пояс машины")
    parser.add_argument("--out-dir", required=True)
    args = parser.parse_args()

    entries = read_csv_journal(args.csv) if args.csv else read_db_journal(args.db)
    if not entries:
        raise SystemExit("журнал пуст: расчёт невозможен")
    if args.zone:
        from zoneinfo import ZoneInfo

        zone = ZoneInfo(args.zone)
    else:
        zone = datetime.now().astimezone().tzinfo
    windows = outage_windows(entries)
    reminders = build_reminders(entries, windows)
    # Сетка ожидаемых моментов: без неё полностью потерянный будильник невидим для журнала.
    grid = expected_schedule(entries, zone, parse_times(args.times), args.per_slot)
    if grid:
        reminders = sorted(reminders + lost_reminders(entries, grid, windows), key=lambda row: row["scheduled_at"])
    summary = summarize(reminders, windows)
    marks = read_marks(args.marks, zone)
    scenarios = scenario_breakdown(reminders, entries, marks)

    share = summary["on_time_share"]
    # Запас в сутки: первый и последний дни прогона могут содержать не все слоты
    # (установка, выгрузка), а окно считается по плановым моментам.
    span_ok = (summary["span_days"] or 0) >= GATE_DAYS - 1
    gate_passed = bool(share is not None and share >= GATE_SHARE and span_ok)
    os.makedirs(args.out_dir, exist_ok=True)
    results = {
        "generated_at": datetime.now(tz=timezone.utc).isoformat(timespec="seconds"),
        "platform": args.platform,
        "run_kind": args.run_kind,
        "gate": {
            "requirement": f"НАП-3 / НФТ-1: не менее {GATE_SHARE * 100:.0f} % вовремя за {GATE_DAYS} суток непрерывно",
            "on_time_share": share,
            "threshold": GATE_SHARE,
            "share_at_or_above_threshold": share is not None and share >= GATE_SHARE,
            "span_days": summary["span_days"],
            "span_covers_gate": span_ok,
            "numbers_support_continuation": gate_passed,
            "closed_by_this_run": False,
            "why": "гейт объявляет человек по условиям прогона: 14 суток непрерывно, настроенный эталон, "
                   "зафиксированная прошивка; скрипт называет числа и не заменяет решение",
        },
        "summary": summary,
        "reminders": reminders,
        "outage_windows": windows,
        "scenarios": scenarios,
        "journal_entries": len(entries),
    }
    json_path = os.path.join(args.out_dir, "results.json")
    with open(json_path, "w", encoding="utf-8") as handle:
        json.dump(results, handle, ensure_ascii=False, indent=2)
    text_path = os.path.join(args.out_dir, "summary.txt")
    report = human(summary, reminders, windows, scenarios, args.platform, args.run_kind)
    with open(text_path, "w", encoding="utf-8") as handle:
        handle.write(report)
    print(report)
    print(f"машинно читаемый результат: {json_path}")


if __name__ == "__main__":
    main()
