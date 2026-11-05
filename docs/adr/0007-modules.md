# 0007 — Module layout: module kinds, dependencies inward, pure Kotlin domain

Status: Accepted. Superseded in part by ADR-0019: the paid contour and its module are gone, so the
product has no network module and no contour mechanism. The module kinds and the dependency direction
below still hold, and the artifact composition check forbids any network permission (ADR-0018,
ADR-0019).

## Context

The product rests on the reliability of the reminder. Errors in the intake window calculation, in
the `pending` to `missed` transition, at the midnight boundary or on a time-zone change are exactly
what hurts. Those calculations must be covered by tests that run on a developer machine in seconds,
which is possible only if the domain neither pulls in the emulator nor depends on Android.

## Decision

1. **Module kinds.**

| Module | Kind | Content |
|---|---|---|
| `core-domain` | Kotlin/JVM, not Android | entities, states, rules, schedule and window calculation, clock abstraction |
| `core-data` | Android library | Room (entities, DAO, migrations), repositories, mappers, transfer file |
| `core-ui` | Android library | design system: tokens, typography, components, shared strings, locale |
| `feature-*` | Android library | screens; a module is created together with a screen, not in advance |
| `app` | application | Hilt graph, navigation, manifest, receivers, notifications, scheduler |
| `acceptance` | Kotlin/JVM | acceptance scenario layer: shared world, step vocabulary, run harness |

2. **Dependencies point inward:** `app → feature-* → core-ui / core-data → core-domain`.
   `core-domain` depends on nothing; `feature-*` modules do not depend on each other, and anything
   shared moves into a `core-*` module.
3. **Platform mechanisms** (`AlarmManager`, notifications, file system, clock) are declared as
   interfaces in `core-domain`, implemented in `app` or `core-data`, and bound through Hilt.
4. **`core-domain` is pure Kotlin, not an Android library with no dependencies:** `android.*` is
   physically absent from its classpath, so the purity of the domain holds by construction and not by
   discipline.
5. **The rule is checked by the build:** the `verifyModuleDependencies` task fails on any outward
   edge; the rule logic itself is verified against deliberately violating graphs by
   `verifyModuleDependencyRule`.

## Consequences

- Domain code uses no Android types: domain to Room mappers live in `core-data`, domain to screen
  mappers in `feature-*`.
- `core-domain` does not use Hilt; bindings are declared in consuming modules.
- A new screen means a new `feature-*`; a detail shared by two screens moves to `core-*` before an
  edge between screens appears.
- Network code exists only in contour modules. `free` contains no contour module, so the shipped
  artifact carries no network path and declares no network permission; `pro` carries the store client
  and is not shipped.
- No network dependency reaches `core-domain` or `core-data`.
- Dependency direction checks are part of `check` and of the single `verifyAll` run.
