# Behavior

Behavior specification: data, schedule and intake, reminders, permissions, screens, profiles, transfer. Rules, timings, thresholds, formats, and limits needed to verify the product. Interface language: Russian; Russian labels are quoted once with a gloss.

Terms. **Intake**: scheduled dose event; **intake record**: its row. **Mark**: taken, skipped (deliberate refusal), or missed (no mark); unmarked means pending. **Course**: medication with its schedule; **PRN** ("as needed") has no times. **Stock**: package remainder; **adherence** is derived. **Profile** owns a medication set; **owner profile** belongs to the phone owner, **foreign profile** to someone else. **Transfer**: export/import file. **Reminder** notifies about an intake; **exact alarm** delivers it. **Deferral**: snooze; **persistent reminder channel** carries repeats; **delivery self-check**: user-triggered test; **report for the doctor**: PDF. **Widget**: home screen plate; **reliability onboarding**: permission walkthrough. **Dark environment**: reminder surface; **accent theme**: user color choice; **design tokens**: shared style values.

Manual intake journal with reminders: no treatment advice, no dose selection, no interaction checks, no AI input. Not a medical device.

---

## 1. Data

Entities: **Profile** (who), **Medication** (what and when), **Intake** (what happened). Adherence, stock, and course progress are derived, never stored. Profile 1 to N Medication 1 to N Intake. An intake has no profile field; its profile comes through its medication. `actor_profile_id` records who marked when that was not the medication's profile.

Storage: `sklyanka.db`, schema 12, tables `profile`, `medication`, `intake`, service tables `op_log`, `reminder_log`, `deferred_reminder`.

### 1.1 Entity fields

**`profile`**

| Field | Type | Rule |
|---|---|---|
| `id` | TEXT, key | String; numeric keys collide on merge |
| `name` | TEXT | Trimmed non-empty; shown as «Профиль · Имя» ("Profile · Name") and in the report |
| `color` | INTEGER (ARGB) | Closed palette; stripe, header tint, foreign accent |
| `is_self` | INTEGER | Owner; exactly one |
| `mirror_reminders` | INTEGER | Ring this profile's reminders on the owner's phone; meaningless for the owner; default on |
| `self_slot` | TEXT, NULL | `self` on the owner; unique index holds the single owner |

**`medication`**

| Field | Type | Rule |
|---|---|---|
| `id` | TEXT, key | String |
| `profile_id` | TEXT, FK RESTRICT | Immutable |
| `name` | TEXT | Person-entered, never checked against a drug reference; trimmed non-empty |
| `form` | TEXT | Decides whether `shape` applies and the dose unit; default tablet |
| `shape` | TEXT, NULL | Tablet silhouette only |
| `dose_amount` | TEXT (decimal) | Above zero; deducted on taken; no default |
| `dose_unit` | TEXT | Closed six from the form: tablet, capsule, millilitre (syrup), drop (drops), spray, injection |
| `strength` | TEXT, NULL | Display only |
| `strength_unit` | TEXT, NULL | Closed: mg, mcg, g, ml, IU, %; filled with `strength` |
| `food_relation` | TEXT | Default "any"; no timing effect |
| `schedule_mode` | TEXT | Scheduled (default) or PRN; decides whether `times` and `max_per_day` apply |
| `max_per_day` | INTEGER, NULL | PRN only |
| `times` | TEXT | Sorted `HH:mm` joined by commas; scheduled only, non-empty, no duplicates; local |
| `schedule_rule` | TEXT, NULL | `daily`, `weekdays:MO,WE,FR`, `every:2`, `cycles:21/7`, `staged:0.5x7;1x24`; empty or unreadable means daily |
| `start_date` | TEXT (date) | First day, default today |
| `end_date` | TEXT (date), NULL | Last day inclusive; empty means indefinite; not earlier than start |
| `stock_left` | TEXT, NULL | Dose units; empty means untracked, no warning; never below zero |
| `package_size` | TEXT, NULL | Empty means unset (card shows "N left", no bar); above zero when set; a refill leaves it unchanged |
| `stock_threshold_days` | INTEGER, NULL | Purchase warning in days; only with `stock_left` |
| `archived_at` | INTEGER (ms), NULL | Off the schedule; stays in history and archive |
| `archive_reason` | TEXT, NULL | "Course finished" or "removed manually"; selects the archive group; filled exactly with `archived_at` |
| `deleted_at` | INTEGER (ms), NULL | Soft deletion; never with `archived_at`; only while no taken mark exists |

