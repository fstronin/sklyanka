# 0016 — Free product: paid code behind a build flag

Status: Superseded by ADR-0019. `free` is the only build, the paid code is deleted rather than kept
behind a flag, and the product has no network module at all. This document records a decision that is
no longer a rule of the product.

## Context

The product is free forever. A paid model would pull in a network, billing, a legal framework for
personal data and a storefront inside a treatment scenario, contradicting both the promise that the
core works without a network and the priority of reminder reliability. Deleting the paid code that was
already written would lose a future option with no benefit for the shipped build; leaving it in `free`
would drag a network and dead entry points into the APK. A build flag gives the third option: neither
network nor entry points in the artifact, and the code kept.

## Decision

1. **`free` is the only shipped build.** It contains no `feature-pro` and no `billing-appgallery`
   module, no purchase entry points, no «Про» ("Pro") mark and no storefront. The shipped artifact
   contains no network module and declares no network permission (ADR-0018); "the core works without
   a network" holds unconditionally.
2. **`pro` is a build variant for a possible return.** It compiles and passes compilation, dependency
   injection and unit tests in CI, but is not shipped or published. Paid acceptance (`@pro` scenarios,
   screen steps, golden frames of paid surfaces) is removed: dormant code keeps no live contract.
3. **The "access" domain seam is retained.** Composition checks keep reading `ProStatus`; in `free` it
   always reports access granted, so complex schedules, widgets, the report, CSV, accent themes and
   profiles three through five work unchanged. The `ProMark` label and storefront entries are hidden
   by an interface flag, not deleted.
4. **Returning a paid model requires a new ADR** and a revision of this one. Product identifiers
   `sklyanka.pro.monthly` and `sklyanka.pro.yearly` stay in the store console but are not published.

## Consequences

- Artifact composition checks cover both variants: `free` without the paid contour, `pro` with it.
  GMS, Firebase and analytics SDKs are forbidden always, and so are `ACCESS_NETWORK_STATE`,
  `ACCESS_WIFI_STATE` and `AD_ID`; `INTERNET` is legal only in the paid contour of `pro`.
- `free` does not contain the paid modules; paid domain types and `ProMark` remain compiled but
  unreachable. This is the accepted price of keeping the code.
- `pro` is not covered by acceptance and may lag: CI catches only compilation, injection and unit
  tests. Restoring a paid model means rebuilding it as new work, not flipping a switch.
- Product metrics are reliability, adherence and retention. Monetary metrics do not exist.
- ADR-0010 and ADR-0015 are superseded and remain as records of decisions that no longer apply.
