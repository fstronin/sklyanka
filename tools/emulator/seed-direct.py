#!/usr/bin/env python3
"""Синтетическая нагрузка прямо в базу приложения (SKL-27).

Зачем помимо файла переноса. Загрузка файла требует системного выбора файлов, а его
на образе может не быть вовсе: `aosp_atd` — урезанный под автотесты образ, и
`DocumentsUI` в него не входит. На таком образе единственный описанный путь завести
курс не работает, и прогон останавливается на шаге, который делается руками.

Что делает. Пишет в снятую копию базы одно лекарство по расписанию и приёмы на
ближайшие часы. Будильники **не ставятся здесь**: их ставит сам продукт, когда
пересобирает окно после запуска. Проверяется, таким образом, настоящий планировщик,
а не подделка.

Чего НЕ делает: не пишет ни одной записи о срабатывании. Журнал напоминаний и записи
откладываний от прежних прогонов счищаются вместе с нагрузкой: их часы двигались в обе
стороны, и их записи лежат под чужими отметками времени. Числа гейта считаются по тому,
что запишет приложение.

Медицинских данных в нагрузке нет (НФТ-19): названия вида «Стенд 1».
"""

import argparse
import datetime
import sqlite3
import uuid

MEDICATION_NAME = "Стенд"
NAMESPACE = uuid.UUID("00000000-0000-4000-8000-000000000000")


def identifier(seed: str) -> str:
    return str(uuid.uuid5(NAMESPACE, seed))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", required=True, help="снятая копия базы приложения")
    parser.add_argument("--now", type=int, required=True, help="время образа, секунды эпохи")
    parser.add_argument("--zone", required=True, help="часовой пояс образа")
    parser.add_argument("--medications", type=int, default=1)
    parser.add_argument("--times", type=int, default=8, help="сколько приёмов на лекарство")
    parser.add_argument("--step-minutes", type=int, default=6)
    parser.add_argument("--first-in-minutes", type=int, default=3)
    arguments = parser.parse_args()

    try:
        import zoneinfo

        tz = zoneinfo.ZoneInfo(arguments.zone)
    except Exception:  # noqa: BLE001 — пояс образа может быть неизвестен машине прогона
        tz = datetime.timezone.utc

    now = datetime.datetime.fromtimestamp(arguments.now, tz)
    moments = [
        (now + datetime.timedelta(minutes=arguments.first_in_minutes + arguments.step_minutes * index)).replace(
            second=0, microsecond=0
        )
        for index in range(arguments.times)
    ]

    connection = sqlite3.connect(arguments.db)
    owner = connection.execute("SELECT id FROM profile WHERE is_self = 1").fetchone()
    if owner is None:
        raise SystemExit("в базе нет своего профиля: приложение ни разу не запускалось")
    owner = owner[0]

    # Нагрузка заменяет прежнюю целиком: два прогона подряд не должны складываться.
    # Журнал напоминаний чистится вместе с нагрузкой: испытания двигают часы образа
    # в обе стороны, и записи прошлых прогонов остаются в журнале под чужими, в том
    # числе будущими, отметками времени. Чистый журнал делает каждый прогон независимым,
    # а числами по-прежнему считается только то, что запишет сам продукт.
    #
    # Внешние ключи включаются, и `deferred_reminder` чистится явно (SKL-27, пункт 39):
    # идентификаторы приёмов детерминированы, и пережившая пересев запись откладывания
    # «угоняет» новый приём — будильник встаёт по прошлому моменту. Каскад снёс бы её
    # вместе с приёмом, но только пока внешние ключи были включены; у сирот прошлых
    # прогонов приёма уже нет, и каскаду нечего сносить — нужна прямая чистка.
    connection.execute("PRAGMA foreign_keys = ON")
    connection.execute("DELETE FROM deferred_reminder")
    connection.execute("DELETE FROM reminder_log")
    connection.execute("DELETE FROM intake")
    connection.execute("DELETE FROM medication")
    for index in range(arguments.medications):
        medication = identifier(f"medication-{index}")
        connection.execute(
            "INSERT INTO medication (id, profile_id, name, form, shape, dose_amount, dose_unit, strength,"
            " strength_unit, food_relation, schedule_mode, max_per_day, times, start_date, end_date, stock_left,"
            " stock_threshold_days, archived_at, archive_reason)"
            " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (
                medication,
                owner,
                f"{MEDICATION_NAME} {index + 1}",
                "TABLET",
                "ROUND",
                "1",
                "шт",
                None,
                None,
                "ANY",
                "SCHEDULED",
                None,
                ",".join(moment.strftime("%H:%M") for moment in moments),
                now.date().isoformat(),
                None,
                None,
                None,
                None,
                None,
            ),
        )
        for position, moment in enumerate(moments):
            connection.execute(
                "INSERT INTO intake (id, medication_id, scheduled_at, schedule_slot, status, acted_at, source,"
                " actor_profile_id) VALUES (?,?,?,?,?,?,?,?)",
                (
                    identifier(f"intake-{index}-{position}"),
                    medication,
                    moment.strftime("%Y-%m-%dT%H:%M"),
                    moment.strftime("%H:%M"),
                    "PENDING",
                    None,
                    None,
                    None,
                ),
            )
    connection.commit()
    connection.close()
    print(
        f"нагрузка: {arguments.medications} лекарств(а), приёмы "
        f"{', '.join(moment.strftime('%H:%M') for moment in moments)} по часам образа"
    )


if __name__ == "__main__":
    main()