Index `(profile_id, archived_at)` serves the list, archive, and Today.

**`intake`**

| Field | Type | Rule |
|---|---|---|
| `id` | TEXT, key | Mark idempotency key |
| `medication_id` | TEXT, FK RESTRICT | Immutable; determines the profile |
| `scheduled_at` | TEXT (`YYYY-MM-DDTHH:mm`) | Planned; equals `acted_at` on PRN; local |
| `schedule_slot` | TEXT, NULL | `S` on scheduled, empty on PRN; two PRN intakes in a minute are legal |
| `status` | TEXT | PENDING, TAKEN, SKIPPED, MISSED |
| `acted_at` | INTEGER (ms), NULL | Empty on PENDING, filled otherwise |
| `source` | TEXT, NULL | PHONE or AUTO; filled with `acted_at` |
| `actor_profile_id` | TEXT, NULL, FK RESTRICT | Who marked when not the medication's profile; basis of "marked by you" |
| `revoked_at` | INTEGER (ms), NULL | PRN revoked: status unchanged, invisible in history, stock, day count, daily limit; no row deleted |
| `dose_amount` | TEXT, NULL | Dose when it differs from the medication dose |

Indexes: unique `(medication_id, scheduled_at, schedule_slot)`; `(scheduled_at, status)` for Today and the overdue sweep; `(actor_profile_id)`. No partial index: Room would refuse to open the database.

Service tables. `op_log`: `at`, `event`, ids, before and after values, source, note. `reminder_log`: `occurred_at`, `event`, ids, `scheduled_at`, `fired_at`, `deviation_seconds`, `channel`, `delivered`, `reason`, `alarms_cancelled`, `alarms_scheduled`, `attempt`, `snooze_minutes`; no names, doses, or person names. `deferred_reminder`: `intake_id` key with CASCADE, `fire_at`, `attempt`. No table holds anything but product data.

### 1.2 Enumerations and storage

Enums are stored as string constant names, never ordinals: an ordinal breaks on insertion and is unreadable in a transfer file.

| Enum | Values |
|---|---|
| Dosage form | TABLET, CAPSULE, SPRAY, SYRUP, DROPS, INJECTION |
| Tablet shape | ROUND, ROUND_SCORED, OVAL, OBLONG, TRIANGULAR |
| Food relation | ANY, BEFORE_MEAL, WITH_MEAL, AFTER_MEAL |
| Schedule mode | SCHEDULED, PRN |
| Intake status | PENDING, TAKEN, SKIPPED, MISSED |
| Intake source | PHONE, AUTO |
| Archive reason | COURSE_FINISHED, MANUAL |

Shape applies only to TABLET; a capsule silhouette comes from its form. An earlier record may hold a shape with another form: not migrated, ignored by display, gone on the next save.

| Value kind | Storage |
|---|---|
| Enum | String constant name |
| `times` | Sorted `HH:mm`; empty string is an empty list |
| Date | `YYYY-MM-DD` |
| Local date and time | `YYYY-MM-DDTHH:mm` |
| Moment | Integer ms: `acted_at`, `archived_at`, `deleted_at`, `occurred_at`, `fired_at`, `fire_at`, `op_log.at` |
| Decimal | Text without exponent; never compared in SQL |

