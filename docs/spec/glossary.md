# Glossary

The vocabulary of the product. Documentation, screen text, tests and code comments use these
terms and no others: a sentence that reaches for a synonym is rewritten. Stored domain values
are string codes and are never translated in code; the Russian column of the tables is the
wording shown to a person. Russian interface strings are quoted as `«Принято»` and glossed in
parentheses.

## 1. Language rules

- One term per concept: a document, a test name, a commit message, an identifier.
- A term names one thing only. "Widget" never means an intake card, "reminder" never means a
  system notification in general, "archive" never means deletion.
- A person is never a patient, a user or an account: profiles have no password and no sign-in.
- The product is not a medical device: it stores the schedule, shows marks, and gives no
  treatment advice, no dose selection, no interaction check.
- Russian words are not transliterated into identifiers; code uses the English term.
- The Russian label of a code is display only. Code compares codes, never labels.

## 2. Product and layers

**Sklyanka**
: The product name: an apothecary vial and a ship's-bell half hour in one word.
_Avoid_: "Sklyanka+", transliteration in Russian prose.

**Offline core**
: What works without a network: schedule, reminders, marks, history, stock, transfer, profiles,
diagnostics.
_Avoid_: database, free version, lite.

**App accent**
: The color of actions and selected elements, from four palettes; profile colors are independent.
_Avoid_: "theme" without a qualifier.

**Complex schedule**
: Any mode beyond daily fixed times, one weekday and as-needed.
_Avoid_: advanced schedule, flexible schedule.

## 3. People and profiles

**Profile**
: The owner of a set of medications, not an account.
_Avoid_: user, account.

**Active profile**
: The single profile the interface works in; screens show only its intakes.
_Avoid_: current account, selected user.

**Owner profile**
: The phone owner's profile, flagged as own; exactly one in the database.
_Avoid_: admin, main profile.

**Foreign profile**
: An active profile not owned by the phone owner; needs redundant indication.
_Avoid_: additional profile, "mom's profile" as a term.

**Cared-for person**
: The person whose intakes are followed through a foreign profile; shown by profile name.
_Avoid_: patient, sick person, "child" as a generalization.

**Reminder mirroring**
: Duplicating a cared-for person's reminders onto the owner's device.
_Avoid_: reminder sync, forwarding.

## 4. Medication and course

**Medication**
: A drug with its dose, schedule and course; one record is one drug of one profile.
_Avoid_: preparation, remedy as a synonym.

**Dose form**
: Tablet, capsule, spray, syrup, drops or injection; sets the tablet shape and dose unit.
_Avoid_: drug type, kind of preparation.

**Tablet shape**
: The silhouette of a tablet for recognition; only for the tablet dose form.
_Avoid_: "tablet form", confused with the dose form.

**Dose per intake**
: Units taken at a time; deducted from stock when the intake is marked taken.
_Avoid_: dosage without a qualifier.

**Dose unit**
: The unit for one intake, from the dose form: tablet, capsule, millilitre, drop, spray puff,
injection; no free choice.
_Avoid_: piece, "pcs.", "dose" as a generalization.

**Medication dosage**
: Active substance per unit (500 in "500 mg each"); shown, never used in calculations. Unit
from a closed list: mg, µg, g, ml, IU, %.
_Avoid_: concentration, "dose".

**Meal relation**
: "Does not matter", "before a meal", "with a meal", "after a meal"; does not affect timing.
_Avoid_: before/after food in code.

**Intake mode**
: The fork of the model: by schedule or as needed.
_Avoid_: intake type, dosing mode.

**Scheduled**
: An intake at given times; records are produced in advance by planning.
_Avoid_: "by the clock" as a term.

**As needed (PRN)**
: An intake not tied to times; the record appears on `«Принять»` ("Take") and is already taken.
_Avoid_: on demand, situational, "PRN" in human-facing text.

**Daily limit**
: The maximum number of intakes per day; as-needed medication only.
_Avoid_: dose limit, "no more than N times" in code.

**Intake times**
: The local times at which intakes are produced; scheduled medication only.
_Avoid_: schedule, intake slots.

**Course**
: The period from the start date through the end date inclusive; no separate entity exists.
_Avoid_: therapy, prescription.

**Archive**
: Removing a course from the schedule while keeping its history; restoring is possible.
_Avoid_: soft delete, deletion, hiding a medication.

**Course finished**
: Archive reason: the course reached its end date; the app suggests it, the person confirms.
_Avoid_: automatic archive.

**Removed manually**
: Archive reason: the person stopped the medication themselves.
_Avoid_: deleted manually.

**Restore from archive**
: Lifting the archive state; schedule and planning are rebuilt from the current date.
_Avoid_: unarchive.

**Deleted medication**
: A softly deleted record created by mistake, with no taken marks; hidden, its rows kept, never
physically deleted.
_Avoid_: erased, permanently deleted, trash.

## 5. Intake and mark

**Intake**
: One event of taking a medication, planned or performed.
_Avoid_: dose in the sense of an intake, transliterated "intake".

