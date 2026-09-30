#!/usr/bin/env python3
"""Расчёт чисел личного пилота (SKL-03, решение ведущей сессии 30.09.2026).

Зачем рядом с `gate.py`. `gate.py` считает формальный стенд: синтетическая нагрузка
(пять лекарств, восемь времён), сетка ожидаемых моментов и разбивка по сценариям
И-1 … И-16. Личный пилот устроен иначе: заказчик пользуется релизной сборкой как обычным
приложением, а метрики считаются по выгруженному им CSV. Синтетической сетки здесь нет
и быть не может — расписание у человека своё. Поэтому ожидаемые моменты инструмент берёт
**из самого журнала**: регулярные серии (лекарство + время суток), а недостающие внутри
серии приёмы считает несостоявшимися.

Вход — CSV из «Диагностика напоминаний» → «Выгрузить журнал»
(`ReminderJournalCsv`: моменты ISO-8601 UTC, заголовок — имена полей журнала, порядок
строк в выгрузке — по убыванию времени; здесь строки сортируются). Копия базы не
принимается: у личного пилота она недоступна, а резервный путь гейта — `gate.py --db`.

Что считается (по каждому приёму журнала):

* `T0` — плановое время (`scheduled_at`), `T1` — первая запись `fired`, `T2` — первое
  предъявление (`shown`/`repeated`); отклонение показа — `T2 − T0` (НАП-2);
* классы: **вовремя** (0…+120 с), **опоздание** (> +120 с), **раньше плана** (< 0 —
  дефект планирования), **не сработало** (нет `fired`/`shown`: показ подавлен приложением
  `suppressed` либо пропуск в регулярной серии), **сработало, но не показано**
  (`fired` без `shown` и `suppressed` — тихий отказ);
* перерывы: окна от предыдущей записи до `rescheduled`/`boot` или до записи `outage`;
  приёмы внутри окон исключаются из знаменателя отдельным числом (правило TDD §6.3),
  а число несостоявшихся приёмов, названное приложением в `outage` (`alarms_scheduled`),
  приводится рядом; пересечение окна и первой несостоявшейся точки не удваивается;
* распределение отклонений: медиана, p90, p99, минимум, максимум; выбросы, пропуски и
  перерывы — списком с датами для ручного разбора; полный список — в `results.json`.

Чего инструмент не делает: не объявляет гейт пройденным (решение принимает человек по
условиям прогона), не видит полностью потерянный будильник, если на нём оборвалась серия
лекарства (у такого срабатывания нет ни одной записи, и «хвост» регулярности до
следующего показа не восстанавливается), и не восстанавливает пропуски в сериях, где
интервалы не кратны минимальному (нерегулярное расписание — сообщается числом).

Вывод — `summary.txt` и `results.json` в `--out-dir`.
"""

from __future__ import annotations

import argparse
import json
import os
import statistics
import sys
from datetime import datetime, timedelta, timezone

# Соседний файл оснастки: формулы и читатель CSV те же, что у гейта, — одним местом.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gate import (  # noqa: E402
    ON_TIME_TO,
    in_any_window,
    moment_text,
    outage_windows,
    percentile,
    read_csv_journal,
    to_int,
)

# События, по которым виден план приёма: всё, что указывает на его существование.
PLANNED_EVENTS = ("fired", "shown", "repeated", "suppressed")

# Сколько строк категории печатать в summary.txt; полный список — в results.json.
MAX_LINES = 20


def positive_int(value) -> int:
    parsed = to_int(value)
    return parsed if parsed is not None and parsed > 0 else 1


