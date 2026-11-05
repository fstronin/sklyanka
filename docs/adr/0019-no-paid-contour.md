# 0019 — No paid contour in the tree

Status: Accepted. It supersedes ADR-0010, ADR-0015, ADR-0016 and ADR-0017 where they describe the
paid contour. The product is free in full, ships a single `free` flavor, contains no store client and
contains no network module.

## Context

ADR-0016 kept the paid code in the tree behind `pro`: two modules, `feature-pro` and
`billing-appgallery`, a second build variant, a `ProStatus` seam read by every composition check, a
«Про» ("Pro") mark and the store entry points, all excluded from the shipped artifact by a flag.

The paid build was never shipped. Its only function in the tree was a proprietary AppGallery in-app
purchase client, pulled through the Huawei Maven repository, with its own SDK, its own licence and its
own transitive closure. That is a dependency the product does not use, cannot test against a real
store in CI and cannot distribute: a build that reaches a vendor repository for a store client is not
reproducible from free-software sources, and F-Droid cannot build it. Keeping the code for a possible
return cost a permanent exception in the network rules, a second variant in every gate, and a check
that had to answer "may this permission be here" instead of "no permission may be here".

The product itself never needed the contour. Nothing in tracking, reminders, marks, stock, history,
transfer or the report depends on a purchase, and the only thing the paid tier ever gated was a
composition of features that are free now.

## Decision

1. **No paid contour.** `:feature-pro` and `:billing-appgallery` are deleted, with the Huawei Maven
   repository, the `com.huawei.hms:iap` coordinate, the `PAID_CONTOUR` data, the store entry points
   and the «Про» label.
2. **No store client and no entitlement code.** No billing client, no purchase state, no store query,
   no `ProStatus` seam and no interface flag that shows or hides a paid surface. A screen never asks
   whether access was bought, because access is not a thing the product sells.
3. **One flavor, `free`.** The `contour` dimension keeps the single `free` flavor so that
   `assembleFreeDebug` and `assembleFreeRelease` keep their names: they are part of the release
   pipeline and of the F-Droid build. No second variant exists, so no code path is compiled for a
   build that is not shipped.
4. **No network module.** The artifact contains no network dependency and declares no network
   permission. `INTERNET` and `ACCESS_NETWORK_STATE` are forbidden, not granted under a condition.
5. **The composition check holds the boundary.** `ArtifactCompositionCatalog` lists the forbidden
   network permissions, the forbidden groups and the always-forbidden permissions;
   `ArtifactCompositionRules` holds the logic as pure functions; `verifyManifest<Variant>` and
   `verifyDependencies<Variant>` apply it to the real artifact, and `verifyArtifactCompositionRule`
   proves the rules fire on deliberate violations.

## Consequences

- **No billing tests exist.** There is no purchase flow to fake, no store stub, no entitlement cache
  to test. The test levels cover reliability, data safety, privacy and the interface instead.
- **The five-profile limit is a product rule.** `ProfileCreationGate.decide(created)` answers
  `Allowed` or `BlockedByLimit` from the number of profiles alone, and the sixth profile is closed to
  everyone, with no second reason and no upgrade path.
- **F-Droid can build the app.** The build resolves from Maven Central and Google's Android
  repository only, needs no vendor repository and no store client, and produces an artifact that is
  fully offline.
- **The artifact composition check forbids any network permission.** A permission no longer has a
  legal contour to come from, so the rule is unconditional: a network permission in the merged
  manifest, in any module manifest or through any dependency fails the build.
- **Every feature is unconditional.** Complex schedules, widgets, the extended report, CSV export,
  accent themes and profiles three through five are composition, not access; the domain types behind
  the removed seam are gone.
- **A paid model cannot be restored by a flag.** It would be a new product decision with a new store
  client, a new distribution analysis and a new ADR, not a variant switch.
