# Emulator run

This harness drives the device matrix and the 16 trials on Android emulator images and
collects the numbers. It is meant for a person sitting at a machine with an emulator who
wants a run, not a design document.

**The boundary to keep in mind.** The emulator runs AOSP, not EMUI. It has no vendor power
management and no autostart setting, so trials 12 and 13 cannot be reproduced on it at all,
and trial 16 (a continuous 14-day run) is not replaced by an accelerated run. The device
matrix in `docs/spec/quality.md` §7 is not covered by the emulator either. The reliability
gate, at least 95 percent of reminders on time over 14 continuous days on a configured
device, stays with a real reference device; `tools/spike` computes its numbers. The emulator
checks the mechanics and filters implementation defects before the hardware run.

## 1. Host prerequisites

| Item | Why | How to check |
|---|---|---|
| Hardware acceleration | without it an image takes tens of minutes to boot and volume checks hit timeouts | Linux: `ls -l /dev/kvm` and write access (`sudo usermod -aG kvm $USER`); macOS: HVF is on by default |
| Matching architectures | the emulator executes only a guest it ships qemu for: an x86_64 image does not start at all on Apple Silicon | `uname -m`; the matrix picks the ABI for the host (`config.sh`, `skl_host_abi`) |
| Android SDK | `adb`, `avdmanager`, `sdkmanager`, `emulator` | `./avd.sh list` does not fail with "SDK not found" |
| SDK path | the scripts read it from `ANDROID_SDK_ROOT`, `ANDROID_HOME`, `sdk.dir` in `local.properties`, or common install locations | `echo $ANDROID_SDK_ROOT` |
| SDK packages | `platform-tools`, `emulator`, the system images of the matrix | `./avd.sh create` installs what is missing |
| Disk space | several gigabytes per image: the system image plus a 4 GB data partition | `df -h $HOME/.android` |
| Python 3 | journal analysis, run summaries, seed generation, contact sheet | `python3 --version` |

`config.sh` is the single place that names the API levels, the image candidates and what each
image is for (`skl_image_candidates`, `skl_image_purpose`). The matrix is API 26 (minimum
supported level), 31 (the boundary where expedited work and the special exact alarm access
appear) and 35 (`targetSdk` and `compileSdk`). Images are without Google APIs: an image with
Play Services would test a different product and forbids the `adb root` some trials need.
The emulator matrix does not cover firmware-dependent behavior, so the vendor matrix stays a
separate manual run.

## 2. Commands

```bash
cd tools/emulator

./run-all.sh                 # full run: the whole matrix, the mechanical groups, results collected
./run-all.sh --fast          # quick run: the first image of the matrix, the fast group only
./run-all.sh --api 35 --keep # one image, left running after the run

./avd.sh create              # create the matrix images (idempotent)
./avd.sh list                # what exists and what is running
./avd.sh delete 35           # remove an image
./start.sh 35                # boot an image headless and wait until it is ready
./install.sh 35              # build and install the debug build over the previous one
./run-tests.sh 35 fast       # groups: fast | data | heavy | migration | journey | ui | all
./seed.sh 35 --mode day      # synthetic load through a transfer file (one manual step)
./seed.sh 35 --mode direct   # the same load written straight into the app database
./notify-watch.sh 35         # independent confirmation of notifications, in another terminal
./trials.sh list             # trial breakdown: what is automated and by what
./trials.sh auto 35          # the quick automated trials, in sequence
./trials.sh i3 35            # one trial
./collect.sh 35              # pull the journal and compute the run numbers
./restore-stand.sh 35        # put the image locale and font scale back after an aborted run
./stop.sh --all              # stop everything

./run-journeys.sh --happy    # positive human journeys plus the survival relay;
                             # frames in build/emulator/api35/ui-screens/,
                             # contact sheet in build/emulator/api35/happy-contact-sheet.png
./run-ui.sh                  # screen run in one command: a window, Compose checks, frames
./run-ui.sh --keep           # same, but the image stays running so the window remains visible
./run-tests.sh 35 ui         # only the ui group: Compose checks on the real platform
./screenshots.sh 35          # pull the frames of a screen run from a running image
```

All scripts are idempotent: an existing image is not recreated, a running image is not
started twice, and the installation goes over the previous one. Image data is **not wiped**
on boot because trials 1 and 10 live across reboots; wipe explicitly with
`./start.sh 35 --wipe`.

