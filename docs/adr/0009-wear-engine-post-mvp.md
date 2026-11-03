# 0009 — Wear Engine postponed beyond the first release

Status: Withdrawn. Watch integration does not return: watches and wearables are excluded from the
product and the domain seam was removed.

## Context

This ADR once recorded a postponement: integration with Huawei watches was moved beyond the first
release, and the domain seam was kept as the basis for returning to the work later. A postponed
integration never returned, while the seam stayed in the model as a second mark source, cost
maintenance and supported the illusion of a supported feature in the documentation.

## Decision

The postponement is cancelled entirely. Watches and wearables are not part of the product in any
variant. See ADR-0005 for the surface that this ADR used to justify.

## Consequences

- The watch and wearable requirements are withdrawn and their content is not carried forward.
- The domain seam is deleted, not preserved in a dormant state.
- An application for Wear Engine rights and the signature SHA-256 binding to it are excluded from
  release readiness criteria: the release key is needed only for the stores.
- The device support matrix has no watch row.
- Returning watches requires a new ADR, not a revival of this one.
