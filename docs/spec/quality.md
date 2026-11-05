# Sklyanka: quality

Quality requirements, the method that verifies each one, and the actual verification status.

1. A requirement is published only together with a verification method.
2. A status comes from a found test, an acceptance scenario, or a build gate. Requirements with no verification are listed in section 5.

Statuses in section 3: **automated** runs inside `./gradlew verifyAll`; **automated, emulator** runs on an emulator outside `verifyAll` (an instrumented test, only compiled inside `verifyAll`, or a `tools/emulator` trial); **manual** is checked by a person on a device during operation through a `@device` or `@manual` acceptance scenario; **none** is verified by nothing; **(partial)** covers only part of the threshold, with the gap listed in section 5.

---

## 1. Verification levels

| Level | What it covers | How it is launched | Scope |
|---|---|---|---|
| Unit (JVM) | domain rules, formatting, guards for resources and clocks | `./gradlew testDebugUnitTest`, part of `verifyAll`, once per module: there are no `src/release` sources | 248 files under `*/src/test/`, 138 in `core-domain` |
| Screen stand (Robolectric) | screens and ViewModels, the component catalog, `@screen` scenarios, snapshots | the same run through the `sklyanka.screen.tests` plugin | 416 files under `*/src/testDebug/` |
| Instrumented | Room migrations, DAO, idempotency, refusals, journal; screen checks on the real platform (group `ui`) | `./gradlew :core-data:connectedDebugAndroidTest`; `tools/emulator/run-tests.sh <API> <group>` | 88 files under `*/src/androidTest/` |
| Journeys | 11 human paths, 2 reliability states, 7 hostile passes with real file storage; state is asserted, not only the screen; survival uses a relay of two runs | `tools/emulator/run-journeys.sh <API>`, group `journey`, five runs; `verifyAll` only compiles | 23 files under `app/src/androidTest/.../screen/journey/` |
| Acceptance (Cucumber) | English `.feature` files grouped by topic under `acceptance/src/main/resources/features/`; the run excludes `@manual`, `@slow`, `@journey`; an unimplemented step prints `UNDEFINED` and does not pass; the human remainder is printed by the list tasks for `@manual`, `@device`, `@screen` and `@journey` | `./gradlew :acceptance:acceptanceRun`, one JVM run per task (`CucumberBatch.kt`) | topic tags such as `@timezone` select the area, the four tags above select the executor |
| Snapshot baselines | Roborazzi comparison: a run fails on a mismatch and on a missing baseline; a baseline is a source file in the module's `screenshots/` | `./gradlew testDebugUnitTest`; record with `-Psklyanka.snapshots=record`; contact sheets with `-Psklyanka.visualReview=true` | 138 baselines |
| Visual run | localization matrix, six languages × screens; tolerance `SKL_PIXEL_TOLERANCE=8`; only the manifest is in git | `tools/visual/capture.sh` → `approve.sh` → `diff.sh` | 138 snapshots in `baselines/manifest.sha256` |
| Emulator run | API 26, 31, 35; groups `fast`, `data`, `heavy`, `migration`, `journey`, `all`, `ui`; trials for reboot, Doze, time zone and clock changes, forced stop, midnight, update, permission refusals | `tools/emulator/run-all.sh`, `trials.sh auto <API>`, `collect.sh <API>` | 16 trials, 3 not reproducible on an emulator |
| Manual device run | firmware-dependent behavior plus `@manual` and `@device` scenarios, presented during operation | gate numbers from `tools/spike/gate.py` over the journal export | the lists printed by the two acceptance tasks |

## 2. Build gates

