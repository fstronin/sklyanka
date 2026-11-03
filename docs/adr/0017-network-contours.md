# 0017 — Network contours: consent-based analytics and the paid contour

Status: Superseded by ADR-0018. The client analytics contour was removed: the product ships with no
analytics, and the shipped build declares no network permission (ADR-0018). The contour mechanism
itself survives for the paid contour only: `PAID_CONTOUR` (`:billing-appgallery`, `INTERNET`, the
store client) is the single contour, it exists only in the `pro` variant, and that variant is not
shipped.

## Context

Network access in the artifact used to be allowed in exactly one way: together with the
`billing-appgallery` module, checked by the merged manifest and dependency composition tasks.
Consent-based analytics then returned to the shipped build, while the paid contour became
non-shipping. An artifact may now contain either contour, both (in the `pro` variant) or neither, so
the rule "network equals paid contour" is no longer true.

## Decision

1. `build-logic` introduces the notion of a **network contour**: data about a contour (module,
   project, permissions, required coordinates), not code. There are two: `ANALYTICS_CONTOUR`
   (`:core-analytics`, `INTERNET` and `ACCESS_NETWORK_STATE`, the provider SDK coordinate) and
   `PAID_CONTOUR` (`:billing-appgallery`, `INTERNET`, `com.huawei.hms:iap`).
2. **Manifest rule.** A contour permission is legal only if a contour present in the artifact declares
   it, and no module outside the contours declares it. The reverse is mandatory too: a contour present
   in the artifact without its permission is a violation. A permission declared by two contours is
   legal when both are in the artifact.
3. **Unconditional bans stay:** GMS, Firebase, advertising and third-party analytics coordinates,
   `ACCESS_WIFI_STATE`, `AD_ID`. The coordinate whitelist is checked for every guarded group, not only
   for the vendor group.
4. **Rule logic is a pure object, `NetworkContourRules`**, verified against known violations by a
   separate task. A check without a violation sample is a promise, not a check.
5. **The analytics provider sits behind a port in the domain**; the app does not know MyTracker. A
   provider change touches neither screens nor use cases.

## Consequences

- `free` declares `INTERNET` and carries the provider coordinate; `pro` carries the store client as
  well. A build with neither contour declares no network.
- Analytics consent is part of settings state: without it the provider is not initialized, no events
  are produced, and the remaining queue is cleared. Events carry no medication names, doses,
  schedules or profile identifiers.
- **The provider drags in forbidden dependencies.** `com.my.tracker:mytracker-sdk:3.6.0` declares
  `play-services-ads-identifier`, `play-services-appset`, `com.android.billingclient` (and through it
  GMS and Firebase) and `installreferrer`. Those groups are excluded in the contour module. The SDK
  tolerates their absence, verified by bytecode: the purchase client disables itself through
  `Class.forName`, calls to `AdvertisingIdClient` and `AppSet` are caught as `Throwable`, and
  automatic purchase tracking is off. `ACCESS_WIFI_STATE` and `AD_ID` are not declared.
- **A release build without the provider key fails.** The key (`sklyanka.mytracker.key` or
  `mytracker.key` in `local.properties`) is required for `assemble*Release`, not for debug or
  `verifyAll`; it never enters git.
- `ACCESS_NETWORK_STATE` belongs to the analytics contour. Measured on a device: without it the SDK
  calls `ConnectivityManager.getActiveNetworkInfo()`, receives a `SecurityException` and sends no
  events, while "time in app" packets still go out; declared by the contour, it sends events and the
  queue empties.
- A provider change or a third network contour requires a new ADR: this is an architectural boundary,
  not a setting.
