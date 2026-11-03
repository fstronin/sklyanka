# Sklyanka: repository conventions

This file is the entry point for any agentic tool working in this repository. The project
conventions are the same for a human contributor and for an agent.

Sklyanka is an Android medication tracker. The interface is Russian. The product is free in
full, works without a network and does not ask for an account.

## Where things live

| Path | Content |
|---|---|
| `README.md` | what the product is, how to build and test it |
| `docs/README.md` | the documentation layer and its rules |
| `docs/spec/` | the normative specification: product, behavior, design, quality, architecture, glossary, privacy |
| `docs/adr/` | architecture decision records `0001` to `0017` |
| `acceptance/src/main/resources/features/` | Gherkin acceptance scenarios, grouped by topic |
| `design/` | the customer's original specification and the mockups; untouched, Russian |
| `app/`, `core-*`, `feature-*`, `billing-*` | the code |
| `build-logic/` | Gradle convention plugins and the project checks |
| `tools/` | the emulator and visual harnesses |

## Stack

Kotlin, Jetpack Compose, Room, Hilt, AlarmManager. Minimum API 26. Google services (GMS,
Firebase) are forbidden by an artifact composition check. See `docs/adr/0001-stack.md`.

## Language

- Documentation, identifiers, test names, comments and commit messages: American English.
- The interface: Russian, with translations into English, Spanish, German, Chinese and Kazakh.
- Acceptance scenarios and their step patterns: Russian, because they assert the Russian
  interface and Russian demo data verbatim.
- Domain terms are used exactly as `docs/spec/glossary.md` defines them.

## Rules that are not negotiable

- **Reliability of a reminder comes before everything else.** A missed reminder is a product
  failure, not a convenience defect.
- **Profiles never mix.** The interface is always in the context of exactly one profile.
- **Accounting works without a network, always.** Accounting, reminders, marks, history and the
  report do not depend on a network, an account or an external service.
- **Health data does not leave the device in the clear.** The analytics contour needs an explicit
  consent and sends no medical data. Ads and data sale are out of scope.
- **The app is not a medical device.** No treatment recommendations, no dose selection, no
  drug-interaction checks.
- **Design tokens are never hardcoded.** Colour, typography, geometry and motion come from
  `core-ui/token/`; the detekt rules enforce it.

## Order of work

1. Read the specification of the area: `docs/spec/behavior.md` (behaviour) or
   `docs/spec/design-system.md` (visual language). Section numbers are cited by Kotlin KDoc, so
   a rule is found by its number.
2. Read the acceptance scenario of the area in `acceptance/src/main/resources/features/`.
3. Write the failing scenario or test first, then the code, then refactor.
4. A change is done when `./gradlew verifyAll` is green and the manual checks of the area have
   been done on an emulator or a device.

## Shared resources: one writer at a time

- **The Gradle build.** Two implementations in one working tree break compilation. Run one build
  at a time.
- **The snapshot baselines** of `tools/visual/`. Re-record them only in the dedicated order.
- **The acceptance step vocabulary.** A step pattern and a scenario line must match exactly;
  `:acceptance:verifyAcceptanceSteps` checks it and names both declaration points of a duplicate.

## Commits

Conventional Commits with an area scope, in English:

```
feat(reminders): keep a deferred reminder across a reboot
fix(intake): do not spend stock on a skipped intake
docs(spec): describe the transfer conflict decisions
```
