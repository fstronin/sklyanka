# 0005 — Watch notification template instead of a watch app

Status: Withdrawn. Watches and wearables are not part of the product, so this decision does not
apply. No watch app, watch notification template or mark from a watch exists.

## Context

Huawei watches were once considered as a second surface for the reminder. A watch app would have
needed its own project, pairing flow and store listing, while the target device, a lite wearable,
already mirrors phone notifications without any app code. Marks had to stay on the phone anyway:
the phone owns the database.

## Decision

Instead of an app on the watch, the reminder was to be mirrored through a Wear Engine notification
template, with the watch as a read-only surface. Marking stayed on the phone, and the interface
never offered a watch mark.

## Consequences

- The domain seam for the watch was deleted rather than kept dormant: the gateway port and its
  no-watch implementation, the `WATCH` mark source, the mirroring flag on a medication, the setup
  intent and the unpaired-link reader are gone.
- The mark source has two values, `PHONE` and `AUTO`. Concurrent marking from two devices does not
  exist as a scenario.
- The 28-character package name limit required by Wear Engine templates is no longer a requirement.
  `applicationId` stays immutable for a different reason: store records.
- The transfer file has no watch field and never carried one.
- Returning watches requires a new ADR, not a revival of this one.

## Alternatives

A watch app was rejected as unnecessary for a lite wearable that mirrors notifications by itself.
Keeping the seam "for later" would have cost maintenance and supported the illusion of a working
feature, so the seam was removed instead of postponed.
