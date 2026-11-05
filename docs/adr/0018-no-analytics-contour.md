# 0018 — No analytics contour in the product

Status: Accepted. It supersedes ADR-0011 and ADR-0017. Item 4 is superseded by ADR-0019: the paid
store client is gone as well, so no network contour remains. The build contains no analytics contour,
no provider SDK, no event queue, no consent screen and no network module.

## Context

The product carried a client analytics contour: a Gradle module with a vendor SDK behind a port, an
event queue in Room, a consent step and a row in settings. The contour needed three things the
product does not want to depend on: a proprietary tracking SDK with a vendor licence and an opaque
release of its own, the `INTERNET` permission together with `ACCESS_NETWORK_STATE`, and a legal
apparatus around consent and the receipt of the data.

None of this serves the person using the app. Tracking, reminders, history, stock, transfer and the
report work offline, and the value of the product is measured by whether a reminder arrives and
whether a mark is truthful, not by how many screens were opened. At the same time the contour kept
the shipped artifact tied to a vendor, made the privacy promise conditional on a consent switch, and
blocked free-software distribution, where a build has to be fully offline and free of proprietary
telemetry.

## Decision

1. **No client analytics.** The app collects no usage events, keeps no event queue, declares no
   analytics settings row and shows no analytics consent step. Nothing about the person, the
   medication or the use of the app leaves the device.
2. **No provider SDK.** No tracking SDK coordinate appears on any variant's runtime classpath, and
   the guarded provider group is forbidden rather than white-listed behind a port.
3. **No network permission in the shipped build.** The merged manifest of `free` declares neither
   `INTERNET` nor `ACCESS_NETWORK_STATE`; `ACCESS_WIFI_STATE` and `AD_ID` stay forbidden as before.
4. **The paid store client remains the only network contour.** `:billing-appgallery` with
   AppGallery IAP keeps the contour mechanism of ADR-0017: the module, its `INTERNET` permission and
   its coordinate are declared as contour data, and the permission is legal only together with that
   module in the `pro` artifact. The `pro` variant is built and unit tested, not signed, not
   published and never in a store.
5. **The build check enforces the boundary.** Any network permission outside the paid contour fails
   the merged-manifest check, and any analytics coordinate fails the dependency-composition check.

## Consequences

- **No usage metrics exist.** There is no data on where people leave, which screens they use or how
  often they return. Success signals come from manual runs, the delivery log on the device, history
  and adherence, and the store consoles; the signals of `product.md` §8 lose their analytics
  alternative and stand alone.
- **The privacy statement becomes unconditional.** `privacy.md` and `product.md` state that nothing
  leaves the device, without a consent exception; the privacy policy has no list of analytics data,
  recipients or retention to disclose.
- **The database loses the queue table.** The schema moves from 11 to 12 with a migration that drops
  the client event queue; no product table is touched.
- **The module count falls by one.** `:core-analytics` disappears, and with it the transport port in
  the domain and the consent state in settings.
- **The build check forbids any network permission outside the paid contour.** `free` has no network
  path at all; a new network contour requires a new ADR, because this is an architectural boundary,
  not a setting.
- A future return of analytics requires a new ADR naming the provider, the data, the consent flow
  and the distribution consequences for a fully offline, free-software build.
