# 0011 — Analytics network contour in all builds

Status: Superseded by ADR-0017. The client analytics contour was withdrawn and later returned in a
different form: consent-based analytics without A/B experiments, described in ADR-0017. This
document records a decision that is no longer a rule of the product.

## Context

An analytics contour with a vendor SDK, a consent check and A/B experiments was once planned for every
build from the first publication. The vendors did not support it: HUAWEI Analytics was closed to new
projects on 30.06.2024, and A/B Testing was removed from AppGallery Connect version 15.0.1 on
27.03.2025. No event was ever collected, the implementation ran behind a no-op sink, and the dead
contour cost maintenance and confused the composition of the artifact.

## Decision

Analytics, a consent check and A/B experiments were to be part of every build: an `Analytics` port
with a sink, a sixteen-event catalog, a consent check with its own storage, a consent screen, an
"Analytics" row in settings, and vendor SDK behind the port.

## Consequences

- The whole mechanism was removed: the port and its no-op implementation, the event catalog, the
  consent check and its storage, the `core-analytics` module, the consent screen and the settings row.
- A stub that answered "sent" without sending was removed: a status "sent" may only mean the sending
  happened.
- A/B experiments remain out of scope in the current contour as well.
- The current analytics arrangement is defined by ADR-0017: consent-based, no health content, with
  an explicit provider and a declared network contour.
- Any return of a different analytics arrangement requires a new ADR naming the provider, the
  policy, the consent flow and the notification to the regulator.