`run-all.sh` runs the mechanical groups only, `fast data heavy migration`, on every image of
the matrix (or one image with `--api`). Journeys and the screen group are separate commands
because they take minutes and need a window or a fresh application state.

`./trials.sh auto` runs the trials that do not wait for hours: 1, 3, 4, 5, 6, 11, 14 and 15.
Trials 2, 7, 8, 9, 10 and 16 are run one by one with `./trials.sh iN`, because they wait for
a scheduled moment, a reboot or a long run. Trials 12 and 13 exit with code 3 on an emulator
by design; trial 16 runs accelerated but never counts as the gate.

## 3. Screen run

`./run-ui.sh [API] [--keep] [--reuse]` is one command: the image boots **with a visible
window** (API 35 by default, the `targetSdk` level), a fresh debug build is installed, the
Compose checks of the `ui` group run on the real `MainActivity` and the real application
graph, and the frames are pulled to the host.

- `--keep` leaves the image running. `--reuse` keeps application data; by default the run
  installs with `--fresh`, so frames cannot show records left by someone else.
- Step by step the same thing is `./start.sh 35 --window` → `./install.sh 35` →
  `./run-tests.sh 35 ui` → `./screenshots.sh 35`.
- Group reports: `build/emulator/api35/androidTest-results/ui/`; the output is
  `tests-ui.log`.
- Frames: `build/emulator/api35/ui-screens/<state>__<test>.png` plus `manifest.txt`, an
  inventory of path and bytes. A frame is **evidence for a person, not a baseline**: no
  frame is compared with a sample, there are no hashes or thresholds, and frames stay in
  `build/`, out of git.
- **A headless boot remains the default.** Only the `--window` flag opens a window;
  `run-all.sh` and the mechanical groups work as before, and `-no-window` stays in
  `config.sh`.
- Frame language follows the device locale because the application follows the phone
  locale. An image with `en-US` therefore produces English frames; set the image locale to
  `ru-RU` before the run when a Russian frame is wanted.
- **Check state is controlled.** The checks run on a test Hilt graph: the product
  application class does not start, the clock is fixed by `DeviceTestClock`, and each check
  seeds its own state. A rerun gives the same result regardless of the data left on the
  image and of the check order.
- **Device log and summary.** The whole run is streamed to
  `build/emulator/api<API>/logcat-ui.txt` and survives a failing check. A summary of checks,
  failures, skipped checks and time is printed and written to
  `build/emulator/api<API>/ui-summary.txt` from the AGP reports. The summary needs
  `python3`, but its absence does **not** stop the run: a warning and the reason in
  `ui-summary.txt` are written, the image is stopped, and the exit code is the exit code of
  the checks.

The boundary is the same as for the whole emulator run: **AOSP, not EMUI**. Vendor
rendering, notification sound and the lock screen stay with a real device, and neither the
firmware matrix nor the reliability gate is closed by a screen run. A frame of the
application window does not show system bars or firmware styling.

## 4. Journeys

`./run-journeys.sh [API] [--keep] [--happy] [--class <full class name>]` runs the journeys
on the emulator. `--happy` limits the run to the positive human journeys and the survival
relay; `--class` runs one journey class for debugging.

The script boots the image with a window, grants permissions, installs with `--fresh`, and
then runs instrumentation in phases, each as a separate application start: the positive and
hostile journeys; phase one of the survival relay; phase two against a fresh process; the
"permissions not granted" journey; and the hostile locale and font scale pass, after which
the values are restored. The journey storage argument is passed to every phase, so a journey
that loses the real file storage fails instead of silently falling back to memory.

Reports land in `build/emulator/api<API>/androidTest-results/journey-<phase>/`, logs in
`tests-journey-<phase>.log`, and the summary in `journey-summary.txt`. A `--happy` run also
builds `happy-contact-sheet.png` from the key frames when `python3` and Pillow are present;
the contact sheet is for the eye and does not change the exit code. An interrupted run can
leave the image in a foreign locale at a raised font scale; `./restore-stand.sh` puts the
defaults back and fails when it cannot.

## 5. Where the results are

Everything under `build/emulator/api<API>/`:

