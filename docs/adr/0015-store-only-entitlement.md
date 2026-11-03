# 0015 — Subscription: the store is the only source of entitlement

Status: Superseded by ADR-0016. There is no subscription in the product, and the product is free. This
document records a decision that is no longer a rule of the product and has no bearing on the current
network architecture (ADR-0017).

## Context

Once a paid tier existed, its entitlement state had to come from somewhere. A server for entitlements
would have given purchase stitching across stores and resistance to reinstalling, but it would have
required an account or an installation identifier as personal data, a server, a legal processing
framework and money for maintenance, all to protect revenue that a subscription without AI input did
not yet have. Reminder reliability must not pass through someone else's service either.

## Decision

The source of truth about a subscription was the store, and only the store. There was no entitlement
server, no account and no stitching of purchases between stores. Four parts:

1. **State cached on the device.** The subscription was checked by a store query
   (`obtainOwnedPurchases`, also a mandatory item of AppGallery pre-release review) at every launch
   with a network; the result went into local state, from which every paid-tier composition check
   lived.
2. **The cache had no expiry.** With the store unreachable, access was preserved, and not "for N
   days" but indefinitely. The accepted risk was named: a person who cancelled the subscription and
   never went online kept access.
3. **A purchase did not move between stores.** Restoration worked only inside the store that sold the
   purchase; each store had its own purchase.
4. **Cancellation was the platform's.** The store cancelled on its subscription management screen; the
   app showed the state and led there (`startIapActivity`), cancelling nothing itself. A store refusal
   blocked no tracking function.

## Consequences

- The subscription check was part of the paid network contour: `INTERNET` was declared only there, and
  artifact composition checks were revised with it (ADR-0010 items 1 and 5).
- The storefront and its states were one shared surface; the storefront sat at the point of choosing a
  paid function.
- Subscription state was not part of the transfer file: a purchase moved neither between stores nor
  between installations.
- Returning to an entitlement server would have required a new ADR: the subscription would have had
  two sources, and the degradation rule would have had to be formulated anew.
