# Sklyanka: Product Specification

This document defines what Sklyanka is, who it serves, what it includes and what it refuses to do. It is the product-level reference for the rest of the specification set.

## 1. What it is

An Android application for keeping track of medication intakes. A person sets up a course: schedule, dose, relation to food, package remainder. The app reminds at the appointed minute, accepts a mark with one tap and keeps a history that can be shown to a doctor as a PDF report.

The product is one thing and it is entirely free. There is no paid tier, no subscription and no purchases.

- **Tracking** is an offline core. It works without a network and without an account at all times: schedule, reminders, marks, history, stock, file transfer, the report for the doctor. Every feature of the product is available to everyone.
- **AI-assisted input is not part of the product.** The app never converts a prescription into a structured draft and never offers such a promise. See §5.3.

The app **is not a medical device**. It gives no treatment recommendations and does not select dosages. This statement is shown verbatim in the "About" screen: `«Приложение не является медицинским изделием»` ("The application is not a medical device").

## 2. Who it serves

An adult on Android 8.0 or newer who manages a medication course, their own or someone else's. A doctor prescribed the regimen; the app neither evaluates nor challenges it.

**Scenario A. Own course.** Several medications for a period from one week to an indefinite course. The person sets it up once; afterwards the app reminds, the person marks with one tap, and at the end of the course or at an appointment shows the history.

**Scenario B. Caregiving.** An adult watches over the medication intakes of a child or an elderly relative. This is a second profile on the caregiver's own phone: the owner profile and a foreign profile, switched in one app, with no password and no separate data set. A foreign profile is always visually marked as such.

**What the user does not do in the app:** look up drug interactions or consult a reference; choose a dose or change a prescription; rely on a server for tracking. Tracking, history and the report never depend on any server.

## 3. Goals

Each goal is a statement about an observable result, not about a feature being present in a build.

- After permission setup a person does not miss an intake because of the app: the reminder arrives at the appointed minute and survives reboot, Doze, time-zone change and vendor power saving.
- At any moment of the day a person answers in one glance whether the medication was taken and what remains for today.
- A caregiver sees the intakes of the foreign profile and never marks someone else's intake as their own.
- By the end of the course there is an adherence history that matches what actually happened, and it can be shown to a doctor.
- A person learns that the package is running out in advance, before it runs out and the course breaks.
- Data is not locked in: everything is exported as one file and moved to a new device with no cloud involved.

## 4. Principles

| Principle | Consequence |
|---|---|
| Reminder reliability outranks everything else | A missed reminder is a product failure, not a convenience defect. Features are sacrificed for reliability, never the reverse. |
| Profiles never mix | The interface is always in the context of exactly one profile. A combined list of intakes across profiles does not exist. Indication of a foreign profile is deliberately redundant. |
| Tracking works without a network; nothing leaves the device | Tracking, reminders, history and the report work without a network and without an account at all times. Health content, meaning names, doses, schedules, intake times, marks and stock, never leaves the device. Nothing about usage is collected either: there is no analytics, no advertising and no data sale. |
| The app is not a medical device | No treatment recommendations, no dose selection, no symptom interpretation. |
| The person owns their data | All content is exported as one file and loaded back. Nothing is locked inside the app. |
| Service failure never blocks treatment | Tracking, reminders and the report depend on neither a network nor an external service. |
| The product tells the truth | No confirmation that did not happen: a stub never reports success, diagnostics never present a plan as delivery, a status "sent" means the sending actually happened. |

## 5. Product composition

### 5.1 Core, for everyone, without a network

Medications with a release form and a tablet appearance · schedules by times of day and a PRN ("as needed") intake with a daily limit · relation to food · course period and indefinite courses · package size and remainder with a configurable warning threshold · retiring a medication for one of two reasons and restoring it · soft deletion of a mistakenly created medication before its first "taken" mark · marking an intake with an undo in a short window, including removal of a PRN record · reliable reminders through exact alarms, a full-screen card, deferral and a persistent reminder channel · permission onboarding · a "Check reminders" diagnostic with a delivery self-check through the production path · a delivery log with export · history, adherence, a month calendar and a PDF report for the doctor with a selectable period · data transfer as one file in two modes with conflict resolution · profiles with switching and deletion of a foreign profile · system dark theme on every screen · six interface languages.

### 5.2 Formerly paid features, now for everyone

Everything that once belonged to a paid tier is available to everyone at no cost: complex schedules beyond a daily intake and PRN; "Today" widgets with a quick mark, a week widget and a per-medication widget; an extended PDF report for the doctor, complementing the basic one at the person's choice (adherence dynamics, period comparison, missed-intake patterns); history export as CSV that opens in Excel; all four accent themes and both icon variants; profiles three through five.

