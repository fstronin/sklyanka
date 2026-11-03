# Deliberate rule violations

This directory is **not part of the build**: it is not a module, it is not compiled, and it
does not enter any artifact. It holds Kotlin fragments, each of which violates exactly one
project rule. The gate that uses them is described in `docs/spec/quality.md` §2.

Why the samples exist: without them, "the rule works and there are no violations" cannot be
told apart from "the rule never fires". A silent rule is worse than no rule, because it
creates the appearance of control.

| Rule | Sample |
|---|---|
| `HardcodedDesignToken` | `kotlin/ru/stohastika/sklyanka/violations/HardcodedDesignTokenSample.kt` |
| `HardcodedTextStyle` | `kotlin/ru/stohastika/sklyanka/violations/HardcodedTextStyleSample.kt` |
| `RussianTextInCode` | `kotlin/ru/stohastika/sklyanka/violations/RussianTextInCodeSample.kt` |
| `DirectSystemClockAccess` | `kotlin/ru/stohastika/sklyanka/violations/DirectSystemClockAccessSample.kt` |
| `DestructiveRoomMigration` | `kotlin/ru/stohastika/sklyanka/violations/DestructiveRoomMigrationSample.kt` |
| `AndroidImportInDomain` | `kotlin/ru/stohastika/sklyanka/domain/violations/AndroidImportInDomainSample.kt` |
| `MedicalDataInJournal` | `kotlin/ru/stohastika/sklyanka/violations/MedicalDataInJournalSample.kt` |

## How it is checked

```
./gradlew verifyStaticAnalysisRules
```

The task runs detekt over `tools/violations/kotlin` with the project configuration and
requires **every** project rule to produce at least one finding. When a rule stays silent,
the run fails and names it. The per-rule result is written to
`build/reports/detekt/rule-enforcement.txt` and printed as `FIRED` or `SILENT` with the
finding count. The task is part of `check` and `verifyAll`.

## See the findings themselves

```
./gradlew detektViolationSamples
cat build/reports/detekt/violation-samples.txt
```

`detektViolationSamples` is the detekt run itself: it is configured to keep going after the
expected findings, so its machine-readable report
(`build/reports/detekt/violation-samples.xml`) can be inspected. `verifyStaticAnalysisRules`
depends on it and makes the judgment.

## Adding a sample

Add one Kotlin file that breaks exactly one rule. When the rule is new, add its name to
`PROJECT_RULES` in
`build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/ProjectChecksConventionPlugin.kt`
and declare it under the project rule set in `config/detekt/detekt.yml`. Two rules in one
file make the check unable to say which rule fired; a sample that breaks nothing makes the
gate fail for the wrong reason.