**Scheduled intake**
: An intake produced in advance by planning, visible before its time arrives.
_Avoid_: future intake in general.

**PRN intake record**
: An intake created on `«Принять»` ("Take") for as-needed medication.
_Avoid_: unplanned intake.

**Mark**
: The action moving an intake to taken or deliberately skipped; idempotent per intake.
_Avoid_: note about an intake, check.

**Record withdrawal**
: Cancelling an as-needed record in a short window: marked withdrawn, not deleted; the dose
returns.
_Avoid_: deleting a record, undoing a mark.

**Withdrawn record**
: A cancelled as-needed record: invisible in history and stock, no reminders.
_Avoid_: deleted record, cancelled intake.

**Pending**
: The time has not come, or the window has not expired, and there is no mark.
_Avoid_: unmarked.

**Taken**
: The person confirmed the intake; stock was reduced by the dose.
_Avoid_: drunk, done.

**Skipped**
: The person explicitly declined the intake; stock is untouched.
_Avoid_: "skip" without a qualifier.

**Missed**
: The window expired with no mark; set automatically.
_Avoid_: auto-skip in human-facing text.

**Mark source**
: Where the mark came from: the phone or the app automatically; no watch source exists.
_Avoid_: mark channel.

**Intake window**
: The interval in which a mark is accepted, up to the end of the intake's day.
_Avoid_: intake deadline, miss window.

## 6. Reminders and reliability

**Reminder**
: A notification about an intake, shown as a full-screen card over the lock screen.
_Avoid_: notification as a synonym in human-facing text, push.

**Regular reminder**
: The first presentation about an intake, always in the regular channel.
_Avoid_: first ping.

**Persistent reminder**
: A repeat about an unmarked intake, at most two, bypassing Do Not Disturb.
_Avoid_: second reminder, alert.

**Deferral (snooze)**
: Moving the presentation by the configured interval; the scheduled time does not change.
_Avoid_: remind later, moving the intake.

**Exact alarm**
: A system alarm set for the intake minute; not shifted by the system, shown in the status bar.
_Avoid_: alarm without a qualifier.

**Reminder check**
: The reliability status screen: verdict, permissions, seven-day summary, delivery self-check.
_Avoid_: diagnostics in human-facing text.

**Delivery self-check**
: A manual check over the real path: the person starts it, a test trigger fires in one or two
minutes, the result comes from the log.
_Avoid_: test reminder.

**Notification channel**
: A system notification group with its own sound and importance; two static channels.
_Avoid_: channel without a qualifier.

**Do Not Disturb**
: A system mode that mutes the regular channel; the persistent reminder bypasses it.
_Avoid_: quiet mode.

**Reliability onboarding**
: The screen walking a person through the system settings delivery depends on: notifications,
exact alarms, autostart, battery saving.
_Avoid_: permission onboarding as a screen name, setup wizard.

## 7. Stock

**Stock**
: Units remaining in the package; reduced by the dose on "taken".
_Avoid_: supply without a qualifier, balance.

**Purchase threshold**
: Days of stock left before a warning appears; seven by default.
_Avoid_: stock threshold without the word purchase.

**Purchase warning**
: "Enough for N days" in the Today header and card when the threshold is hit; sends nothing.
_Avoid_: purchase notification.

**Restock**
: Manual new stock value; adds to or replaces it, never counts packages.
_Avoid_: purchase, receipt.

## 8. History and adherence

**History**
: The feed of intakes and the aggregates of a profile for a period, archived medications included.
_Avoid_: statistics.

**Adherence**
: The share of taken against scheduled for a period, computed from records and not stored.
_Avoid_: compliance, observance.

**Course summary**
: "7 days, 13 of 14 taken" in the archive card; the denominator counts intakes before archiving.
_Avoid_: course success.

**Report for the doctor**
: A PDF with the adherence history of one profile for a period, handed over by the person.
_Avoid_: medical document, conclusion.

**Taken on time**
: The history tile with the share of marks inside two hours before to two hours after the
scheduled time; the denominator counts scheduled intakes that arrived.
_Avoid_: "on time" for delivery, "on time" without a window.

## 9. Data and transfer

**Data transfer**
: The only way to move to another phone and the only backup: one file, no cloud.
_Avoid_: sync, backup.

**Transfer file**
: JSON with the product identifier and the format version.
_Avoid_: data archive, dump.

**Merge (add to current)**
: The default load mode; matches are listed separately for a manual decision.
_Avoid_: merge without questions.

**Replace all**
: A mode that fully replaces the data; a safety export is mandatory.
_Avoid_: re-upload, restore.

**Match decision**
: Keep mine, take from file, add separately, or do not load, for one medication in both sets.
_Avoid_: merge conflict.

**Safety export**
: A copy of the data before replace all; the person is told where it is.
_Avoid_: backup.

**History export**
: Adherence history to CSV, one active profile, opens in Excel; independent of the full transfer.
Entry: `«Настройки»` ("Settings"), `«Данные»` ("Data").
_Avoid_: database export, history dump, "Excel file".

