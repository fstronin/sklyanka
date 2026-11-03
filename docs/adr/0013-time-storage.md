# 0013 — Time storage: scheduled time as local wall clock, facts as UTC instants

Status: Accepted

## Context

The model holds two kinds of time, and a time-zone change pulls them apart. The scheduled time is an
intention in wall-clock terms ("at 8:00"). The moment of a mark happened once. Storing everything in
UTC breaks the first: an intake scheduled for 8:00 would arrive at 11:00 after a flight. Storing
everything in local time breaks the second: the order of marks becomes ambiguous across a zone
change.

## Decision

**Scheduled time is stored as local text without a zone; a fact moment is stored as epoch millis in
UTC** (`core-data/.../room/SklyankaConverters.kt`).

| Field | Storage | Domain type |
|---|---|---|
| `Medication.times` | `TEXT`, sorted string `HH:mm,HH:mm`; empty string for a PRN course | `List<LocalTime>` |
| `Medication.start_date`, `end_date` | `TEXT`, `YYYY-MM-DD`; `end_date = NULL` means an indefinite course | `LocalDate`, `LocalDate?` |
| `Intake.scheduled_at` | `TEXT`, `YYYY-MM-DDTHH:mm`, local date and time without a zone | `LocalDateTime` |
| `Intake.acted_at` | `INTEGER`, epoch millis UTC; `NULL` for a pending intake | `Instant?` |
| Other fact moments (`archived_at`, `deleted_at`, operation and delivery log times) | `INTEGER`, epoch millis UTC | `Instant` |

Four mechanisms follow from the rule:

1. **The alarm trigger moment is computed, not stored.** Scheduling converts the local scheduled
   time with the current zone (`scheduledAt.atZone(zone).toInstant()`), and the zone comes from
   `TimeSource.zone()`. A zone or system-time change is a full window rebuild
   (`RescheduleReason.TIMEZONE_CHANGED`, ADR-0003).
2. **Travel does not rewrite created records.** The scheduler is calendar-based: a zone change
   changes neither the set of intakes nor their times, only the alarm queue is recomputed.
3. **A later mark does not replace an earlier one.** Comparison uses the absolute `acted_at`, the
   moment the person acted, not the moment the event was delivered.
4. **The day boundary is local midnight of the zone**, not "24 hours before the moment". On a day
   with a clock change, 23 or 25 hours long, calendar grouping does not drift.

`scheduled_at` is text, not a number: the lexical order of the format matches the chronological
order, a day is selected by range through an index (the "Today" query and history grouping), and a
number would have to be recomputed on every zone change, rewriting history. The unique index
`intake(medication_id, scheduled_at, schedule_slot)` prevents duplicate generation; a partial SQLite
index is not used because Room does not know about it. A PRN intake has no scheduled time:
`scheduled_at` is derived from the creation moment in the zone, truncated to the minute.

## Consequences

- The database knows no time zone: the zone arrives from `TimeSource` when a value is calculated,
  which makes rescheduling on `TIMEZONE_CHANGED` mandatory. Storage formats are neutral
  (`Locale.ROOT`) and the same in the transfer file (ADR-0008 item 5).
- A corrupted time string does not crash the app: the converter returns `null` and the calling layer
  decides the outcome.
- The window around a clock change is covered by tests wherever the conversion and the index are
  used.
