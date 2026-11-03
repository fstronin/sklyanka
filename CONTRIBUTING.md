# Contributing

## Before you change behaviour

1. Read the specification of the area: [`docs/spec/behavior.md`](docs/spec/behavior.md) for
   behaviour, [`docs/spec/design-system.md`](docs/spec/design-system.md) for the visual language.
2. Find or write the scenario in `acceptance/src/main/resources/features/`. The scenarios are the
   contract of the product: a scenario that passes while the behaviour is wrong is a defect.
3. Make the scenario fail, then change the code, then make it pass.

## Checks

```bash
./gradlew verifyAll
```

The gate builds every module, runs detekt (including the project's own rules), ktlint, Android
Lint, the artifact composition checks, all JVM tests and the acceptance scenarios. Instrumented
tests need a device or an emulator:

```bash
./gradlew :core-data:connectedDebugAndroidTest
```

## Rules that are not negotiable

- **A missed reminder is a product failure**, not a convenience defect. A change that can make a
  reminder late is a defect even if every other check is green.
- **Profiles never mix.** The interface is always in the context of exactly one profile, and a
  mark of a foreign profile carries its actor.
- **Accounting works without a network, always.** Accounting, reminders, marks, history and the
  report must not depend on a network, an account or an external service.
- **Nothing leaves the device.** There is no account, no cloud, no sync, no ads and no analytics,
  and the shipped build declares no network permission. Health data, the transfer file, the report
  and the delivery log stay on the device. The paid `pro` contour is the only network module and is
  not shipped.
- **The app is not a medical device.** No treatment recommendations, no dose selection, no
  drug-interaction checks.
- **Design tokens are not hardcoded.** Colour, typography, geometry and motion come from
  `core-ui/token/`; the detekt rules `HardcodedDesignToken` and `HardcodedTextStyle` enforce it.

## Language

- Documentation and commit messages: American English.
- The interface: Russian, with translations into English, Spanish, German, Chinese and Kazakh.
- Acceptance scenarios and their step patterns: Russian, because they assert the Russian
  interface and Russian demo data verbatim.
- Code identifiers, test names and comments: English.
- Domain terms are used exactly as the glossary defines them: [`docs/spec/glossary.md`](docs/spec/glossary.md).

## Commits

Conventional Commits, with the area in the scope:

```
feat(reminders): keep a deferred reminder across a reboot
fix(intake): do not spend stock on a skipped intake
docs(spec): describe the transfer conflict decisions
```
