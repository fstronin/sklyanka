# 0006 — No medication directory and no interaction checks

Status: Accepted

## Context

Checking drug compatibility or selecting a dose is clinical decision support. That makes the product
a medical device, with registration, conformity assessment and lifecycle obligations. Both failure
modes harm the person: a false warning makes them cancel a prescribed medication, a missed warning
lets a real interaction pass. An on-device directory needs a license, updates and a network, and a
built-in one is outdated from the first day.

## Decision

- There is no medication directory in the product, neither on the device nor as a service, and no
  interaction check.
- The person types the medication name; it is stored as text. Recognition relies on the release
  form, tablet appearance and dose, all set by the person.
- The statement "the application is not a medical device, gives no treatment recommendations and
  does not select dosages" is shown verbatim in `«О приложении»` ("About") and accompanies the
  report for the doctor.
- The app records what a doctor prescribed and helps carry it out on time. It does not discuss the
  prescription.
- Reference data is not consulted anywhere. Nothing in the product normalizes a medication name
  against a registry, and nothing is filled in automatically.

## Consequences

- No interface text may read as a recommendation.
- Misspellings in names are possible, so manual match review during a transfer is mandatory: the
  normalized name and dose are compared inside the matched profile, and the person confirms the
  match.
- Application size and database volume stay small: no directory ships in the artifact.
- No feature compares medications with each other, in any build variant.