FKs are RESTRICT, and the `foreign_keys` pragma is raised on the write connection after the database is built, in production and all builds. A Room callback is unsuitable: `onCreate` and `onOpen` run inside the creation transaction, where the pragma is a no-op.

### 1.3 Time storage

| Field | Storage | Reason |
|---|---|---|
| `times` | Local time, no zone | A rule: after a flight still "8 a.m." |
| `start_date`, `end_date` | Local date, no zone | Calendar bounds |
| `scheduled_at` | Local date and time | Stable day grouping: in UTC, 23:30 would land on the wrong day |
| `acted_at` | UTC moment | Conflict comparison across zones |

Alarm moments are computed in the current zone and recomputed on zone change. Intakes keep their zone; recomputation of the current day is offered, never automatic.

### 1.4 Invariants

Held on every write path: save, transfer validation and application, generation, marking, undo.

- **Single owner**: one profile has `is_self`; unique index on `self_slot` plus first launch.
- **Owner flag inert**: `mirror_reminders` affects nothing for the owner, whose intakes are always scheduled.
- **Fixed ownership**: `medication.profile_id` never changes.
- **Field constraints**: positive dose; strength pair filled together or empty; scheduled times non-empty and unique, PRN empty; course bounds ordered; stock non-negative and clamped; archive pair both or neither; mark fields filled exactly when not pending; package size above zero; delete and archive exclusive; a deleted medication has no taken mark.
- **References**: intake to medication to profile; RESTRICT plus the pragma.
- **No duplicate**: a scheduled medication has no two intakes at one `scheduled_at`; the unique index holds it.
- **PRN shape**: `scheduled_at == acted_at`, status not pending; a mark on a residual intake of an earlier schedule is not judged by the current mode.
- **Inside course**: checked at generation; a live mark and a transfer file are not judged, since dates may change later.
- **Foreign actor**: references an existing profile and differs from the medication's; validated on marking, transfer, file validation.
- **Repeat mark is a no-op**: conditional `WHERE status = 'PENDING'`; no second deduction.
- **Schedule parameters**: exactly the kind's own; non-empty days, interval at least 2, on and off lengths at least 1, non-empty stages; daily has none.
- **Contiguous stages**: each at least one day, dose above zero, the last ending no later than the course end.
- **One kind**: daily, weekdays, every N days, cycles, or stepped; PRN has none.

### 1.5 Domain rules

- **Automatic miss** at window expiry; `source = AUTO`, `acted_at` at the transition.
- **Idempotent mark** by intake id; a repeat changes nothing.
- **Conflict**: the earlier `acted_at` wins, by mark moment, not delivery.
- **PRN birth** at «Принять» ("Take"), `scheduled_at = acted_at`; no pending PRN records.
- **Stock effect**: taken deducts; skipped and missed do not; empty stock is not updated.
- **Indefinite course** generates intakes without an end.
- **Profile scoping**: every product query is limited to the active profile. Service exceptions: the overdue sweep and reminder candidate selection.
- **Archived course**: no intakes, no planning; records stay in history.
- **Foreign actor** is the owner profile when the marker is not the medication's profile.
- **Stock warning** when `stock_left` and `stock_threshold_days` are set and the remainder covers strictly fewer days. PRN and archived courses never warn; zero always warns; the most urgent warning only.
- **Soft deletion**: generates nothing; invisible in archive, history, report, stock warnings; intakes stay but are out of adherence. Requires no taken mark; removes future pending intakes and cancels alarms; return clears `deleted_at` within the undo window.
- **Schedule kinds**: weekdays, selected only, at least one; every N days, N of at least 2, from the start; cycles, N on and M off from the start, for example 21/7; stepped, dose from the stage, times shared.
- **Widget quick mark**: same use case, source PHONE, same idempotency and window.
- **Widget scope**: active profile, following its switch; a foreign active profile carries a compact indication (name and color, no "foreign" or "ward"), the owner none.

### 1.6 Archive, soft deletion, migrations

