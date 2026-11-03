# 0012 — Data safety: migrations, a copy before upgrade, integrity checks

Status: Accepted

## Context

The database is the only copy: there is no cloud, Android backup is disabled, and the transfer file
is the only other one. A transaction guarantees all or nothing, not correctness: a migration over a
misread schema, or a build that accepts a newer database, destroys history without a visible error.

## Decision

1. **Forward-only migrations.** A schema change means a new version, a new `Migration(N, N+1)` and a
   new JSON in `core-data/schemas/`; a released JSON is never edited or deleted, the chain has no
   gaps, and down migrations do not exist. The only rollback is restoring the copy from item 3 with
   the previous build. Every transition is tested on a real database of the previous version and on
   the volume database by comparing records and check values, plus a chain test from any released
   version.
2. **Forbidden:** `fallbackToDestructiveMigration*` (lint `DestructiveRoomMigration`), `DROP TABLE`
   for tables with person data, cosmetic-only migrations, history recalculation without a requirement
   and a test, network access, reading the system clock in place, medical data in the log.
3. **Copy before migration.** `PRAGMA user_version` is read before the first Room open. If a
   migration is pending, one copy is taken into `noBackupFilesDir` after
   `PRAGMA wal_checkpoint(FULL)`, with the "from" version in the name; it is not a backup, is not in
   the transfer file, and lives until the next migration. Without space the migration does not run
   and the person sees the number of missing megabytes.
4. **Trace and checks.** `MIGRATION_APPLIED` is logged with versions, duration, whether a copy was
   taken and the outcome, without medical data. `PRAGMA quick_check` and `foreign_key_check` run
   after a migration, and cheap checks at most once a day off the main thread. Findings are reported
   and data is preserved: automatic repair is forbidden, checks never block reminders.
5. **Soft deletion only.** A medication is never physically deleted: archive (`archived_at` with a
   reason) or deletion before the first "taken" mark (`deleted_at`). `DELETE` is allowed only for
   replacing all data during a transfer, deleting a foreign profile, and recreating future pending
   intakes after a schedule change; any new operation starts with an amendment to this ADR.
   Withdrawing a PRN record sets `revoked_at` and keeps the row. A transfer is atomic: validation
   before a single record, snapshot invariants before applying, one transaction for the load,
   identifiers reissued inside it, external state written after commit.
6. **Release procedure.** A version change ships one package: the new JSON committed and the old ones
   unchanged, the generated schema equal to the committed one, the harness migration group green,
   volume and chain tests passing, zero `DestructiveRoomMigration` hits, and a manual upgrade over
   the release build.
7. **Old build over a newer database.** The actual `PRAGMA user_version` is compared with the version
   the build knows before Room opens. A newer database is not opened and its data is untouched: a
   blocking screen says the database is newer and the app must be updated, and destructive
   degradation is forbidden. Alarms firing in this state create no marks, show no intake card, and
   report that an update is needed.

## Consequences

- Schema export per version, migration tests, volume and integrity tests, both lints, a safety export
  before "replace everything", and the operation log written in the same transaction.
- A schema change is one indivisible package: version, JSON, `Migration`, transition test, volume
  test, log entry and the amendment to this ADR.
