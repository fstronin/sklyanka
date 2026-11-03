# Reminder delivery spike

The stand does not live in the application: it measures the product build, and this directory
holds only what prepares the load, pulls the journal, and computes the numbers. The gate it
feeds is defined in `docs/spec/quality.md` §3.1; the thresholds it uses are listed in
`docs/spec/behavior.md` §3.8.

| File | Role |
|---|---|
| `gate.py` | formal stand: computes the reliability gate numbers (on-time share, deviations, outages, silent failures, breakdown by scenario); writes `results.json` and `summary.txt` |
| `collect.sh` | pulls the journal (an exported CSV or a database copy over adb) and runs the stand |
| `mark.sh` | marks the start and the end of a scenario by the device clock |
| `probe.sh` | short delivery probe on a Huawei device: schedules probes through the product mechanism, reproduces an environment scenario, and writes a report |
| `personal.py` | personal pilot: computes numbers from a CSV without a synthetic grid (intake classes, outages, restored gaps in regular series, outliers); writes `results.json` and `summary.txt` |
| `personal-sample.csv` | sample export for the self-check of `personal.py` |
| `test_personal.py` | self-check of `personal.py` against the sample, with numbers counted by hand |
| `seed-stand.json` | transfer file with the stand load: five medications, eight times a day |
| `config.sh` | single place for the stand parameters: the load size and the seed file path |

## 1. Compute the gate numbers

```bash
# From a CSV exported by the application (the main path, no computer connection needed)
./collect.sh --csv ~/Downloads/sklyanka-reminder-journal-2026-10-15.csv \
    --platform "Huawei ..., EMUI 15.0, Android 14" --out build/spike

# Fallback: pull a database copy from a connected device (debug build or emulator)
./collect.sh --serial FMR0219A30001234 --out build/spike

# Scenario marks during short visits
./mark.sh 1 start
./mark.sh 1 end
```

`collect.sh` flags:

| Flag | Meaning |
|---|---|
| `--csv FILE` | journal exported by the application; the main path |
| `--serial S` | device serial for the database fallback; by default the only connected device |
| `--out DIR` | result directory, `build/spike` by default |
| `--platform TEXT` | platform line written into the report; read from the device when omitted |
| `--marks FILE` | scenario marks; `DIR/marks.tsv` by default, used when present |
| `--zone IANA` | device time zone for the expected grid; read from the device when omitted |
| `--times "HH:MM,..."` | slot times of the load; `SPIKE_TIMES` from `config.sh` by default |
| `--per-slot N` | how many intakes each slot must contain; `SPIKE_MEDICATIONS` by default |
| `--run-kind TEXT` | run name for the summary |

The script writes the platform line to `<out>/platform.txt` and then calls `gate.py`. The
same calculation can be run directly when the files already exist:

```bash
python3 gate.py --csv ~/Downloads/journal.csv --out-dir build/spike \
    --platform "Huawei ..., EMUI 15.0, Android 14" --zone Europe/Moscow
```

`gate.py` takes exactly one journal source, `--csv` or `--db`, and requires `--out-dir`. It
also accepts `--marks`, `--platform`, `--run-kind`, `--times`, `--per-slot` and `--zone`.
It applies the 95 percent threshold and checks the 14-day span with one day of tolerance for
the partial first and last days, and writes both numbers into `results.json`. It never
declares the gate passed: the decision belongs to a person after a continuous 14-day run on
a configured reference device. Output is `results.json`,
`summary.txt`, and the same summary on standard output.

`mark.sh CODE STATE` records a mark in `build/spike/marks.tsv` by default (or `--file FILE`),
using the device clock unless `--at "2026-10-03T10:00"` is given. Any code can be used; the
report groups reminders by the marks around them, so start and end must share the code.
`collect.sh --marks FILE` points the calculation at such a file. Without marks the report has
no per-scenario breakdown.

## 2. Personal pilot

```bash
python3 personal.py --csv ~/Downloads/sklyanka-reminder-journal-2026-10-08.csv \
    --zone Europe/Moscow --platform "Pura 80 Ultra, EMUI 15.0.x, AOSP 12" \
    --out-dir build/personal

python3 test_personal.py
```

`personal.py` computes from a CSV only; a database copy is not accepted. It takes
`--csv`, `--platform`, `--run-kind`, `--zone`, `--from`, `--to`, `--no-inference`, and
`--out-dir` (default `build/spike-personal`). Because a person's schedule is not the
synthetic grid, expected moments come from the journal itself: regular series are
medication plus time of day, and missing intakes inside a series are counted as not
occurring. `--no-inference` disables that restoration and uses only journal records.
Output is `summary.txt` and `results.json`.

`test_personal.py` needs no arguments and no third-party packages. It runs the tool end to
end over `personal-sample.csv` and compares the numbers with the values counted by hand.

## 3. Short delivery probe

`probe.sh` answers a different question from the gate: whether delivery works in the current
environment, within minutes, after a change of vendor settings. It schedules probes through
the real application mechanism and reproduces a scenario around them.

```bash
./probe.sh --install --scenario doze --out build/probe
./probe.sh --scenario force-stop --any-device
```

| Flag | Meaning |
|---|---|
| `--serial S` | device serial; by default the only connected device |
| `--install` | build `:app:assembleFreeDebug` with `-Psklyanka.reminderMeasurement=true` and install it with `-r -g` |
| `--strategy alarm_clock\|exact_idle` | measurement strategy written into the application |
| `--probes +2,+5,+10` | probe offsets in minutes from scheduling |
| `--wait-min N` | wait after the last probe before collecting artifacts, default 5 |
| `--quick` | short smoke: probes `+1,+2`, wait 2 minutes, no journal |
| `--scenario none\|freeze\|kill\|standby\|doze\|force-stop` | environment reproduced around the probes |
| `--out DIR` | artifact directory; `build/spike/probe-<timestamp>` by default |
| `--csv` | also write `probes.csv` |
| `--any-device` | do not require Huawei/EMUI; measure mechanics instead of the vendor environment |
| `--open-emui` | open the vendor startup screen and exit |

Artifacts in `--out`: `report.md`, `probe.log`, `alarm-scheduled.txt`, `alarm-after.txt`,
`notification.txt`, `platform.txt`, `probes.csv` with `--csv`, and `journal/` unless
`--quick`. Exit code 0 means every probe arrived on time (for `force-stop`, the documented
expected refusal); 1 means the run failed or probes were not delivered. The script does not
change vendor settings: it opens the screen and a person sets them once.

## 4. Stand load

`seed-stand.json` is a transfer file with one profile and five medications, the same load the
gate grid expects (`config.sh`: `SPIKE_MEDICATIONS`, `SPIKE_TIMES`). Import it in the
application through **Settings → Data transfer → Import** (the label follows the device
locale). `config.sh` names the file path once, as `SPIKE_SEED`.

## 5. What the journal contains

`gate.py` reads identifiers, moments and service codes only: no column of the journal holds
medical data, and `notify-watch.sh` in the emulator harness records only a notification key.
The probe log is filtered to its own tag, and the probe report is built from delivery
artifacts. The numbers describe delivery, not the person.
