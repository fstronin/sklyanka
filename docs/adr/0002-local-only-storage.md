# 0002 — Local-only storage of tracking data

Status: Accepted. Tracking data is stored on the device only. Network access in the artifact is
allowed solely through declared network contours (ADR-0017); it carries no health content.

## Context

The data is medical: medication names, doses and schedules reconstruct a diagnosis. Cloud
synchronization would pull in a server, accounts, consent, encryption, two store jurisdictions and
liability for a leak, while moving to a new phone is solved by a single file. The owner of the phone
is also often the caregiver of the person whose intakes are tracked, so the content has to stay on
one device by design.

## Decision

- All tracking data is stored in the local Room database on the device.
- Android backup is disabled (`android:allowBackup="false"`): otherwise a copy would leave for the
  cloud around this decision.
- The only way to move data is the transfer file, in two modes: "add to current" and "replace
  everything".
- The tracking core has no network code. `INTERNET` is declared only by a network contour present in
  the artifact: the analytics contour (ADR-0017), and in the paid build variant also the store
  client. The analytics contour sends no medication names, doses, schedules, times, intake statuses,
  stock, profiles, history, transfer file or delivery log.
- Those values never leave the device in any build. There is no account, no synchronization and no
  AI input.

## Consequences

- No backup exists: losing the phone loses the history unless the transfer file was exported. The
  transfer screen says so plainly and never suggests that data is stored somewhere else.
- Data transfer is a mandatory feature, not a convenience: a failed export costs data.
- The transfer file is not encrypted, so a warning during export is mandatory.
- There is no synchronization between devices: profiles live on one phone by design.
- The privacy policy states the facts: tracking content is not sent anywhere; the only outgoing
  traffic is the analytics contour, which carries no health content.
- "The tracking core makes no network call" is verified on the merged artifact by the manifest
  checks, not by review.