def build_rows(entries: list) -> tuple:
    """Одна строка на приём: T0, T1, T2, отклонение, класс (см. docstring модуля)."""
    by_intake: dict = {}
    suppressed_without_intake = 0
    for entry in entries:
        event = entry["event"]
        if event not in PLANNED_EVENTS:
            continue
        intake_id = entry["intake_id"]
        if intake_id is None:
            if event == "suppressed":
                suppressed_without_intake += 1
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
                "inferred": False,
            },
        )
        for field in ("medication_id", "profile_id"):
            if row[field] is None and entry[field] is not None:
                row[field] = entry[field]
        if entry["scheduled_at"] is not None:
            row["scheduled_at"] = entry["scheduled_at"]
        if event == "fired" and row["fired_at"] is None:
            row["fired_at"] = entry["fired_at"] or entry["occurred_at"]
        elif event in ("shown", "repeated"):
            show_at = entry["fired_at"] or entry["occurred_at"]
            if row["shown_at"] is None or show_at < row["shown_at"]:
                row["shown_at"] = show_at
                row["channel"] = entry["channel"]
                row["presented_attempt"] = entry["attempt"]
            else:
                row["repeat_shows"] += 1
        elif event == "suppressed" and row["suppressed_at"] is None:
            row["suppressed_at"] = entry["occurred_at"]
            row["suppress_reason"] = entry["reason"]

    rows = list(by_intake.values())
    for row in rows:
        finalize_row(row)
    rows.sort(key=lambda item: (item["scheduled_at"] or 0, item["intake_id"]))
    return rows, suppressed_without_intake


def finalize_row(row: dict) -> None:
    """Отклонения и класс приёма; неполная запись не роняет расчёт, а помечается."""
    scheduled = row["scheduled_at"]
    row["deviation_seconds"] = None
    row["control_deviation_seconds"] = None
    row["present_delay_seconds"] = None
    if scheduled is not None and row["shown_at"] is not None:
        row["deviation_seconds"] = (row["shown_at"] - scheduled) / 1000.0
    if scheduled is not None and row["fired_at"] is not None:
        row["control_deviation_seconds"] = (row["fired_at"] - scheduled) / 1000.0
    if row["deviation_seconds"] is not None and row["control_deviation_seconds"] is not None:
        row["present_delay_seconds"] = round(
            row["deviation_seconds"] - row["control_deviation_seconds"], 3
        )
    row["during_outage"] = False
    if row["shown_at"] is not None:
        deviation = row["deviation_seconds"]
        if deviation is None:
            row["kind"] = "unresolved"
        elif deviation < 0:
            row["kind"] = "early"
        elif deviation <= ON_TIME_TO:
            row["kind"] = "on_time"
        else:
            row["kind"] = "late"
    elif row["suppressed_at"] is not None:
        row["kind"] = "suppressed"
    elif row["fired_at"] is not None:
        row["kind"] = "silent_failure"
    else:
        row["kind"] = "missing"


def inferred_row(medication_id, profile_id, moment: int) -> dict:
    row = {
        "intake_id": None,
        "medication_id": medication_id,
        "profile_id": profile_id,
        "scheduled_at": moment,
        "fired_at": None,
        "shown_at": None,
        "suppressed_at": None,
        "suppress_reason": None,
        "channel": None,
        "presented_attempt": None,
        "repeat_shows": 0,
        "inferred": True,
    }
    row["intake_id"] = "missing@" + moment_text(moment)
    finalize_row(row)
    return row