| File | What is there |
|---|---|
| `results.json` | machine-readable result: deviation for every reminder, the on-time share, outages, silent failures |
| `summary.txt` | the same numbers in a human summary, plus the boundary of the run |
| `androidTest-results/<group>/` | AGP reports for the instrumented checks (XML) |
| `tests-<group>.log` | the output of a group run |
| `tests-journey-<phase>.log` | the output of each journey phase |
| `platform.txt` | the platform version of the image; a result without it does not travel |
| `trial-marks.tsv` | trial marks: what was done to the image and when |
| `notifications.tsv` | independent confirmations that a notification was shown |
| `journal/sklyanka.db` | the pulled copy of the reminder journal |
| `logcat.txt`, `dumpsys-alarm.txt`, `dumpsys-deviceidle.txt` | material for diagnosing a failure |
| `logcat-ui.txt` | the device log of the screen run, streamed for the whole run |
| `logcat-journey.txt` | the device log of the journey run, streamed for the whole run |
| `ui-summary.txt` | screen run summary: checks / failures / skipped / time |
| `ui-screens/` | frames of the screen and journey runs, with `manifest.txt` |
| `journey-summary.txt` | journey run summary, per phase and against the time budget |
| `happy-contact-sheet.png` | contact sheet of the positive journeys |
| `emulator.log`, `emulator.pid` | emulator output and the process id of the running image |

## 6. Synthetic load

There are two paths, and the choice between them is not about convenience.

**Through a transfer file** with `./seed.sh 35 --mode day` (or `--mode fast`). The script
pushes the file to `/sdcard/Download/sklyanka-seed.json`, and a person imports it in the
application through **Settings → Data transfer → Import** (the label follows the device
locale). There is no product entry point for the stand, and adding one for a run is separate
product work. The file contains five medications with eight intakes a day; `--mode fast`
keeps the eight intakes but uses a step of minutes instead of hours.

**Straight into the database** with `./seed.sh 35 --mode direct`. This is needed where the
first path is impossible: the `aosp_atd` image has no system file picker at all, because
`DocumentsUI` is not part of the image cut for automated tests, so the "Select file" button
has nothing to open. The mode writes the course into the application database directly and
starts the application; **the product itself sets the alarms** while rebuilding its window,
so the real scheduler is measured, not a substitute. The mode does not touch the reminder
journal: the numbers still come from what the application recorded.

## 7. Common failures

| Symptom | Cause | What to do |
|---|---|---|
| `SDK not found` | neither environment variables nor `sdk.dir` | `export ANDROID_SDK_ROOT=...` |
| `no emulator in the SDK` | the package is not installed | `./avd.sh create` installs it; manually, `sdkmanager emulator` |
| `image did not boot within 600 s` | no hardware acceleration or little memory | check `/dev/kvm`, raise `SKL_BOOT_TIMEOUT` in `config.sh`, free disk space |
| the emulator is up, `adb devices` is empty | the port is taken by another emulator | `./stop.sh --all`, then `adb kill-server` |
| `connectedFreeDebugAndroidTest` sees no device | Gradle picked another serial | the scripts set `ANDROID_SERIAL`; manually, `ANDROID_SERIAL=emulator-5560` |
| migrations fail with "no schema" | schema assets were not built | schemas live in `core-data/schemas` and are wired in `core-data/build.gradle.kts`; rebuild `:core-data:assembleDebugAndroidTest` |
| `could not pull the application database` | the application never started | `./install.sh`, launch the application, repeat `./collect.sh` |
| a trial exits with code 3 | the scenario does not reproduce on an emulator (trials 12 and 13) | this is not a failed run: those trials are measured only on a real device |
| the clock does not change | the image has Google APIs and `adb root` is refused | recreate the image from the matrix without GMS |
| "Select file" opens nothing | the image has no `DocumentsUI` (`aosp_atd`) | load with `--mode direct`; the application reports the refusal instead of crashing |
| a time zone change does not reschedule | auto synchronization set the zone back | `./start.sh` disables `auto_time` and `auto_time_zone`; check `settings get global auto_time` |
| `run-ui.sh` does not open a window | the machine has no display or the window is unavailable | the checks do not need a window: `./start.sh 35` (without `--window`), then `./run-tests.sh 35 ui` and `./screenshots.sh 35` |
| `ui` group: zero checks | the check package did not match or `androidTest` did not build | check `SKL_GROUP_UI_PACKAGES` in `config.sh` and `:app:assembleFreeDebugAndroidTest` |
| `screenshots.sh`: "no frames" | the checks did not run or did not reach frame capture | repeat `./run-tests.sh 35 ui`, read `tests-ui.log`, check that `:app:connectedFreeDebugAndroidTest` ran instead of failing during install |
| `python3 not found: summary not built` | no `python3` in `PATH` | install `python3`; the run, the frames and the exit code are unaffected |
