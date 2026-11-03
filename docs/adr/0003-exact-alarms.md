# 0003 — Exact alarms through `AlarmManager`, not `WorkManager`

Status: Accepted. Intake reminders use `setAlarmClock`.

## Context

A reminder must arrive within ±2 minutes of the appointed time, and that promise has to survive
reboot, Doze, time-zone change and vendor power management. `WorkManager` means "no earlier than"
with a window of tens of minutes under Doze; `setRepeating` has been inexact since API 19; a
foreground service keeps a permanent notification and drains the battery; push needs a server, which
the core does not have.

Exact alarms are not immune to the environment either. On EMUI 15 nine exact alarms and the anchor
were held until the app was opened, with deviations of 2321 to 14921 s. `setExactAndAllowWhileIdle`
frees a firing from Doze but does not oblige the system to deliver it at that moment.

## Decision

1. **Intake reminders** are scheduled with `AlarmManager.setAlarmClock` (`AlarmClockInfo` carrying
   the trigger moment and the show `PendingIntent`), in
   `app/.../platform/reminder/AlarmManagerGateway.kt`. The regular build always uses this mechanism;
   only the measurement build reads a saved strategy.
2. **Service alarms**, the reschedule anchor (03:17 local) and the day boundary, stay on
   `setExactAndAllowWhileIdle`: the system alarm indicator must show only the nearest intake
   reminder.
3. Scheduling covers a window 48 hours ahead (`ReminderWindow.HORIZON`), not the whole course.
4. Rescheduling runs on `BOOT_COMPLETED`, on `LOCKED_BOOT_COMPLETED` before unlock from
   device-protected storage, on a time-zone or system-time change, after a schedule change,
   archival or profile switch, after every fired alarm, and at app start.
5. Both permissions are declared: `USE_EXACT_ALARM` as the primary one, `SCHEDULE_EXACT_ALARM` as
   the fallback. `canScheduleExactAlarms()` is checked before scheduling, and the grant is caught by
   the receiver of `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. Silent degradation to
   inexact alarms is forbidden: without the permission no reminders are scheduled at all and the
   person sees a warning.
6. Permission onboarding is mandatory. The delivery self-check is started by the person and runs
   through the production path.

## Consequences

- The system alarm indicator is visible on the lock screen and in the status bar; it cannot be
  hidden while staying on `setAlarmClock`. The screens `«Настроить надёжность»` ("Set up
  reliability") and `«Проверка напоминаний»` ("Check reminders") explain it in words.
- Battery use is higher than with `setExactAndAllowWhileIdle`: reliability outranks economy, and the
  consumption is measured.
- The alarm queue is state outside the database. Rescheduling is idempotent and is tested by
  comparing the database set with the queue set.
- Every firing must schedule the next one; otherwise the chain breaks silently. Each firing is
  recorded in the delivery log.
- Reliability is measured per device on the device matrix: at least 95 % of deliveries within
  ±2 minutes over a continuous run. A firing delayed by the environment counts as late.