A medication is never physically deleted. **Archive**: trace of a finished course, in archive, history, report, and adherence, reversible. **Deletion**: a wrongly entered record, hidden everywhere, intakes kept but out of calculations. Physical deletion exists only in the "replace everything" mode.

At `end_date` the app **offers** archiving with "course finished"; the person confirms. "Removed manually" is set when archiving from the card. Restoring with a passed end date requires a period update.

| Requirement | Rule |
|---|---|
| Schema export | Exported, kept, reviewed; a schema change without a new file is rejected |
| No destructive migration | In any build, debug included |
| Migration tests | Each tested on a real database of the previous version |
| Independent versions | Database schema and transfer format versions are independent |
| Future format | Rejected with a clear message, never read partially |
| Unknown enums | In a file: the default plus a notice. In the database: that row fails, not the app |
| Newer database | Not opened, data unchanged: a blocking screen asks to update. Alarms create no marks, show no card, only the update notice |
| Corrupted database | If opening recreates the database while a "database was alive" marker exists (outside the file, in settings), Today shows a one-time non-modal explanation that data was corrupted and reset, with a close action. It does not return until the next recreation; a clean install and a normal opening stay silent; clearing app data erases the marker |

Migrations: 1 to 2 replaces the marker table with the whole schema; 2 to 3 watch mirroring; 3 to 4 the firing log; 4 to 5 `package_size`; 5 to 6 deferred firings; 6 to 7 `deleted_at`; 7 to 8 removes the watch seam; 8 to 9 `revoked_at`; 9 to 10 `schedule_rule` and intake `dose_amount`; 10 to 11 the client event queue; 11 to 12 drops the client event queue. Free space is checked and a copy taken into the no-backup directory with the source version in the name; insufficient space stops the migration with a dedicated screen. A maintenance trace and integrity checks are written at most once per day.

---

## 2. Schedule and intake

### 2.1 Intake generation

Window, not the whole course: **7 days including today**, minimum 3, longer than the alarm horizon.

Only medications with a schedule, not archived, not deleted, with a non-empty time list generate intakes. Past times on the start day are not generated; on other days they become missed.

Plan diff: a record of **any** status occupies its moment; only missing moments are created; only pending intakes absent from the new plan and still future are removed. Past pending intakes stay for the sweep; marked ones are never rewritten. Insertion ignores conflicts.

| Event | Future pending intakes |
|---|---|
| Schedule edit (times, dates, mode, dose) | Recreated |
| Archiving | Removed |
| Restore | Created again |
| Soft deletion | Removed; alarms cancelled |
| Undo of deletion | Created again |

Rebuild triggers: app start, boot, timezone change, time change, medication save, add, archive, restore, delete, undo of delete, anchor alarm, manual check, transfer load.

### 2.2 Statuses, intake window, automatic transition

| Status | Meaning |
|---|---|
| Pending | Time not reached or window open; no mark |
| Taken | Confirmed |
| Skipped (deliberate) | Explicit refusal |
| Missed (no mark) | Window expired unmarked |

The two skips differ in meaning and are never merged in the report.

| Transition | Condition | Effects |
|---|---|---|
| none to pending | Planner created a scheduled intake | None |
| none to taken | «Принять» on PRN | Marked; `scheduled_at = acted_at`; stock deducted |
| pending to taken | «Принято» ("Taken") | `acted_at`, `source = PHONE`, `actor_profile_id`; stock decreases |
| pending to skipped | «Пропустить» ("Skip") | Same fields; stock untouched |
| pending to missed | Window expired | `acted_at` at transition, `source = AUTO`, no actor; no notification |
| taken to revoked (PRN) | «Отменить» ("Undo") in the short window | `revoked_at` filled, status stays taken, dose returns; leaves history, stock, day count, limit |

Marked statuses have no outgoing transitions; the only exception is earlier-mark substitution, which deducts nothing twice.

