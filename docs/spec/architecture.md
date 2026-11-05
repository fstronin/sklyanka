# Sklyanka: Architecture Specification

The stack, the twelve Gradle modules and their allowed dependencies, the build checks that hold
the module and artifact rules, the path of an intake from schedule to report, and the boundaries
enforced by the build.

## 1. Stack

A native Android application: exact alarms, notification channels and a token-based design system
depend on platform APIs directly.

- Kotlin (coroutines, `Flow`, JVM target 17) and Jetpack Compose with typed design tokens.
- Room is the only database: typed entities, DAOs, forward-only migrations, compile-time queries.
- Hilt wires activities, receivers and workers through `@AndroidEntryPoint`.
- `AlarmManager` is the only reminder mechanism; `WorkManager` carries work beyond a receiver
  budget. DataStore holds state that must survive process death; kotlinx.serialization carries the
  transfer file; Glance builds widgets.
- PDF and CSV are rendered locally and handed to the system. No server participates.

`ProjectDefaults` is the single source of platform parameters: `minSdk 26` (Android 8.0), `compileSdk`
and `targetSdk` 35, application ID `ru.stohastika.sklyanka`, and interface locales with Russian as
default.

There is no Google Play Services, Firebase, server, account, synchronization, advertising or client
analytics, and the product contains no network module at all: the app declares no network permission
(§5).

## 2. Module map

Twelve Gradle modules exist; `build-logic` and `config/detekt/rules` are included builds. Below,
`screen` = `core-domain`, `core-ui`, `core-data`; `view` = `core-domain`, `core-ui`.

| Module | Type | Responsibility | Allowed dependencies |
|---|---|---|---|
| `:app` | application | Hilt graph, navigation, manifest, receivers, channels, alarm scheduler, platform adapters | `core-domain`, `core-ui`, `core-data`, all `feature-*`; `testDebug` adds `acceptance` |
| `:core-domain` | Kotlin/JVM | Entities, statuses, invariants, schedule expansion, windows, use cases, adherence and report model, repository interfaces, ports | none |
| `:core-data` | Android lib | Room entities, DAOs, migrations and schemas, repositories, mappers, DataStore stores, journals, transfer file | `core-domain` |
| `:core-ui` | Android lib | Tokens, typography, theme, base components, shared strings, locale, previews | `core-domain` |
| `:feature-today` | Android lib | Today: intake lanes, as-needed section, marking with undo, remainder of the day | screen |
| `:feature-meds` | Android lib | Medication list, form, card, course archive | screen |
| `:feature-reminder` | Android lib | Reminder card and actions, deferral, diagnostics, delivery self-check | view |
| `:feature-history` | Android lib | History, adherence month, breakdown, report for the doctor, PDF, CSV export | screen |
| `:feature-settings` | Android lib | Settings, accent theme, profiles, data transfer | screen |
| `:feature-onboarding` | Android lib | Reliability onboarding, permissions, privacy policy | view |
| `:feature-widgets` | Android lib | Today, week and medication widgets, providers, marking receiver, settings | view |
| `:acceptance` | Kotlin/JVM | Shared scenario world, Gherkin step dictionary, run harness | `core-domain` (API) |

The module set is closed: there is no paid module, no store client and no network module, so no
artifact carries a network dependency or a network permission.

## 3. Dependency rules

Dependencies point inward: `app -> feature-* -> core-ui / core-data -> core-domain`.

- `core-domain` depends on no project module and has no Android plugin.
- A `feature-*` never depends on another `feature-*`; shared detail moves into `core-*`.
- `core-ui` and `core-data` depend on `core-domain` only.
- Nothing depends on `:app`; `:acceptance` is a test dependency only.
- Platform mechanisms are interfaces in `core-domain`, implemented outside it: alarms,
  notifications, file system, clock and zone, report transfer, journals. Hilt is not applied to
  `core-domain`.

`verifyModuleDependencies` fails on any outward edge, and `verifyModuleDependencyRule` proves the
same logic on synthetic violating graphs.

## 4. Build logic

Module build files contain facts only. The included build `build-logic/convention` supplies shared
plugins for application, library, Compose, JVM library, Hilt, quality, screen tests, release signing,
artifact checks and project checks: SDK levels, targets, lint, KSP and Hilt, signing from external
properties, the screen stand and artifact checks.

