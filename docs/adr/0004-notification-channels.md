# 0004 — Two static notification channels

Status: Accepted

## Context

Since Android 8 the channel owns sound, vibration and importance, and those settings are immutable
after creation: recreating a channel with the same `id` restores the old channel with its old
settings. The reminder must be louder and more visible than an ordinary notification, yet the
product refuses a per-medication sound.

## Decision

There are exactly two notification channels, created once on first launch and never recreated
(`app/.../platform/notification/ReminderChannels.kt`):

| Channel | `id` | Importance | Sound | Bypasses "Do not disturb" |
|---|---|---|---|---|
| Regular | `reminders_regular_v2` | `IMPORTANCE_HIGH` | the product's own short signal (`res/raw/reminder_chime.wav`), `USAGE_ALARM` | no |
| Persistent | `reminders_persistent` | `IMPORTANCE_HIGH` | the system alarm signal, `USAGE_ALARM` | yes |

- The regular channel plays the product's own signal; the persistent one plays the system alarm
  signal, because a system notification sound is indistinguishable from any other notification on
  the target firmware.
- Both play at alarm volume. Otherwise a reminder is muted together with notifications and lost in
  the stream of messages.
- The persistent channel has the long vibration pattern and bypasses "Do not disturb". Repeats are
  limited to two, with the interval taken from the `«Отложить на»` ("Defer by") setting.
- The `«Звук»` ("Sound") row in settings opens the system notification settings of the app. It does
  not choose a sound itself.
- A per-medication sound is not part of the product.

## Consequences

- Channel identifiers are part of compatibility: after the first publication they do not change
  without a reason, or the person receives a channel with default settings and possibly a silent
  reminder. A sound change is such a reason: the regular channel moved to `reminders_regular_v2`,
  and the former `reminders_regular` is deleted at startup when the shade holds no notification of
  it.
- Vibration patterns are frozen before publication.
- The channel list in system settings stays short: two items instead of one channel per medication,
  and sound behavior lives where the person is used to looking for it.
- A full-screen reminder requires `USE_FULL_SCREEN_INTENT`. Android 14 and newer restrict that
  permission by app category, so degradation to a heads-up notification is mandatory. A banner
  counts as a presentation and does not fail the delivery self-check.
- The difference between the channels is own signal versus system alarm signal: the system sound
  differs across firmware, the product's own sounds the same everywhere.

## Alternatives

A per-medication sound would need either a channel per medication with a versioned `id`, which
litters system settings and does not survive a transfer, or the app's own player over a silent
channel, which adds a second source of silent reminders in the most sensitive part of the product.
Both were rejected.