**Window.** Pending becomes missed at **the end of the day**, 00:00 next day; a domain parameter, not a transition constant. Adherence and notification removal depend on it. A mark before it lands as taken. "On time" is a different window, [−2 h, +2 h], counting scheduled intakes only.

**Sweep.** On a timer, on every opening, and on every reschedule, since the device may have been off. Causes: timer, app start, replan. Removes the shown cards, logs a record.

### 2.3 Marking and its undo

One entry point; source AUTO cannot pass through it. A second mark changes zero rows, returns "ignored", touches no stock. Earlier-mark substitution is a conditional update on `acted_at`.

Undo lives **5 seconds** (corridor 4 to 6): «Отменить» returns the intake to pending, restores the dose, logs a trace, does not re-raise the reminder. History is never edited retroactively. The store holds the window; the scenario does not check time.

PRN undo **revokes** the record by a soft flag and returns the dose. Revocation is PRN-only. Nothing is deleted; the fact is logged without medical data. A revoked record is invisible in history and stock, generates no reminders, is out of the PRN day count, does not block deletion. A repeat changes zero rows.

### 2.4 Package stock

Taken decreases stock; undo of taken restores it; other statuses leave it. Stock clamps at zero and the clamp is kept. Empty stock is not updated.

"Lasts N days" uses dose times times per day; PRN is undefined and hidden. Days are the integer part, no ceiling. Threshold per medication: 3, 5, 7, 10, or 14 days, default 7; turning the reminder off clears it. A refill replaces the remainder and leaves the package size; a negative value is rejected.

---

## 3. Reminders

### 3.1 Priority and criterion

- **Delivery first**: reminders outrank every other requirement.
- **Fired means presented**: a full-screen card or a heads-up with sound and vibration completed. Setting an alarm, a broadcast without presentation, and a silent collapsed presentation do not count. On time: `fired_at − scheduled_at` from 0 to +120 s; earlier than zero is a planning defect.
- **Reliability gate**: at least 95 percent on time across a continuous 14-day run.
- **Local log** of planned and actual time, medication, channel, and presentation; feeds the gate and diagnostics, stays on the device. Self-check records never count in the gate.

### 3.2 Mechanism, window, rescheduling

| Rule | Content |
|---|---|
| Mechanism | Intake reminders and repeats use `setAlarmClock` with a show intent; service alarms (anchor, day boundary) use `setExactAndAllowWhileIdle` and never reach the system indicator. No inexact or repeating alarms |
| Window | Forward only, 48 hours |
| Anchor | Daily 03:17 local, nearest moment strictly after now; rebuilds the window even if nothing fired and the app stayed closed |
| Idempotence | Same data, same set; alarms addressed by intake id; a repeat replaces; only changed moments move |
| PRN | No reminders |
| Profile switch | Changes no alarm by itself; the set comes from the mirroring flag |
| Execution | Background within the receiver budget; long work in `WorkManager`; no constraints; expedited only on API 31 and above |
| Logging | Time, reason, cancelled and scheduled counts. Pre-unlock recovery drains its trace after unlock with the reason "pre-unlock recovery" |
| System indicator | Only the nearest intake reminder; service alarms never reach it; it cannot be hidden while staying on `setAlarmClock`; explained in the exact alarm step and the next-reminder block; an indicator on the night anchor is forbidden |

Rescheduling runs on: boot (on EMUI only after unlock), locked boot from the snapshot, first unlock (full rebuild), package replaced, timezone or time change, exact alarm grant, medication save, add, archive, restore, delete, undo of delete, every fired reminder, profile set or flag change, the anchor, a transfer load, and app start.

### 3.3 Pre-unlock recovery

An **alarm snapshot** mirrors set alarms in storage available before the unlock code, updated where alarms are set and cancelled. A record holds intake id, absolute moment, show number; no medication names, doses, person names, or profile ids. Before unlock no notification carries medical data.

