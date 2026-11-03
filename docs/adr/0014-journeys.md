# 0014 — End-to-end journeys: channel, file state, place in the run

Status: Accepted

## Context

Screen checks run against an in-memory database, and the acceptance layer runs on the JVM with
neither a screen nor a platform. Neither can testify that a person closed the app and came back, that
a completed setting survived process death, or that the alarm snapshot was restored from
device-protected storage. Those claims need the real platform and a real file.

## Decision

The application gets a fourth verification level, journeys: end-to-end passes of a person through the
app on the real platform. Each part was chosen against an alternative that looked cheaper.

1. **Channel: the instrumentation layer `app/src/androidTest`, package `screen/journey`.** Real
   `MainActivity`, real navigation, real layout, real storage. Rejected: a Robolectric screen harness,
   which has no platform, and a JVM acceptance run, which has neither screen nor platform; both exist
   already and add no evidence about human behavior. A live device remains a manual run.
2. **State: a real database file and a real `DataStore` file, for journeys only.** Screen checks stay
   on the in-memory database. The instrumentation argument `journeyStorage=file` decides the storage,
   not the class name: one APK serves both suites and the graph is assembled identically. A journey
   class started without the argument fails with a clear refusal instead of silently running in
   memory.
3. **Place: the `journey` group of the emulator harness, a pre-release run.** Every push runs
   `verifyAll`, which only compiles instrumentation checks; journeys are not and cannot be part of it,
   because `verifyAll` runs without a device (ADR-0007) and a journey run lasts tens of minutes. A
   green journey set is a condition of the release build, not of every commit.

Process death cannot be reproduced inside one process, so the survival relay is two runs: the first
writes state, the second, a separate instrumentation launch, reads it. A second Hilt graph was
rejected: it would require uninstalling modules for journey classes and keeping two similar graph
descriptions, while the argument describes exactly one difference, where the database lives.

One honesty rule overrides convenience: a defect found by a journey is not hidden. The assertion is
written as the requirement demands and stays red until fixed; the suite never adapts to current
behavior.

## Consequences

- The surface registry is kept with the journeys. Acceptance steps guard completeness: a surface
  without a row, a journey without a class, a class without a frame means a red run. A new screen gets
  a row in the same commit.
- Each new journey file is named in the instrumentation registry of checks.
- Journeys are excluded from the `ui` and `data` groups, so the screen group stays a one-minute run.
- System events (reboot, Doze, time-zone and clock change, midnight, update, actual delivery) are not
  covered by journeys; they remain harness trials. The split is recorded as explicit "out of suite"
  rows with reasons, so a gap reads as named rather than forgotten.
- Environment that a person does not set (permission grant and revoke, font scale, device locale) is
  set by a group script, with a separate run per state, so a failure is attributable to the class or to
  the environment.
- The `journey` group has its own report and summary next to those of the other groups.