Seven rules live in the included build `config/detekt/rules`:

- `HardcodedDesignToken` and `HardcodedTextStyle` keep design literals inside
  `ru.stohastika.sklyanka.ui.token`; `RussianTextInCode` keeps Cyrillic out of code, with the
  acceptance step dictionary as the only exception.
- `DirectSystemClockAccess` allows clock reads only in `ru.stohastika.sklyanka.platform.time`.
- `DestructiveRoomMigration` forbids `fallbackToDestructiveMigration*`.
- `AndroidImportInDomain` forbids `android.*` imports in `ru.stohastika.sklyanka.domain`.
- `MedicalDataInJournal` forbids names, doses, schedules and profile identifiers in journal records.

`verifyStaticAnalysisRules` runs detekt over deliberate violation samples in
`tools/violations/kotlin` and requires every rule to fire. The JVM screen stand runs Robolectric at
API 35 with qualifiers `ru-rRU-w390dp-h844dp-mdpi` in `app`, `core-ui` and every feature module.

`verifyAll` builds all modules, compiles instrumented tests, runs the `app` unit tests, the module,
rule and artifact composition checks, the rule tests and the acceptance run.

## 5. Variants and network

`app` has one flavor dimension, `contour`, with a single flavor, `free`. The flavor is kept for its
name only: `assembleFreeDebug` and `assembleFreeRelease` are part of the release pipeline and of the
F-Droid build, so the variant names stay stable. There is no second variant, no paid build and no
product flavor that differs in code.

There is no network contour and no contour mechanism. The product ships no network module, so the
composition check does not grant a permission to a module; it forbids the permission outright.
`ArtifactCompositionCatalog` holds the lists, `ArtifactCompositionRules` holds the logic as pure
functions, and the manifest and dependency tasks read both:

| Check | Rule |
|---|---|
| `verifyManifest<Variant>` → `ArtifactCompositionRules.validateManifest` | `INTERNET` and `ACCESS_NETWORK_STATE` fail in the merged manifest and in any source manifest of any module; a `tools:node="remove"` node does not count as a declaration, which is how `app` strips the network-state permission that `androidx.work` merges in. `ACCESS_WIFI_STATE` and `com.google.android.gms.permission.AD_ID` fail always. The package name and `minSdk 26` are checked in the same task |
| `verifyDependencies<Variant>` → `ArtifactCompositionRules.validateDependencies` | `com.google.android.gms`, `com.google.firebase`, `com.google.android.play`, `com.crashlytics`, `io.fabric` and `com.google.mlkit` fail in the resolved runtime classpath and in the version catalog, so a coordinate cannot wait in `libs.versions.toml` for its first use |
| `verifyArtifactCompositionRule` | the logic above must fire on deliberate violations, otherwise "the rule holds" is indistinguishable from "the rule is silent" |

The rules are part of `assemble<Variant>`; the debug variant is also part of `check`. Nothing in the
repository needs a network: the merged manifest declares no network permission, no module manifest
declares one, and no forbidden group reaches an artifact.

## 6. Data flow

### 6.1 Schedule generation

`PlanIntakesUseCase` expands a schedule over 7 calendar days including today, with a minimum depth
of 3 days so the 48-hour alarm horizon is covered from any moment. Only non-archived, non-deleted
medications with times are expanded; past times on the start date are skipped. The diff keeps every
existing record of any status at its time, creates missing moments, removes only future pending
records absent from the new plan, and leaves past pending records for the sweep. Marked records are
never rewritten; insertion ignores conflicts, and a unique index on medication and planned time
holds uniqueness.

Generation precedes alarm planning, because an alarm must address an existing intake.

### 6.2 Reminder planning and delivery

`RescheduleRemindersUseCase` is the only operation that changes the alarm set, so no partial update
leaves an orphan alarm. It sweeps expired pending intakes, takes a 48-hour window, merges deferred
and retry records by intake ID, compares the result with `AlarmGateway.scheduled()`, cancels stale
alarms and schedules missing or moved ones; a repeated run with unchanged data changes nothing.
Alarms are addressed by intake ID, so scheduling again replaces the previous one, and the show
number is an intent extra.