The whole snapshot is discarded on a foreign format version, the record ceiling, an age beyond the planning horizon, or a future timestamp beyond the allowance. Records are skipped on an empty address, a show number out of range, or a passed moment. Partial restore is forbidden: wrong alarms are worse than none.

Degradation is safe and explicit: no snapshot, corruption, staleness, or a foreign version means no alarms, no crash, and the attempt trace reaches the log after unlock. The rebuild is senior and cancels everything diverging from the plan.

### 3.4 Notification channels

Two static channels, created once at first launch with permanent ids, never recreated. No per-medication sound.

| Parameter | Regular | Persistent |
|---|---|---|
| Identifier | `reminders_regular_v2` | `reminders_persistent` |
| Name | «Напоминания о приёме» ("Intake reminders") | «Настойчивые напоминания» ("Persistent reminders") |
| Importance | High | High |
| Sound | Product short signal, alarm volume | System alarm signal, alarm volume |
| Vibration | `[0,350,250,350]` | `[0,700,250,700,250,700]` |
| Do Not Disturb bypass | No | Yes |
| Lock screen | Public | Public |
| Icon badge | Off | Off |

- **First reminder**: regular channel.
- **Repeat**: persistent only with "persistent reminder, repeat if not marked" on and the intake still pending after the "defer by" interval.
- **Limit**: at most two repeats; then no show, the intake stays pending and becomes missed. Endless repeat is forbidden.
- **Volume**: alarm volume, not notification volume.
- **Immutability**: recreating with the same id restores old settings, hence the versioned regular id. The old `reminders_regular` is removed at start only when none of its notifications are in the shade.
- **Do Not Disturb bypass**: repeat-only, with policy access; without it diagnostics shows a limitation, not an error.
- **Public lock screen**: a reminder without the medication name would force an unlock.

### 3.5 Reminder card

- **Surface**: full-screen above the lock screen; «Приём по расписанию» ("Scheduled intake"), time, name, dose and strength on one line; a foreign profile gets a label. No watch mark.
- **Actions**, exactly three: «Принято» marks, deducts, dismisses; «Отложить N мин» ("Defer N min") moves the alarm by the configured interval, keeps `scheduled_at` and pending status, dismisses until the repeat; «Пропустить» marks without deduction. The deferral label comes from the setting. Bottom buttons apply to **all** unmarked intakes. The same three actions are embedded in the shade notification and dismiss it.
- **Planned time immutable**: never rewritten on mark or deferral.
- **Idempotence** by intake id, including repeated presses.
- **Ignore**: the notification stays, the intake stays pending; persistent reminders repeat; at window expiry it becomes missed and the notification goes. A regular firing above 30 minutes late is **not presented**: logged as "fired late, not shown"; the intake stays pending and markable until end of day; exactly 30 minutes is shown. A suppressed firing counts neither as fired nor missed in the summary.
- **Same time**: one card with a list, not a stack; rows mark independently; one notification per profile and time slot.
- **Late deferred repeat**: above 30 minutes late is not presented; the record is removed and the log gets "deferred, dropped". A separate constant, not the intake window.

### 3.6 Diagnostics, delivery self-check, log

- **First level**: last presented reminder (medication, plan, fact, deviation; suppressed excluded), next planned (medication, time, "plan" label, indicator explanation), the 7-day summary as one sentence without degenerate fractions, every permission state on its row, and the self-check. Technical details never reach it.
- **Outage**: a forced stop (alarms removed by the system, recognized by missing firings while the plan is alive) produces at the next launch a direct message: reminders did not arrive, since when, what to do. Tolerance 2 minutes, depth 1 day.
- **No sending**: nothing is sent; no in-app developer report action.
- **Self-check**, person-started: shade explains the flow, test firing after 90 s, request to close the app (swiped from recents; locking not required), result at the next entry. Production path: exact alarm, regular channel, full-screen card; on Android 14 and above without full-screen permission a banner, which also counts. Not an intake: no record, history, or stock, never on Today. The card carries «Проверка доставки» ("Delivery check") and «Тестовое напоминание. Отмечать ничего не нужно.» ("Test reminder. Nothing needs marking."), shown even beyond the late threshold. Outcomes: on time (0 to +120 s), late by N minutes, did not arrive, did not take place (app open at firing). One banner at the next entry, kept in the block. Local. A test past due at opening closes as "did not arrive".
- **Record kinds**: firing, presentation, suppression, repeat, deferral, deferred, reschedule, settings change, transition to missed, rotation, pause, environment, self-check; the outcome is a code in the reason field.
- **Rotation**: above 90 days, never inside the 7-day window; above 2 MB evicted in batches of 128, never inside that window; the fact is its own record; runs at start outside the critical path.