def infer_missing(rows: list, zone) -> tuple:
    """Пропуски внутри регулярных серий: лекарство + время суток.

    Регулярность выводится из самого журнала: интервал серии — минимальный из
    наблюдённых, пропуском считается только разрыв, кратный ему (день, два, три).
    Серия с некратными интервалами не разбирается вовсе: расписание мог поменять
    человек, и «восстанавливать» в нём пропуски значило бы выдумывать план.

    Пропуск в конце серии не восстанавливается: у полностью потерянного будильника
    нет ни одной записи, а до следующего показа «хвост» серии неизвестен.
    """
    groups: dict = {}
    for row in rows:
        if row["scheduled_at"] is None or row["medication_id"] is None:
            continue
        local = datetime.fromtimestamp(row["scheduled_at"] / 1000, tz=zone)
        key = (row["medication_id"], row["profile_id"], local.hour, local.minute)
        group = groups.setdefault(key, {"moments": set(), "dates": set()})
        group["moments"].add(row["scheduled_at"])
        group["dates"].add(local.date())

    inferred: list = []
    irregular = 0
    for (medication_id, profile_id, hour, minute), group in groups.items():
        dates = sorted(group["dates"])
        if len(dates) < 2:
            continue
        gaps = [(dates[index + 1] - dates[index]).days for index in range(len(dates) - 1)]
        if any(gap <= 0 for gap in gaps):
            continue
        step = min(gaps)
        if any(gap % step for gap in gaps):
            irregular += 1
            continue
        for index, gap in enumerate(gaps):
            for multiple in range(1, gap // step):
                date = dates[index] + timedelta(days=multiple * step)
                try:
                    moment = datetime(date.year, date.month, date.day, hour, minute, tzinfo=zone)
                except ValueError:
                    continue
                moment_ms = int(moment.timestamp() * 1000)
                if moment_ms in group["moments"]:
                    continue
                inferred.append(inferred_row(medication_id, profile_id, moment_ms))
    inferred.sort(key=lambda row: row["scheduled_at"])
    return inferred, irregular


def read_outage_records(entries: list) -> list:
    """Записи `outage`: приложение само назвало первый несостоявшийся момент и число."""
    records = []
    for entry in entries:
        if entry["event"] != "outage":
            continue
        records.append(
            {
                "at": entry["occurred_at"],
                "since": entry["scheduled_at"] or entry["occurred_at"],
                "missed": positive_int(entry["alarms_scheduled"]),
                "reason": entry["reason"],
            }
        )
    records.sort(key=lambda record: record["at"])
    return records


def outage_missed_total(excluded: list, records: list) -> int:
    """Сколько приёмов не состоялось за перерывы, без двойного счёта.

    Моменты, видимые в журнале (исключённые строки), считаются поимённо; запись
    `outage` добавляет свой первый момент, если строки на него нет, и довесок
    `missed − 1` — моменты за ним журнал не называет, и это отмечено в сводке.
    """
    moments = {row["scheduled_at"] for row in excluded if row["scheduled_at"] is not None}
    extras = 0
    for record in records:
        since = record["since"]
        missed = record["missed"]
        if since not in moments:
            moments.add(since)
        extras += max(0, missed - 1)
    return len(moments) + extras


def apply_period(rows: list, from_ms, to_ms) -> list:
    if from_ms is None and to_ms is None:
        return rows
    return [
        row
        for row in rows
        if row["scheduled_at"] is not None
        and (from_ms is None or row["scheduled_at"] >= from_ms)
        and (to_ms is None or row["scheduled_at"] <= to_ms)
    ]


def parse_boundary(text: str, zone, is_end: bool):
    """Граница периода: `ГГГГ-ММ-ДД` (для `--to` — включая день) или ISO-8601."""
    if not text:
        return None
    try:
        moment = datetime.fromisoformat(text)
    except ValueError:
        raise SystemExit(f"не разобрать границу периода: {text!r}")
    if moment.tzinfo is None:
        if "T" in text or " " in text:
            moment = moment.replace(tzinfo=zone)
        else:
            moment = datetime(moment.year, moment.month, moment.day, tzinfo=zone)
            if is_end:
                moment += timedelta(days=1)
    return int(moment.timestamp() * 1000)


def share(count: int, total: int):
    return (count / total) if total else None


def summarize(rows: list, excluded: list, windows: list, records: list, irregular: int,
              suppressed_without_intake: int, unresolved: int) -> dict:
    counted = [row for row in rows if not row["during_outage"] and row["scheduled_at"] is not None]
    deviations = [row["deviation_seconds"] for row in counted if row["deviation_seconds"] is not None]
    late_values = [
        row["deviation_seconds"] for row in counted if row["kind"] == "late"
    ]
    kinds = ("on_time", "late", "early", "suppressed", "missing", "silent_failure")
    counts = {kind: sum(1 for row in counted if row["kind"] == kind) for kind in kinds}
    suppressed_reasons: dict = {}
    for row in counted:
        if row["kind"] == "suppressed" and row["suppress_reason"]:
            suppressed_reasons[row["suppress_reason"]] = suppressed_reasons.get(row["suppress_reason"], 0) + 1
    total = len(counted)
    scheduled_moments = [row["scheduled_at"] for row in rows if row["scheduled_at"] is not None]
    return {
        "planned": len(rows),
        "unresolved": unresolved,
        "excluded_for_outage": len(excluded),
        "counted": total,
        "on_time": counts["on_time"],
        "late": counts["late"],
        "early": counts["early"],
        "suppressed": counts["suppressed"],
        "missing": counts["missing"],
        "not_delivered": counts["suppressed"] + counts["missing"],
        "silent_failures": counts["silent_failure"],
        "suppressed_without_intake": suppressed_without_intake,
        "suppressed_reasons": suppressed_reasons,
        "shares": {
            "on_time": share(counts["on_time"], total),
            "late": share(counts["late"], total),
            "early": share(counts["early"], total),
            "not_delivered": share(counts["suppressed"] + counts["missing"], total),
            "silent_failure": share(counts["silent_failure"], total),
        },
        "repeated_presentations": sum(row["repeat_shows"] for row in counted),
        "deviation_seconds": {
            "median": statistics.median(deviations) if deviations else None,
            "p90": percentile(deviations, 0.90),
            "p99": percentile(deviations, 0.99),
            "min": min(deviations) if deviations else None,
            "max": max(deviations) if deviations else None,
        },
        "late_seconds": {
            "median": statistics.median(late_values) if late_values else None,
            "max": max(late_values) if late_values else None,
        },
        "outage_windows": len(windows),
        "outage_seconds": sum(window["seconds"] for window in windows),
        "outage_records": len(records),
        "outage_records_missed": sum(record["missed"] for record in records),
        "outage_missed_total": outage_missed_total(excluded, records),
        "irregular_series": irregular,
        "first_scheduled_at": min(scheduled_moments) if scheduled_moments else None,
        "last_scheduled_at": max(scheduled_moments) if scheduled_moments else None,
        "span_days": (
            (max(scheduled_moments) - min(scheduled_moments)) / 86_400_000.0
            if scheduled_moments
            else None
        ),
    }


def pick(rows: list, kind: str) -> list:
    return [row for row in rows if row["kind"] == kind]


def outliers(rows: list, excluded: list) -> dict:
    # Категории разбора — без строк, исключённых за перерыв: они показаны отдельно,
    # и один и тот же приём не должен читаться как два разных повода.
    counted = [row for row in rows if not row["during_outage"]]
    late = sorted(pick(counted, "late"), key=lambda row: row["deviation_seconds"], reverse=True)
    early = sorted(pick(counted, "early"), key=lambda row: row["deviation_seconds"])
    return {
        "late": late,
        "early": early,
        "silent_failure": pick(counted, "silent_failure"),
        "suppressed": pick(counted, "suppressed"),
        "missing": pick(counted, "missing"),
        "unresolved": pick(counted, "unresolved"),
        "outage": excluded,
    }


def seconds(value) -> str:
    if value is None:
        return "—"
    return f"{value:.1f}"


def row_line(row: dict) -> str:
    deviation = row["deviation_seconds"]
    return (
        f"{moment_text(row['scheduled_at'])}  приём {row['intake_id']}  "
        f"лекарство {row['medication_id'] or '—'}  отклонение {seconds(deviation)} с"
    )


def human(summary: dict, marks: dict, windows: list, records: list, platform: str,
          run_kind: str, source: str, entries: int, irregular: int) -> str:
    def share_text(key: str) -> str:
        value = summary["shares"][key]
        return "нет данных" if value is None else f"{value * 100:.1f} %"

    span = summary["span_days"]
    span_text = "нет данных" if span is None else f"{span:.2f} суток"
    deviation = summary["deviation_seconds"]
    lines = [
        f"Личный пилот напоминаний: {run_kind}",
        f"Платформа: {platform or 'не записана (зафиксируйте модель, EMUI и Android — НАП-40)'}",
        f"Источник: {source} — записей журнала {entries}",
        f"Период по плановым моментам: {moment_text(summary['first_scheduled_at'])} … "
        f"{moment_text(summary['last_scheduled_at'])} ({span_text})",
        "",
        f"Приёмов в расчёте:           {summary['planned']}",
        f"  исключено за перерыв:      {summary['excluded_for_outage']}",
        f"  в знаменателе доли:        {summary['counted']}",
        "",
        f"Вовремя (0 … +120 с):        {summary['on_time']:>4} — {share_text('on_time')}",
        f"Опоздание (> +120 с):        {summary['late']:>4} — {share_text('late')} "
        f"(медиана {seconds(summary['late_seconds']['median'])} с, "
        f"максимум {seconds(summary['late_seconds']['max'])} с)",
        f"Раньше плана (меньше 0):     {summary['early']:>4} — {share_text('early')}",
        f"Не дошло до показа:          {summary['not_delivered']:>4} — {share_text('not_delivered')}",
        f"  подавлено приложением:     {summary['suppressed']:>4}",
        f"  пропущено (нет fired/shown): {summary['missing']:>3}",
        f"Сработало, но не показано:   {summary['silent_failures']:>4} — {share_text('silent_failure')}",
    ]
    if summary["unresolved"]:
        lines.append(f"Записей без планового времени (вне знаменателя): {summary['unresolved']}")
    if summary["suppressed_reasons"]:
        reasons = ", ".join(f"{key}:{value}" for key, value in sorted(summary["suppressed_reasons"].items()))
        lines.append(f"Причины подавления показа: {reasons}")
    lines += [
        "",
        "Отклонения предъявлений, секунды:",
        f"  медиана {seconds(deviation['median'])}  p90 {seconds(deviation['p90'])}  "
        f"p99 {seconds(deviation['p99'])}  минимум {seconds(deviation['min'])}  "
        f"максимум {seconds(deviation['max'])}",
        "",
        f"Перерывы: {summary['outage_windows']} окон на {summary['outage_seconds']} с; "
        f"в них приёмов (исключено из знаменателя): {summary['excluded_for_outage']}; "
        f"приложение назвало несостоявшимися: {summary['outage_records_missed']}",
    ]
    for window in windows:
        lines.append(
            f"  {moment_text(window['from'])} … {moment_text(window['to'])} "
            f"({window['kind']}, {window['seconds']} с)"
        )
    for record in records:
        lines.append(
            f"  обнаружение приложением: {moment_text(record['at'])}, "
            f"первый несостоявшийся момент {moment_text(record['since'])}, "
            f"пропущено {record['missed']}"
        )
    lines += ["", "Разбор (полный список — в results.json):"]
    sections = (
        ("Опоздания", marks["late"]),
        ("Ранние срабатывания (дефект планирования)", marks["early"]),
        ("Сработало, но не показано", marks["silent_failure"]),
        ("Пропуски в регулярных сериях (нет fired/shown)", marks["missing"]),
        ("Показ подавлен приложением", marks["suppressed"]),
        ("Исключено за перерыв", marks["outage"]),
    )
    empty = True
    for title, rows in sections:
        if not rows:
            continue
        empty = False
        lines.append(f"  {title}: {len(rows)}")
        for row in rows[:MAX_LINES]:
            reason = f" — {row['suppress_reason']}" if row["kind"] == "suppressed" else ""
            lines.append(f"    {row_line(row)}{reason}")
        if len(rows) > MAX_LINES:
            lines.append(f"    … ещё {len(rows) - MAX_LINES} в results.json")
    if empty:
        lines.append("  выбросов и пропусков нет")
    if irregular:
        lines.append(
            f"  Серий с нерегулярными интервалами: {irregular} — пропуски в них не восстанавливались, "
            "проверьте расписание этих лекарств вручную."
        )
    lines += [
        "",
        "ОГРАНИЧЕНИЯ РАСЧЁТА",
        "Полностью потерянный будильник виден, только если следующий приём той же серии дошёл:",
        "серия на пропуске оборвалась — восстанавливать нечего, а записи о срабатывании нет вовсе.",
        "«Пропуски в регулярных сериях» — восстановленные ожидания, а не подтверждённые отказы:",
        "проверьте их по датам (смена расписания, пауза курса, отпуск) и отметьте в отчёте.",
        "",
        "УСЛОВИЯ РЕШЕНИЯ ПО ГЕЙТУ",
        "Порог НАП-3/НФТ-1 — не менее 95 % вовремя по НАП-2 и не менее 14 суток непрерывного",
        "прогона на настроенном эталоне. Скрипт называет числа; гейт объявляет человек —",
        "ведущая сессия по этому отчёту. Формальный стенд остаётся запасным вариантом.",
    ]
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Расчёт чисел личного пилота по выгруженному журналу (SKL-03)"
    )
    parser.add_argument("--csv", required=True, help="выгруженный журнал (sklyanka-reminder-journal-*.csv)")
    parser.add_argument("--platform", default="", help="строка платформы: модель, EMUI, Android (НАП-40)")
    parser.add_argument("--run-kind", default="личный пилот, Huawei Pura 80 Ultra (EMUI 15 / AOSP 12)")
    parser.add_argument("--zone", default="", help="часовой пояс устройства (IANA); по умолчанию — местный пояс машины")
    parser.add_argument("--from", dest="period_from", help="начало периода: ГГГГ-ММ-ДД или ISO-8601")
    parser.add_argument("--to", dest="period_to", help="конец периода: ГГГГ-ММ-ДД (включая день) или ISO-8601")
    parser.add_argument(
        "--no-inference",
        action="store_true",
        help="не восстанавливать пропуски в регулярных сериях (только записи журнала)",
    )
    parser.add_argument("--out-dir", default="build/spike-personal", help="каталог summary.txt и results.json")
    args = parser.parse_args()

    entries = read_csv_journal(args.csv)
    if not entries:
        raise SystemExit("журнал пуст: расчёт невозможен")
    entries.sort(key=lambda entry: entry["occurred_at"])
    if args.zone:
        from zoneinfo import ZoneInfo

        zone = ZoneInfo(args.zone)
    else:
        zone = datetime.now().astimezone().tzinfo

    windows = outage_windows(entries)
    rows, suppressed_without_intake = build_rows(entries)
    rows = apply_period(
        rows,
        parse_boundary(args.period_from, zone, is_end=False),
        parse_boundary(args.period_to, zone, is_end=True),
    )
    unresolved = sum(1 for row in rows if row["kind"] == "unresolved")
    irregular = 0
    if not args.no_inference:
        inferred, irregular = infer_missing(rows, zone)
        rows = rows + inferred
    rows.sort(key=lambda item: (item["scheduled_at"] or 0, item["intake_id"]))
    for row in rows:
        row["during_outage"] = in_any_window(row["scheduled_at"], windows)
    excluded = [row for row in rows if row["during_outage"]]
    records = read_outage_records(entries)
    summary = summarize(
        rows, excluded, windows, records, irregular, suppressed_without_intake, unresolved
    )
    marks = outliers(rows, excluded)

    os.makedirs(args.out_dir, exist_ok=True)
    results = {
        "generated_at": datetime.now(tz=timezone.utc).isoformat(timespec="seconds"),
        "run_kind": args.run_kind,
        "platform": args.platform,
        "source": {
            "csv": os.path.abspath(args.csv),
            "journal_entries": len(entries),
            "zone": str(zone),
        },
        "summary": summary,
        "outliers": marks,
        "reminders": rows,
        "outage_windows": windows,
        "outage_records": records,
        "notes": [
            "синтетической сетки нет: ожидаемые моменты восстановлены из регулярных серий журнала",
            "пропуск в конце серии не виден: у полностью потерянного будильника нет ни одной записи",
            "пропуски в регулярных сериях — ожидания, а не подтверждённые отказы: проверяются по датам",
            "гейт объявляет человек; скрипт называет числа и условия",
        ],
    }
    json_path = os.path.join(args.out_dir, "results.json")
    with open(json_path, "w", encoding="utf-8") as handle:
        json.dump(results, handle, ensure_ascii=False, indent=2)
    report = human(
        summary, marks, windows, records, args.platform, args.run_kind,
        os.path.abspath(args.csv), len(entries), irregular,
    )
    text_path = os.path.join(args.out_dir, "summary.txt")
    with open(text_path, "w", encoding="utf-8") as handle:
        handle.write(report)
    print(report)
    print(f"машинно читаемый результат: {json_path}")


if __name__ == "__main__":
    main()