Intake reminders use `setAlarmClock` with a show intent and appear in the system alarm indicator;
the daily anchor (03:17 local, next occurrence strictly after now) and the day boundary use
`setExactAndAllowWhileIdle` and stay out of it. Inexact and repeating alarms are not used.
`AlarmReceiver` presents the reminder immediately, then enqueues the rebuild through `WorkManager`;
the anchor and day boundary also run as `WorkManager` work. A direct-boot snapshot without medical
data restores alarms before unlock; after unlock the full rebuild wins. Channels are created once
with fixed identifiers, and intake reminders use the persistent reminder channel.

### 6.3 Marking, undo and missed intakes

`MarkIntakeUseCase` is the only mark entry point for the Today screen, the reminder card and the
widget action; source `AUTO` is rejected there and belongs to the sweep.

- The update is conditional and transactional: taken or skipped only from pending. A second mark
  changes zero rows and returns "ignored", so stock is not debited twice; an earlier mark may
  replace a later one on `acted_at`.
- Taken debits the dose from the package remainder, skipped does not; the remainder never goes
  below zero, and clipping at zero is recorded.
- Undo lasts 5 seconds: a planned intake returns to pending and the dose is returned, while an
  as-needed record is revoked with `revoked_at` and leaves history, stock and the daily as-needed
  limit. A marked record is never rewritten retroactively; a late correction is a new decision.

A pending intake becomes missed at 00:00 of the next day. The sweep runs on a timer, on app start
and on every replan; it writes `acted_at`, source `AUTO`, no actor and no notification.

### 6.4 History and the report for the doctor

`HistoryRepository` exposes flows for the active profile; `AdherenceCalculator` and the on-time rule
compute adherence in the domain, and the history screen renders the month, tiles and breakdown.

`BuildDoctorReportUseCase` uses the same slice and calculator, so adherence has one definition, and
produces a `DoctorReport` for one profile. The PDF renderer in `:feature-history` turns it into a
`ReportDocument` (file name, media type, bytes) in memory; the `DoctorReportTransfer` port in `app`
hands it to the system share sheet or place picker. No network step exists and the app stores no
copy.

## 7. Enforced boundaries

### 7.1 Domain purity

`core-domain` is a Kotlin/JVM library without the Android plugin, so `android.*` is absent from its
classpath by construction. Domain to Room mappers live in `core-data`, domain to screen mappers in
`feature-*`. Time and zone come only from `TimeSource`: an `Instant` in UTC, the profile zone,
`today()` and `nowLocal()`.

### 7.2 Tokens and text

Only `ru.stohastika.sklyanka.ui.token` may contain design literals, and screens consume tokens,
theme and components from `core-ui`.

### 7.3 Storage and schema safety

Room is the only store of user data. Android backup is disabled (`android:allowBackup="false"`);
there is no cloud copy and no synchronization. The transfer file, in modes "add to current" and
"replace all", is the only way to move data.

- Schema changes are forward only: a new version requires `Migration(N, N+1)` and a new exported
  JSON in `core-data/schemas`. Released schemas are never edited or removed, the chain has no gaps,
  and reverse migrations do not exist. `fallbackToDestructiveMigration*` and `DROP TABLE` on user
  data are forbidden; physical `DELETE` is limited to replacing all data on import, deleting a
  foreign profile and recreating future pending intakes after a schedule edit.
- Before the first Room open, `PRAGMA user_version` is compared with the known version. For a
  pending migration one copy is taken in `noBackupFilesDir` after `PRAGMA wal_checkpoint(FULL)`;
  if space is short, the migration does not run and the missing megabytes are shown. It is not a
  backup and stays out of the transfer file.
- After a migration, `PRAGMA quick_check` and `foreign_key_check` run off the main thread, at most
  once a day. Findings are reported and data is preserved; automatic repair is forbidden and
  reminders are never blocked. If the database is newer than the build, Room is not opened, a
  blocking screen asks to update, and alarms create no mark and show no card in this state.

Medications are soft deleted through `archived_at`, `deleted_at` and `revoked_at`. Import is atomic:
the file is validated before writing, invariants are checked before applying, the load is one
transaction, and identifiers are reissued inside it.

### 7.4 Offline first, no Google services

No Google Play Services, Firebase, advertising SDK or client analytics SDK may appear on the runtime
classpath. The app has no network module: its merged manifest declares no network permission and no
code path opens a connection. Schedule, reminders, marks, history, stock, transfer, profiles and the
report work with no network. Nothing leaves the device.
