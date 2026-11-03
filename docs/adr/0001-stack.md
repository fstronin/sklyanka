# 0001 — Stack: Kotlin, Jetpack Compose, Room, Hilt, minSdk 26

Status: Accepted

## Context

The product has to deliver a reminder at an appointed minute on stock Android 8.0 and newer, with
no server, no account and no Google services. Everything the reminder depends on, exact scheduling,
notification channels, permission state, is a platform API, so any cross-platform layer would turn
the most fragile part of the product into a hand-made bridge.

## Decision

A native Android application without an intermediate layer: Kotlin, Jetpack Compose, Room, Hilt,
`AlarmManager`. `minSdk 26`, `targetSdk = compileSdk = 35`. Google services are not used and are
absent from the dependencies.

- **Kotlin** is the platform language; coroutines give the receiver and database work one model.
- **Jetpack Compose** builds the interface: design tokens become objects (`SklyankaColors`,
  `SklyankaType`, `SklyankaShapes`, `SklyankaSpacing`), components become functions with an
  enumerable state, the profile accent becomes a theme.
- **Room** stores data: typed migrations with tests (ADR-0012), coroutine access, queries checked at
  compile time.
- **Hilt** wires dependencies: alarm and notification receivers receive theirs through
  `@AndroidEntryPoint`, and the time source is replaceable in tests.
- **`AlarmManager`** is the only reminder mechanism (ADR-0003). There is no server-side delivery
  channel: the core has no network, and Google services are absent.

`minSdk 26` is set for two reasons at once: notification channels appeared in API 26, and Doze,
the reason `setExactAndAllowWhileIdle` exists, starts at API 26.

## Consequences

- The domain layer is free of Android and is tested on the JVM (ADR-0007).
- Design tokens are never hardcoded at the point of use; a lint rule enforces this.
- Time and time zone come from a replaceable source (ADR-0013).
- Cold start on Android 8.0 is measured, not assumed.
- Version numbers have one source, `build-logic/.../ProjectDefaults.kt`; the merged manifest is
  checked by the `verifyManifest*` tasks.
- There is no iOS version: the stack is tied to one platform deliberately.

## Alternatives

A cross-platform framework was rejected: exact alarms, notification channels and a token-based
design system work only against the platform APIs directly, and the reminder cannot be expressed
as "run at least every fifteen minutes".
