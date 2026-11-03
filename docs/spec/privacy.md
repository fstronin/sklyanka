# Privacy

Sklyanka is a local medication tracker. Health data stays on the device of the person who
entered it; the product has no account, no cloud and no sync.

## 1. What the app stores

The person enters:

- medication names, dosage form, dose, the relation to meals, the course period and the schedule;
- "taken" and "skipped" marks with their time;
- package stock and the refill threshold;
- profile names: the person's own profile and the profiles of dependants;
- intake history, adherence and the settings of the report for the doctor.

This information may relate to health, which is a special category of personal data. The app
processes it only in its private storage on the device. Nothing in this repository receives it.

## 2. What leaves the device

- **Analytics.** The build declares the `INTERNET` permission for one contour: client analytics
  that runs only after an explicit consent and is described in ADR-0017. It sends no medical
  data: no medication names, doses, marks or profile names. Consent can be withdrawn, and the
  contour then stops.
- **A transfer file.** The person creates it on their own action, saves it where they choose and
  moves it themselves. The file is not encrypted and is a medical document; a folder synced with
  a cloud will take it to the cloud through that folder, not through the app.
- **An exported report and a CSV table.** Same rule: the person chooses the place.
- **A diagnostic log.** A technical reminder log (time, reason, delivery channel) used to check
  delivery. Medication names and doses are not written to it, and it does not leave the device
  unless the person exports it for troubleshooting.

No account, no server profile, no cloud copy and no sync exist. No advertising identifiers are
used, data is not sold, and no data is transferred to third parties.

## 3. Permissions and why they are needed

| Permission | Why | What it does not grant |
|---|---|---|
| Notifications | show a medication reminder | access to other apps' content |
| Exact alarms (`USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM`) | wake the device at the scheduled minute | access to the calendar or the alarms |
| Full-screen notification | show the intake card on the lock screen | access to the screen or the input |
| Boot (`RECEIVE_BOOT_COMPLETED`) | restore the schedule after a restart | access to files |
| Do Not Disturb policy access | explain why a reminder may be silent and help with the setup | reading or controlling notifications without the person's action |
| Vibration | a tactile reminder signal | nothing beyond the signal |
| `INTERNET` | the consent-gated analytics contour | access to any health data |

The app does not request location, camera, microphone, contacts, calendar, SMS or full storage
access.

## 4. Retention and deletion

Information stays on the device until the person deletes it:

- clearing the app data in the Android settings deletes the local database and the settings;
- uninstalling the app deletes its private storage;
- archived records are deleted with the app data.

There is no server copy, so a server-side deletion is neither required nor possible. Android
system backup for the app is off: the manifest sets `allowBackup="false"`.

## 5. Children

The app is not directed at children and creates no children's accounts. An adult may keep a
dependant's profile, a child's or an elderly relative's, on their own phone; that adult is
responsible for the information entered and for access to the device.

## 6. Medical status

Sklyanka is not a medical device, does not give treatment recommendations and does not select
dosages. It does not check drug interactions, does not make a diagnosis and does not replace a
doctor.

## 7. Contact

Questions about this repository and the app: `support@stohastika.ru`.