### 3.7 Degradation without the exact alarm permission

Without the permission reminders are **not set at all**: the exception is caught, no inexact alarm is substituted. A warning shows on Today and a row in diagnostics. The permission is checked on every reschedule; the result lands in the rebuild outcome and the log.

### 3.8 All numeric thresholds

| Threshold | Value |
|---|---|
| Alarm planning horizon | 48 h |
| Intake generation depth | 7 days including today, minimum 3 |
| Firing on time | 0 to +120 s |
| Diagnostic summary window | 7 days |
| Human mark on time | [−2 h, +2 h] around the planned time |
| Late regular firing suppressed | above 30 min |
| Late restored repeat suppressed | above 30 min |
| Persistent repeats | at most 2 |
| Deferral intervals, default | 5, 10, 15, 30, 60 min; 15 |
| Intake window boundary | 00:00 next day |
| Undo of mark or medication deletion | 5 s (corridor 4 to 6) |
| Group mark confirmation | 10 s |
| Dense day | above 10 intakes |
| Anchor alarm | 03:17 local |
| Outage tolerance, depth | 2 min, 1 day |
| Pre-unlock snapshot: records, age, future allowance | 256, 48 h, 5 min |
| Pre-unlock trace queue | 8 |
| Presentation poll | 10 times 100 ms |
| Self-check delay | 90 s |
| Log retention, untouchable window, ceiling, batch | 90 days, 7 days, 2 MB, 128 |
| Background task attempts | 3 |
| Stock warning threshold | 3, 5, 7, 10, 14 days; 7 |
| Tap target | at least 44 dp |
| Font scale, Display ceiling | free to 200 percent, 130 percent |
| Time format in diagnostics | 24-hour in all locales, `H:mm` |
| Profiles | at most 5 |

---

## 4. Permissions and reliability onboarding

### 4.1 Platform constraints

| Constraint | Consequence |
|---|---|
| Huawei firmware unloads background processes and denies autostart | State in the database; an alarm raises the process from zero; onboarding walks vendor settings by hand |
| Doze defers ordinary alarms | Exact alarms; their idle frequency limit means the nearest window is planned |
| Exact alarms need special access from Android 12, not granted by default from 13 | Checked before planning; a warning instead of silent degradation |
| Alarms vanish on reboot; a force-stopped app gets no boot event | Rescheduling on boot events; a secure lock delays the event to the first unlock, covered by snapshot recovery |
| Channels are immutable; recreating with the same id restores old settings | Two static channels |
| Full-screen notifications are limited to alarm and call apps from Android 14 | Full-screen permission, degrading to an ordinary notification |
| A timezone change separates "8:00" from a moment | Rescheduling; intakes keep their zone, a recomputation is offered |

### 4.2 Permissions

The shipped build declares no network permission: `INTERNET` and `ACCESS_NETWORK_STATE` are absent from the merged manifest, and so are `ACCESS_WIFI_STATE` and `AD_ID`.