| Gate | What fails the build | Where it is declared |
|---|---|---|
| detekt, 7 project rules | `HardcodedDesignToken`, `HardcodedTextStyle`, `RussianTextInCode`, `DirectSystemClockAccess`, `DestructiveRoomMigration`, `AndroidImportInDomain`, `MedicalDataInJournal`; a finding fails the build | `config/detekt/detekt.yml`, `config/detekt/rules/` |
| `verifyStaticAnalysisRules` | each rule must fire on a deliberate violation from `tools/violations/kotlin/` | `ProjectChecksConventionPlugin.kt` |
| ktlint 1.3.1 | formatting; a finding fails the build | `QualityConventionPlugin.kt` |
| Android Lint | `NewApi`, `InlinedApi`, `HardcodedText`, `ManifestOrder` at `error`; `GradleDependency` and `OldTargetApi` ignored; only product sources analyzed | `config/lint/lint.xml` |
| `verifyManifest<Variant>` | the merged manifest and every source manifest of every module: `INTERNET` and `ACCESS_NETWORK_STATE` are never allowed; `ACCESS_WIFI_STATE` and `AD_ID` always forbidden; package name, `minSdk 26` | `VerifyMergedManifestTask.kt`, `ArtifactCompositionRules`, `ArtifactCompositionCatalog` |
| `verifyDependencies<Variant>` | resolved composition and version catalog: GMS, Firebase, Play, Crashlytics, Fabric and ML Kit forbidden in the classpath and in the catalog | `VerifyDependencyCompositionTask.kt`, `ArtifactCompositionRules`, `ArtifactCompositionCatalog` |
| `verifyModuleDependencies`, `verifyModuleDependencyRule` | direction inward (`app` → `feature-*` → `core-*` → `core-domain`); `core-domain` has no outgoing edges; the rule must fire on a violating graph | `ModuleGraphRules.kt` |
| `verifyArtifactCompositionRule` | the manifest and dependency rules must fire on violating inputs | `VerifyArtifactCompositionRuleTask.kt` |
| `verifyAcceptanceSteps` | duplicate step definitions | `acceptance/build.gradle.kts` |
| Release signing | without secrets release variants are disabled and `assembleRelease`/`bundleRelease` fail clearly, not with debug signing | `ReleaseSigningConventionPlugin.kt` |
| `verifyAll` | `build` of all modules, `assembleDebugAndroidTest` of all Android modules (compile only), all gates above, `:detekt-rules:test`, `:acceptance:acceptanceRun`, `acceptanceChecklist`, `verifyAcceptanceSteps`, `acceptanceScreenList`, `acceptanceDeviceList`, `acceptanceJourneyList` | `ProjectChecksConventionPlugin.kt` |

Both composition gates are part of `assemble<Variant>`; the debug one is also part of `check`.

### 2.1 Build variants and network

- One flavor, `free`, and one variant per build type: `freeDebug` and `freeRelease`. The flavor is
  kept for its name, because those task names are part of the release pipeline and of the F-Droid
  build. No other variant of the product exists.
- There is no paid build, no store client and no entitlement code: a payment path does not exist in
  the tree, so no variant compiles one and no test covers one.
- The product has no network module. `INTERNET` and `ACCESS_NETWORK_STATE` are forbidden
  unconditionally, in the merged manifest and in every source manifest of every module, by
  `ArtifactCompositionCatalog` and `ArtifactCompositionRules`. `ACCESS_WIFI_STATE` and `AD_ID` are
  forbidden as before.
- GMS, Firebase, advertising and analytics SDKs are forbidden in the resolved runtime classpath and
  in the version catalog by the same rules.

## 3. Quality requirements

### 3.1 Reliability

| Requirement and threshold | Verification method | Status |
|---|---|---|
| At least 95% of reminders fire within ±2 min of `scheduled_at` over a continuous 14-day run on a configured device | `tools/spike/gate.py` over the journal export (0…+120 s, `outage` excluded); a person gives the verdict | manual |
| After reboot the nearest-window alarms are restored within 60 s of `LOCKED_BOOT_COMPLETED` before unlock and the full set within 60 s of `USER_UNLOCKED` | `DirectBootReplayPolicyTest.kt`; trial 1 | automated (partial) |
| In Doze a reminder fires within ±2 min | Trial 3 (forced idle), `AlarmManagerGatewayTest.kt` | automated, emulator (partial) |
| After a time zone change rescheduling happens within 30 s and the time of day is preserved | `SystemEventReceiverTest.kt`, `RescheduleRemindersUseCaseTest.kt`; trial 4 | automated (partial) |
| After a forced stop alarms are restored on the first launch | Trial 7; `@manual` whole-set check | automated, emulator (partial) |
| After any schedule change rescheduling completes before control returns to the interface, with no database/alarm mismatch | `RescheduleRemindersUseCaseTest.kt`, `AlarmManagerGatewayTest.kt` | automated (partial) |
| After every fired alarm the next one is scheduled; the queue never empties | `HandleFiredReminderUseCaseTest.kt` | automated |
| Granted and not granted permissions are distinguished on the permissions screen and in settings; delivery is checked by the self-check | `PermissionsScreenTest.kt`, `@screen` scenarios | automated (partial) |