## 10. Platform and time

**Scheduled intake time**
: The rule "8:00 by the local wall clock", not a moment: after a flight it stays 8 a.m.
_Avoid_: intake timestamp.

**Actual mark moment**
: The moment the person tapped; never recomputed, and the mark race is decided by it.
_Avoid_: mark time without a qualifier.

**Time zone change**
: The event that recomputes the full window: local time shifted against the moment; not a clock
change.
_Avoid_: clock change for both events.

## 11. Screens and surfaces

**Today**
: The main screen of the active profile's intakes: feeds by time of day, day progress, stock
warning.
_Avoid_: home, dashboard.

**Intake card**
: An intake row with `«Принято»` ("Taken") and `«Пропустить»` ("Skip") on Today.
_Avoid_: tile, widget.

**Reminder card**
: The full-screen card over the lock screen; simultaneous intakes form one card with a list.
_Avoid_: push, notification.

**Widget**
: An Android home-screen widget (Today, week, quick mark); never an intake card.
_Avoid_: widget in the sense of an intake card, tile.

## 12. Words the product does not use

**Patient**: not a medical device; say profile or cared-for person.

**Prescription, appointment**: no prescription is stored; the person enters the schedule.

**Account, sign-in**: no accounts; a profile is not an account.

**Cloud, cloud backup**: no cloud; transfer is a file.

**Drug interaction, contraindication**: no compatibility checks exist.

**Treatment recommendation, dose selection**: no recommendations are given.

**Trash, delete forever**: no medication is physically deleted.

**Quiet hours, per-app sound**: not part of the product.

**Sync between devices**: a second phone receives data only by transfer file.

## 13. Domain value codes

Values are stored as string codes, never translated in code. Codes match the Kotlin enums and
the transfer file exactly.

| Enum | Code | Meaning (Russian UI label) |
|---|---|---|
| Intake mode | `SCHEDULED` | by schedule («по расписанию») |
| Intake mode | `PRN` | as needed («по потребности») |
| Intake state | `PENDING` | pending («ожидается») |
| Intake state | `TAKEN` | taken («принято») |
| Intake state | `SKIPPED` | deliberately skipped («пропущено осознанно») |
| Intake state | `MISSED` | missed, no mark («пропущено без отметки») |
| Mark source | `PHONE` | marked on the phone («отмечено в телефоне») |
| Mark source | `AUTO` | set by the app automatically («проставлено приложением автоматически») |
| Archive reason | `COURSE_FINISHED` | course finished («курс завершён») |
| Archive reason | `MANUAL` | removed manually («убрано вручную») |
| Dose form | `TABLET` | tablet («таблетка») |
| Dose form | `CAPSULE` | capsule («капсула») |
| Dose form | `SPRAY` | spray («спрей») |
| Dose form | `SYRUP` | syrup («сироп») |
| Dose form | `DROPS` | drops («капли») |
| Dose form | `INJECTION` | injection («инъекция») |
| Tablet shape | `ROUND` | round («круглая») |
| Tablet shape | `ROUND_SCORED` | round, scored («круглая с риской») |
| Tablet shape | `OVAL` | oval («овальная») |
| Tablet shape | `OBLONG` | oblong («продолговатая») |
| Tablet shape | `TRIANGULAR` | triangular («треугольная») |
| Meal relation | `ANY` | does not matter («неважно») |
| Meal relation | `BEFORE_MEAL` | before a meal («до еды») |
| Meal relation | `WITH_MEAL` | with a meal («во время еды») |
| Meal relation | `AFTER_MEAL` | after a meal («после еды») |
| Dose unit | `TABLET` | tablet («таблетка») |
| Dose unit | `CAPSULE` | capsule («капсула») |
| Dose unit | `MILLILITRE` | millilitre («мл») |
| Dose unit | `DROP` | drop («капля») |
| Dose unit | `SPRAY` | spray puff («впрыск») |
| Dose unit | `INJECTION` | injection («инъекция») |
| Reminder channel | `REGULAR` | regular channel («обычный канал») |
| Reminder channel | `PERSISTENT` | persistent channel («настойчивый канал») |
| Alarm kind | `ALARM_CLOCK` | exact intake alarm («точный будильник приёма») |
| Alarm kind | `EXACT_IDLE` | silent service alarm («служебный тихий будильник») |
| Match decision | `keep_mine` | keep mine («оставить моё») |
| Match decision | `take_from_file` | take from file («взять из файла») |
| Match decision | `add_separately` | add separately («добавить отдельно») |
| Match decision | `do_not_load` | do not load («не загружать») |
| Transfer mode | `merge` | add to current («добавить к текущим») |
| Transfer mode | `replace_all` | replace everything («заменить всё») |

The medication dosage unit is a closed list of display labels, not enum codes: «мг», «мкг», «г»,
«мл», «МЕ», «%».

A code needs its enum: `TABLET` is a dose form in one enum, a dose unit in another, a tablet
shape in a third.