| Permission | Purpose | On refusal |
|---|---|---|
| `POST_NOTIFICATIONS` | From Android 13; reminders and stock warnings | Silent and most dangerous: the reminder is planned, fires, and is not shown. The app detects it and does not pretend to work |
| `SCHEDULE_EXACT_ALARM` | Special access, Android 12 and above | The system shifts the firing; on Android 12 and above `setAlarmClock` depends on it, so the mechanism fails |
| `USE_EXACT_ALARM` | Declared, Android 13 and above; primary right; granted at install | Both declared: this primary, the previous as fallback |
| `RECEIVE_BOOT_COMPLETED` | Rescheduling after reboot | Reminders silently stop after the first reboot |
| `USE_FULL_SCREEN_INTENT` | Full-screen card | Degrades to a heads-up |
| `VIBRATE` | Tactile channel | A silent reminder goes unnoticed |
| `ACCESS_NOTIFICATION_POLICY` | Do Not Disturb bypass and a diagnostics row | The app never appears in the system list, the check always fails, the row stays "check manually" |

Checked on every reschedule; refusal shows a warning at once. A grant is a system event, so the window is replanned immediately. Do Not Disturb access is not an onboarding step: requested on the reminder settings screen with an explanation. No refusal breaks a screen: the app works as a manual journal but says permanently that reminders are unreliable.

Package visibility covers vendor managers (Huawei, Xiaomi, both Samsung builds), without which the section cannot open on Android 11, and the PDF document handler. `QUERY_ALL_PACKAGES` is not declared.

### 4.3 Vendor steps and the reliability screen

«Настроить надёжность» ("Set up reliability") walks notifications, exact alarms, then vendor steps. Two common steps are those the system answers itself. Vendor steps come from the firmware profile: Huawei and Honor three (app launch, power saving, pinning in recents), Xiaomi HyperOS or MIUI including Redmi and POCO three (autostart), Samsung One UI two (power saving, pinning), unknown firmware none. Set and order come from a domain table, not the screen; the profile is detected from manufacturer and brand strings and the system manager; wording and paths come from that firmware. A vendor step is an instruction with «Открыть настройки» ("Open settings") and human confirmation, not a permission request; pinning has no button anywhere.

- **One screen, all entries**: first launch, the Today unreliability banner, the outage banner, the settings row, the quiet "run the setup again" in diagnostics. Five steps in fixed order, system-granted first; names match the system's words.
- **Vendor steps are not checked**: unconfirmed by default, leading to system settings, confirmed by a switch marked «Подтверждено вами» ("Confirmed by you"); removable.
- **Skip**: «Пропустить, настрою позже» ("Skip, I will set it up later") on first launch only, and recorded. Elsewhere the footer is a quiet «Готово» ("Done"). Today keeps a warning until the chain is complete.
- **Notifications step** checks permission, channel, full-screen. Reasons come from one enumeration, in the words of the Today banner and diagnostics. Order: "do not arrive" (notifications, then channel), "arrive late" (exact alarms), "shown incompletely" (full-screen). With notifications off the channel is not named separately; "notifications plus exact alarms" is one phrase.
- **First launch**: the step indicator appears then only. System Back acts as a skip: the screen is marked seen, the person lands on Today with the warning, and onboarding does not reopen by itself.

### 4.4 First launch: privacy policy

- Before the first screen and onboarding, a «Политика конфиденциальности» ("Privacy policy") dialog: data stays on this device and is transferred nowhere, a link to the published policy, and two actions, «Принимаю» ("I accept") and «Отказываюсь» ("I decline"). Not dismissible by system Back or a tap outside; until a choice is made neither onboarding nor Today is available.
- The link opens the same document as the settings row; no second address. Page language follows the app: RU `/ru/`, EN `/en/`, ZH `/zh/`; DE, ES, KK, and unknown get English. Opening the link does not dismiss the dialog.
- Acceptance is one key, `privacy_acknowledged`, dismissing the dialog forever. Without it the dialog appears at every launch, including after a process death before the press.
- «Отказываюсь» closes the app and records nothing; the next launch shows the dialog again.
- Release logs contain no medication names, doses, profile names, or file contents.

---