### 3.2 Data safety

| Requirement and threshold | Verification method | Status |
|---|---|---|
| Room migrations lose no records on a year of intake: at least 4,000 `Intake`, 30 `Medication`, 4 `Profile` | `MigrationTest.kt`, `VolumeTest.kt`, `MigrationVolumeTest.kt` | automated, emulator (partial) |
| Concurrent marks leave exactly one record, the first by `acted_at`; sources `PHONE` and `AUTO` | `ConcurrencyTest.kt` (1,000 repeats), `IntakeLifecycleTest.kt` | automated, emulator |
| Under low storage the write is transactional, the refusal is shown, the database stays consistent | `IntakeMarkingTest.kt` (mid-transaction rollback of mark and stock) | automated, emulator (partial) |
| Export → clean install → import "replace all" restores all data, including soft-deleted medications | `@screen` transfer scenarios, `ReplaceAllDataTest.kt` | automated (partial) |
| No backup exists other than the transfer file, and the transfer screen says so | `DataTransferScreenTest.kt`, the string `transfer_principle` (`«облака нет — файл остаётся у вас»`, "there is no cloud, the file stays with you") | automated (partial) |

### 3.3 Privacy

| Requirement and threshold | Verification method | Status |
|---|---|---|
| The app sends no medical data anywhere; it has no network path | `verifyManifest`, `verifyDependencies`, `MedicalDataInJournal`, privacy scenarios | automated |
| No network permission appears anywhere: `INTERNET` and `ACCESS_NETWORK_STATE` are absent from the merged manifest and from every module manifest; `ACCESS_WIFI_STATE` and `AD_ID` never appear | `ArtifactCompositionRules`, `VerifyMergedManifestTask.kt` on the merged and module manifests | automated |
| The transfer file is created through the system picker, stays out of public directories, and needs no storage permission | `TransferScreenSteps.kt` against the source manifest | automated (partial) |
| The transfer file holds medical data in clear, without a password, and export warns about it | `DataTransferScreenTest.kt`, `transfer_privacy_content`, `transfer_privacy_place` | automated |
| Release logs carry no medication names, doses, profile names or transfer content; pre-unlock snapshots carry only identifiers, moments and service numbers | `MedicalDataInJournal` with a sample and firing gate | automated (partial) |
| The database is excluded from Android backup: `allowBackup="false"` | Declared in the manifest; no gate checks the attribute | none |

### 3.4 Network

| Requirement and threshold | Verification method | Status |
|---|---|---|
| The app declares no network permission and its merged manifest contains neither `INTERNET` nor `ACCESS_NETWORK_STATE` | `ArtifactCompositionRules`, `VerifyMergedManifestTask.kt` on the merged manifest | automated |
| No module manifest declares a network permission, so the permission has no source to come back from | `VerifyMergedManifestTask.kt` over every `src/**/AndroidManifest.xml` in the build | automated |
| No GMS, Firebase, advertising or analytics coordinate reaches the runtime classpath or waits in the version catalog | `VerifyDependencyCompositionTask.kt`, `ArtifactCompositionRules`, `gradle/libs.versions.toml` | automated |

### 3.5 Performance

