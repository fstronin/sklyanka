# 0010 — Paid network contour

Status: Superseded by ADR-0019. There is no paid network contour and no paid build variant: the paid
modules, the store client and its permission are gone, and the product has no network module at all.
This document records a decision that is no longer a rule of the product.

## Context

AI-assisted input cost money per call, so unlimited use was to be sold as a subscription, and both
the subscription and the extraction had a server side. Reminder reliability must not depend on
someone else's service, so the contour had to be isolated from the core and had to degrade in favor
of the person.

## Decision

1. **`INTERNET` only in the paid contour**, declared for exactly two network points: AI input and
   subscription check. Tracking worked without a network and without an account at all times.
2. **Isolation from the core.** A separate Gradle module outside `core-domain` and `core-data`, with
   the dependency direction of ADR-0007 intact; the core did not know about the contour. Provider
   keys lived only on the server, and the client called an API proxy with a minimal request.
3. **Degradation and cache.** A server, network or store failure blocked no tracking function and no
   manual medication entry; the outcome was named in words and never imitated success. Subscription
   state was cached on the device: when it could not be verified, access was kept, not closed. It was
   not part of the transfer file.
4. **Quota and billing.** Three successful fills per installation, counted by a server counter per
   installation identifier plus a local copy; on a mismatch or without the server the limit counted
   as not exhausted. Sales went through RuStore Billing and AppGallery IAP, with products
   `sklyanka.pro.monthly` and `sklyanka.pro.yearly` identical for both stores and immutable after
   creation.
5. **Build checks.** `INTERNET` was allowed in the merged manifest only with the paid contour and
   only from its manifest. The coordinate whitelist was the closure of the store client, including
   vendor telemetry that the product never calls. GMS, Firebase, advertising, third-party analytics,
   `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` and `AD_ID` stayed forbidden.
6. **Privacy.** Health content left the device only in AI input, on an explicit action after a
   separate consent, and only to a server within the country; the snapshot was not stored after
   processing and server logs held no images, medication names or dosages.

## Consequences

- The core stayed verifiable offline: no network traffic from the core was checked on the artifact.
- The privacy policy had to precede the first paid request and had to disclose server processing and
  the installation identifier as personal data.
- A consent screen for health data and the "not a medical device" disclaimer were mandatory on the
  AI input screen. The storefront sat at the point of choosing a paid function, never in a treatment
  scenario.
- A subscription came only from the store, with its state cached and access preserved when the store
  was unreachable. Measurement used server counters and store consoles, not client analytics.
