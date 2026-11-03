# Scripted visual run

This harness lives on top of the localization contact sheets: it captures a run, approves a
baseline, and shows what changed, without reading Gradle commands or comparing a hundred
images by eye. It covers the localization matrix in `docs/spec/quality.md` §1: six interface
languages across the screens.

**The boundary to keep in mind.** The sheets are drawn by Robolectric, a simulation of
Android, not by EMUI. Fonts, CJK, system insets and vendor behavior are not checked on the
JVM; a match with the baseline does not prove correctness, it only records that nothing
changed. A contact sheet is a review artifact, not a product check. The Roborazzi baselines
in `*/screenshots/` are a separate mechanism and are not touched by this harness.

## 1. Short cycle

```bash
cd tools/visual

./capture.sh                          # capture a run (the name is a timestamp)
./capture.sh --run i18n-check         # capture a run with a name
./capture.sh --run i18n-check --force # replace an existing run

./approve.sh i18n-check               # after review: make the run the baseline
./diff.sh                             # compare the latest run with the baseline
./diff.sh i18n-check                  # compare a specific run
```

`diff.sh` prints the report path and returns a code: `0` means no differences, `1` means
there are `changed`, `new` or `missing` sheets, and `2` means a refusal (baseline not
approved, no runs, run not found). The report and the difference images are written under
`<run>/diff/`.

A run must be reviewed before `approve.sh`: by a person or by a visual verifier. Approving a
baseline that nobody looked at legalizes a defect.

## 2. Layout

| Path | What it is | In git |
|---|---|---|
| `tools/visual/runs/<name>/` | a captured run: PNG per language, `manifest.sha256`, `meta.txt` | no, ignored |
| `tools/visual/runs/<name>/diff/` | `report.md` and difference images | no, ignored |
| `tools/visual/baselines/<language>/*.png` | approved baselines | no, ignored |
| `tools/visual/baselines/manifest.sha256` | baseline manifest: sha256 and path | **yes** |
| `app/build/visual-review/<language>/*.png` | fresh sheets from the generator test | outside git (`build/`) |

**Why baseline PNGs are local.** The images weigh tens of megabytes and are not readable in
a review diff. Git carries the manifest: it shows that the baseline changed and makes a PNG
edited around `approve.sh` noticeable, because the manifest then disagrees with the files and
`diff.sh` says so under "Notes".

The manifest format is one line per sheet, `<sha256>  <relative path>`, sorted by path:

```
9f2c…  de/splash.png
1a04…  en-US/history.png
```

`meta.txt` records the run name, capture time, git revision, Java version, sheet count and
the language list.

## 3. What each command does

- `capture.sh [--run NAME] [--force]` deletes the previous sheets in
  `app/build/visual-review`, runs **only** `LocalizationContactSheetTest` through
  `:app:testFreeDebugUnitTest` with `-Psklyanka.visualReview=true` and `--rerun`, copies the
  result to `runs/<name>`, writes the manifest, and checks that the manifest line count
  matches the PNG count. An existing run is not overwritten without `--force`.
- `approve.sh <run>` replaces the baseline as a whole: the PNGs are copied to `baselines/`
  keeping the language subdirectories, and the manifest is rebuilt from the files. Extra
  PNGs of the previous baseline disappear; otherwise a removed screen would stay in
  `missing` forever. It warns when the run manifest disagrees with its files.
- `diff.sh [<run>]` recomputes the hashes of both sides (a PNG edited by hand must be
  found), classifies the sheets as `changed`, `new` or `missing`, and runs a pixel
  comparison of changed sheets when a tolerance tool is available. Without an argument the
  latest run is chosen by the capture time in `meta.txt`, with the directory time as a
  fallback and a warning; writing `diff/` does not make an old run "latest".

## 4. Tolerance tool

| Tool | When it is used | What it gives |
|---|---|---|
| `python3` + Pillow | when `python3 -c "import PIL"` succeeds | changed pixel count, share, maximum channel difference; `diff/<language>/<screen>.diff.png` with the significant pixels highlighted in red |
| `magick` or `compare` (ImageMagick) | when Pillow is absent | `compare -metric AE -fuzz <threshold>%` and a difference image |
| none | when neither is available | sha256 comparison only; the report says the tolerance was not applied |

The default tolerance is 8 out of 255 per channel (`SKL_PIXEL_TOLERANCE`), which filters
anti-aliasing and rounding noise. Change it through the environment:

```bash
SKL_PIXEL_TOLERANCE=16 ./diff.sh i18n-check
```

A size mismatch (for example, a state whose height changed) is not compared pixel by pixel:
`pixel_diff.py` saves a vertical pair of baseline and current sheet and marks the difference
as significant. The tool can also be called directly with `--baseline`, `--current`, `--out`
and `--threshold`.

## 5. Traps

- **`./gradlew clean`** erases `app/build` together with the fresh sheets, but not `runs/`
  or `baselines/`: captured runs and approved baselines survive a clean.
- **A change of Robolectric, Roborazzi or stand fonts** changes the hashes of every sheet at
  once. That is not a layout defect: such a diff is reviewed and the baseline is approved
  again deliberately, with the reason in the manifest commit.
- **A repeated capture without `--force`** refuses on purpose: the run may already have been
  reviewed and approved.
- **The manifest is tiny, the PNGs are large.** When moving baselines to another machine,
  copy the whole `baselines/` directory; otherwise `diff.sh` reports the entire baseline as
  `missing`.
- **Java.** Java 17 or newer is required. The scripts look in `JAVA_HOME`,
  `/usr/libexec/java_home`, Homebrew (`openjdk@17`, `openjdk@21`, `openjdk`) and `PATH`, and
  refuse before Gradle with a hint when nothing is found.

## 6. Common failures

| Symptom | Cause | What to do |
|---|---|---|
| `Java not found` | no `JAVA_HOME` and no JDK 17+ in the usual places | set `JAVA_HOME`, for example `export JAVA_HOME=/opt/homebrew/opt/openjdk@21` |
| `generator test not found` | the contact sheet test was renamed or removed | restore the test or update `SKL_CAPTURE_TEST_FILE` and the filter in `config.sh` |
| `sheet directory is empty` | the review property did not reach the test or the test failed | read the Gradle output; check `-Psklyanka.visualReview=true` in `config.sh` |
| `run already exists` | the name is taken | choose another name, or `--force` when the previous run is not needed |
| `baseline is not approved` | `approve.sh` has not run yet | `./approve.sh <run>` after review |
| `no runs` | `capture.sh` has not run | `./capture.sh` |
| the diff reports the whole baseline as `missing` | baselines were moved or cleaned | restore `baselines/` from the local copy and approve the run again |

## 7. Maintenance

- All parameters are in `config.sh`: the Gradle task, the test filter and file, the review
  property, the directories and the tolerance. Do not duplicate them in the scripts.
- Syntax is checked with `bash -n` on every script and
  `python3 -m py_compile pixel_diff.py`.
- A new shared step goes into `lib.sh`, not as a copy in three scripts.