| Requirement and threshold | Verification method | Status |
|---|---|---|
| Cold start to the first draw of Today: at most 1.5 s median, 2.5 s at the 95th percentile | `StartupFrameTest.kt` checks splash frame retention, not start time; no macrobenchmark | none |
| Today responds to marking an intake within 150 ms until the card state changes | `VolumeTest.kt`: a day query over 4,000 intakes stays within 150 ms | automated, emulator (partial) |
| Scrolling Today with 20 intakes and a month of history: at most 1% of frames longer than 16 ms | Neither a scrolling benchmark nor a frame test exists | none |
| Database size for a year of intake with five medications (about 3,650 `Intake`) at most 5 MB | `HistoryQueriesTest.kt` fills a year; the file size is not measured | none |
| Opening history with a monthly adherence calculation within 300 ms until the calendar is drawn | `HistoryQueriesTest.kt`: a month query stays within 150 ms | automated, emulator (partial) |

### 3.6 Energy

| Requirement and threshold | Verification method | Status |
|---|---|---|
| No foreground service and no persistent notification | A narrow check of journal rotation sources only; no gate. `SystemForegroundService` comes from `WorkManager` | none |
| Battery use per day at five intakes at most 1% by the system counter | No measurement tool; `dumpsys battery` serves the Doze trials | none |
| Handling a fired alarm uses at most 2 s of receiver CPU time, without ANR warnings | No test, scenario or trace; trials exercise the receiver only | none |

### 3.7 Accessibility

| Requirement and threshold | Verification method | Status |
|---|---|---|
| Every interactive area is at least 44 dp on both sides, including visually smaller elements | `TouchTargetTest.kt` (component catalog, both profiles), `LargeFontTest.kt` | automated (partial) |
| Every interactive element without visible text has a `contentDescription` in Russian | `MedsA11yScreenSteps.kt`, `TodayA11yScreenSteps.kt` | automated (partial) |
| At font scale up to 200% text is not clipped or overlapped, and screens stay passable to the final action | `LargeFontTest.kt`, `EnvironmentSnapshotTest.kt` | automated (partial) |
| Text and background pairs meet WCAG AA: 4.5 normal, 3.0 large, within the token table | `ContrastTest.kt` (25 pairs, tolerance 0.02, recorded exceptions) | automated |
| No status is conveyed by color alone: there is text, an icon or a label | `ComponentStatesTest.kt`, `ProfileIndicationSteps.kt` | automated (partial) |

### 3.8 Time and time zones

| Requirement and threshold | Verification method | Status |
|---|---|---|
| A time zone change does not shift the time of day: "08:00" stays "08:00" | `TimeTest.kt` (Moscow → Almaty and Chicago), `@timezone` scenarios | automated |
| Moving the clock forward does not turn `pending` into `missed` retroactively beyond the window rule; moving it back creates no duplicates | `ReminderWindowTest.kt`, `@clock` scenarios | automated |
| The daylight saving transition neither loses nor doubles an intake inside the shifted hour | No automated test; the `@manual` scenario has no steps | none |
| The day boundary is local midnight of the device profile, and Today switches day without a restart | `ObserveTodayAgendaUseCaseTest.kt`, `@midnight` scenarios; trial 9 | automated |

### 3.9 Build and compatibility

| Requirement and threshold | Verification method | Status |
|---|---|---|
| `minSdk 26`, `targetSdk = compileSdk = 35`; functionality unavailable on API 26 degrades instead of failing | `ProjectDefaults.kt`; `verifyManifest` checks `minSdk`; lint `NewApi`, `InlinedApi` at `error` | automated |
| `applicationId = ru.stohastika.sklyanka`, immutable | `VerifyMergedManifestTask.kt`: merged package name must match | automated |
| No network permission exists in any artifact: `INTERNET` and `ACCESS_NETWORK_STATE` never appear in a merged or module manifest; `ACCESS_WIFI_STATE` and `AD_ID` never appear | `ArtifactCompositionRules`, `verifyManifest<Variant>`; section 2.1 | automated |
| No GMS, Firebase or derived artifacts in dependencies | `verifyDependencies<Variant>`, `gradle/libs.versions.toml` | automated |
| Release is signed with the project keystore, with R8 and resource shrinking; without secrets release variants are disabled | `ReleaseSigningConventionPlugin.kt` | automated |
| Minimum version Android 8.0 (API 26), target current at release; APIs above 26 only behind a version check | As in the `minSdk` row | automated |
| Works without Google services: no scenario depends on GMS | `verifyDependencies`, `verifyManifest`; the full scenario on AOSP images | automated |
| The mandatory pre-release matrix is section 7 | `acceptanceChecklist`, `acceptanceDeviceList`, emulator regression | manual |