The limit of five profiles is a product constraint, not a paywall: the sixth profile is closed to everyone. There is no subscription, purchase, trial period, storefront or paid-tier label anywhere, and no feature is marked as paid.

### 5.3 Explicitly out of scope

Cloud synchronization and accounts for tracking · a medication reference and interaction checking · any treatment recommendations, dose selection or symptom interpretation · advertising · client analytics and usage tracking · a per-medication custom sound · quiet hours · Huawei watches and wearables · A/B experiments inside the app · a paid tier, subscription, purchases, storefront or paid-tier label · AI-assisted input. The app makes no network call at all: there is no cloud, no account, no synchronization and no server-side supervision.

## 6. Data and privacy

**What never leaves the device:** all of it. Medication names, dosages, schedules, times, intake statuses, stock, profiles, history, the transfer file and the delivery log stay in the app's private storage.

**What leaves the device:** nothing. There is no account, no cloud, no sync, no ads, no analytics and no server-side supervision. `AD_ID` is not declared, GMS and Firebase are not used, and the shipped build declares no network permission. Data is not sold and is not transferred to third parties.

**Required texts:**

- A privacy policy, a minimal honest public page at `stohastika.ru` in Russian and English: the statement that no data is collected and everything stays on the device, that there is no account, cloud, sync, advertising or analytics, and how the person deletes the data. Presented as a dialog on first launch, in the interface language.
- The disclaimer `«Приложение не является медицинским изделием»` ("The application is not a medical device").

## 7. Platform and reach

| Parameter | Value |
|---|---|
| OS | Android 8.0+ (API 26), `targetSdk` 35 |
| Devices | Any Android phone; verification priority is Xiaomi, Samsung and Huawei |
| Google services | Not required or used; the app runs on devices without GMS |
| Stores | RuStore and AppGallery, the free build only, with no paid release or subscriptions; RuStore launches first, AppGallery follows. Google Play is not a channel |
| Languages | `ru` (base and fallback), `en-US`, `es`, `de`, `zh-CN`, `kk` |
| Watches and wearables | Not supported |
| Network | Not used: the shipped build declares no network permission and makes no network call. No GMS, no Firebase, no `AD_ID`. Tracking, reminders, history and the report work with no network at all. The paid `pro` contour declares `INTERNET` for AppGallery IAP, and that variant is not shipped |

Reminder reliability depends on the power management of the firmware. The product does not promise equal survival on every device: it promises setup guidance, a measured delivery self-check and an honest statement when reminders are unreliable.

## 8. Success signals

The signals below are measured by a personal run, the delivery log and history on the device, and the store console. There are no monetary signals: the product is free.

| Signal | Threshold | Measurement source |
|---|---|---|
| Share of reminders delivered within ±2 min on a configured device | ≥ 95 % over a continuous run on each device of the matrix | Delivery log on the device, CSV export |
| Share of intakes that received a mark instead of an automatic missed status | ≥ 90 % over 30 days | Delivery log and history on the participant's device |
| Number of wrong marks in a foreign profile | 0 | Caregiving scenario log plus the "marked by you" label in history |
| Share of people who complete permission onboarding up to a successful delivery self-check | ≥ 90 % | Observation at installation |
| Time to add one medication with a schedule by hand | ≤ 90 seconds | Timed run |
| Share of installs with a rating ≥ 4.5 in the stores | ≥ 4.5 stars | RuStore and AppGallery consoles, each separately |
| Adherence: share of arrived planned intakes marked within ±2 h of the planned time | ≥ 80 % over 30 days | History on the participant's device |
| Retention: the app is still used 30 days after installation | ≥ 30 days of personal use | Owner's personal run; store consoles as reference |

The delivery gate runs on the device matrix: Huawei Pura 80 Ultra plus one or two devices of the Xiaomi/HyperOS and Samsung/One UI profiles, **14 continuous days on each**, with a threshold of ≥ 95 % within ±2 min. A delivery delayed by the system counts as late and stays in the calculation; a firing not presented to the person is not excluded from it. If devices are scarce, the promise narrows to the verified ones, but the threshold and the continuity of the run do not.

## 9. Honesty boundaries of the product

The product tells the truth about what happened, even when it is inconvenient:

- A reminder delayed by the system is a late reminder, not a "pause".
- A firing that was not presented to the person does not count as delivered.
- Diagnostics show the permission state the system reports, not a saved flag.
- The delivery self-check travels the same path as a production reminder.
- Any "done" message is confirmed by an actual result, not by an intention.
- A reminder that cannot fire honestly, for example with a database newer than the installed build, says so and creates no intake mark.