### 3.10 Localization

| Requirement and threshold | Verification method | Status |
|---|---|---|
| All texts live in `strings.xml`; no string literals in layouts or screen code | Lint `HardcodedText` at `error`; detekt `RussianTextInCode` | automated |
| Six languages: `ru` (base and fallback), `en-US`, `es`, `de`, `zh-CN`, `kk`; adding a language needs no screen code changes | `ProjectDefaults.INTERFACE_LOCALES`, `locales_config.xml`, `LocalizationResourcesTest.kt` | automated |
| The application chooses the interface language: a declared language is taken whole, an unknown device locale gets the Russian set whole | `InterfaceLocaleTest.kt`, `InterfaceLanguageAcceptanceTest.kt` (device locale `fr-rFR`) | automated |
| Plurals follow interface language rules; dates and times use platform formatting, and the 12/24 hour preference comes from the system | `InterfaceLanguageSteps.kt` (numerals 1, 2, 5, 11, 21, 101) | automated |

### 3.11 Code quality

| Requirement and threshold | Verification method | Status |
|---|---|---|
| Three test levels are mandatory: unit, instrumented (Room and migrations, receivers, channels, transfer file), scenario (all scenarios green); domain coverage at least 85% of lines | Levels run, section 1; `acceptanceRun`; no coverage tool | automated (partial) |
| The domain layer does not depend on Android and is tested on the JVM without an emulator | detekt `AndroidImportInDomain` (bans `android.*`, `androidx.*`, `dagger`, `javax.inject`); `core-domain` has no outgoing edges | automated |
| Color, font size, radius and padding literals outside the design system package are forbidden; a violation fails the build | detekt `HardcodedDesignToken`, `HardcodedTextStyle` (only `ru.stohastika.sklyanka.ui.token`) | automated |
| Time and time zone come from a substitutable source, not the system clock at the call site | detekt `DirectSystemClockAccess` bans `System.currentTimeMillis`, `*.now()`, `ZoneId.systemDefault`, `TimeZone.getDefault`, `Clock.system*`, `Calendar.getInstance` outside `platform.time` | automated |
| The build is reproducible and passes on a clean machine with one command | `./gradlew verifyAll`; CI `.github/workflows/verify.yml` on every push and pull request to main | automated |

### 3.12 Observability

| Requirement and threshold | Verification method | Status |
|---|---|---|
| The firing journal records scheduled and actual time, deviation, delivery outcome and reschedule events with a reason, using identifiers | `ReminderLogTest.kt`, `ReminderOnTimeRuleTest.kt` (0…+120 s, 7-day summary) | automated, emulator |
| The journal is kept at least 30 days, bounded (at most 2 MB, ring eviction) and reachable from settings: 7-day summary and file export | `ReminderLogRetentionTest.kt` (90 days, untouched 7-day window, 2 MB ceiling), `RotateReminderJournalUseCaseTest.kt` | automated (partial) |
| The diagnostics screen shows actual system state: notifications, exact alarms, battery optimization, the nearest alarm | `ReadPermissionChecklistUseCaseTest.kt`, `DiagnosticsScreenTest.kt` | automated (partial) |

## 4. Capabilities outside the product

No quality requirements exist for these capabilities; the gates in section 2 enforce their absence.

- **AI input.** No model runs on the device or over the network, and no screen offers an entry for free-form input.
- **Payment, purchase, subscription.** No payment entry point, no paid module, no store client and no entitlement code exist in the tree. The limit of five profiles is a product rule in `ProfileCreationGate`, not a paywall.
- **Wearable companion.** No Wear module, watch permission or watch dependency, and no screen promises a watch card.
- **Experiments, developer reporting and client analytics.** No A/B SDK and no analytics layer: no
  provider SDK, no event queue, no analytics consent step, no analytics settings row and no usage
  tracking. The app declares no network permission.
- **Cloud backup and accounts.** No account system and no server-side storage of user data; data leaves the device only as the transfer file.
- **Medical advice.** No treatment recommendations, no dose selection, no drug interaction checks.

## 5. Requirements without verification

Nothing verifies these requirements:

- `allowBackup="false"`: no gate checks the attribute.
- Cold start (1.5 s / 2.5 s), dropped scroll frames, database size (5 MB): no benchmark, frame test or file measurement.
- No foreground service on all paths: only journal rotation sources are checked; battery use (1% per day) and receiver CPU time (2 s): nothing measures them.
- An intake inside a daylight saving shift: no automated test, the `@manual` scenario has no steps; domain coverage of 85%: no coverage tool; journal export from the interface: the entry point is absent.

A verification exists, but the threshold is not covered in full: reboot recovery (the 60 s threshold), Doze (one firing instead of 20), time zone change (30 s only on an emulator), forced stop (only the `outage` record, not the whole alarm set), schedule change (no pending-vs-alarm comparison), migrations (small row counts, year scale separate), low storage (no store raising `SQLiteFullException`), transfer (no before/after snapshot comparison; no "no backup exists" wording; source manifest only; public directory exclusion manual), release logs (release `logcat` and device-protected storage), screen response and opening time (database query only, not response and drawing), accessibility (components and key screens, not a full walk; Scanner and color blindness simulation manual).

## 6. What is not measured and why

- **CI covers the JVM level.** `.github/workflows/verify.yml` runs `verifyAll` on every push and pull request to main. Instrumented tests are only compiled; they run separately through `android-test.yml`.
- **Coverage is not collected.** No coverage tool exists, so 85% is not verified: the test count is known, the covered share is not.
- **There are no benchmarks.** No macrobenchmark module and no baseline profiles, so cold start, frame timing, database size and battery use are not measured. Screen response and history opening are covered by database query timing only, which verifies the data layer, not the interface.
- **Product success indicators** are measured only by manual runs and the on-device journal. Adherence is the share of planned intakes taken within ±2 h over 30 days, target at least 80%. Retention is 30 days of personal use, checked by a personal run and the store console. Reminder reliability is the 14-day, 95%, ±2 min gate.
- **Everything firmware dependent** cannot be reproduced on an emulator: battery optimization and autostart, extreme power saving, a full screen reminder over the lock screen, channel sound, actual Doze delivery, the long gate run. Trials 12, 13 and 16 are not replaced by an emulator; trials 2, 8 and 15 only in part.
- **The reliability gate is not passed.** A continuous 14-day run on matrix devices has not been completed, and the arithmetic rule for the `outage` window is not fixed before a run starts.

## 7. Device matrix and manual run

**The emulator covers everything firmware independent:** domain, screens, snapshots, the six-language matrix, acceptance scenarios, trials 1 to 11, 14 and 15 (2, 8 and 15 only in part), on API 26, 31 and 35.

**Matrix devices cover only firmware-dependent behavior:** battery optimization and autostart, a full screen reminder over the lock screen, channel sound, recovery before unlock, actual Doze delivery, the long gate run.

| Level | Devices | What is promised |
|---|---|---|
| Verification matrix | Composition not fixed yet; candidates are Xiaomi, Samsung and Huawei devices | The full scenario and the reliability gate on every matrix device; a defect reproduced on a matrix device blocks release |
| Must work | Any phone on Android 8.0+ with a Russian locale; HMS and GMS not required | Installation and complete operation. Reminder reliability depends on the power management of a specific firmware and is not guaranteed without manual setup; defects are accepted but do not block release |
| Not verified | Android below 8.0; tablets and foldables; Android TV, Auto, Wear OS; watches and wearables; devices with GMS as targets | Compatibility is neither claimed nor tested |

Manual run rules:

1. **Manual scenarios need no protocol.** They are listed by `acceptanceChecklist` (`@manual`) and `acceptanceDeviceList` (`@device`) and presented during operation by the owner. Unchecked work stays visible in these lists.
2. A run yields the journal export and the numbers from `tools/spike/gate.py`. The reliability verdict is given by a person, together with fixing the matrix composition and the `outage` arithmetic rule.
3. A scenario that could not be checked stays unverified; "done" requires a run on a device.
