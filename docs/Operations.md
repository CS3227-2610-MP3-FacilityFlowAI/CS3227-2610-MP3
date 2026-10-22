# Production operations and submission evidence

Current acceptance, 22 October 2026: scheduled backup run 39725364898 and scheduled
monitor run 39763250399 completed successfully on GitHub. Earlier first-run
qualifications are resolved; [Acceptance](Acceptance.md) records exact sources
and limitations. Future execution still needs routine observation.


Requirements: NFR-REL-002, NFR-OPS-001/002, NFR-DEP-001. The live application and
database cutover are verified separately; automation below is not accepted merely
because the workflow files exist. Record actual run/issue/artifact IDs under logs/.

## External health and GitHub issue alerts

`production-monitor.yml` requests a check every five minutes, offset from the hour.
Only public /health is requested, with ordinary TLS and a bounded timeout. Exactly
HTTP200 with status UP is healthy. A bot-owned state issue persists the consecutive
failure count; the third failure opens one outage issue. Further failures reuse it;
recovery closes it and resets the count. Do not close/edit the state issue.

The user approved GitHub issue notifications. Watch repository issues or subscribe
to the monitor/alert issues and verify receipt during the alert exercise. Manual
workflow dispatch with `exercise=true` creates a distinct EXERCISE issue, processes
three synthetic failures and recovery, and does not alter live monitor state or
make the live endpoint fail. Capture that issue URL and received notification.

GitHub schedules are best-effort: jobs can be delayed/dropped and public-repository
schedules can be disabled after inactivity. Watch workflow activity and do not
claim a guaranteed five-minute SLA. There is no aggregate five-minute HTTP5xx
metric on this free stack, so the specification's conditional >5% metric alert
cannot be evaluated here; uptime/status alerts are implemented. If strict wall-clock
cadence is required, use a dedicated external scheduler/monitor.

## Restricted backup identity and secrets

1. In DBeaver, duplicate the owner connection, retaining verified TLS and project
   reference, but set database `facilityflow_prod` and owner username
   `postgres.PROJECT_REF`. Check current_database/current_user before proceeding.
2. Run [production-backup-role.sql](../workflow/database/production-backup-role.sql)
   as a complete script. It creates/revalidates a restricted backup role, grants
   read-only schema/table/sequence access and explicit SELECT RLS policies.
   Unexpected membership, out-of-scope direct grants, writes or ownership cause rollback; inspect rather
   than granting bypass/superuser. Reapply after future schema migrations.
3. Privately assign a new strong backup-role password and enable LOGIN. Use
   `\password facilityflow_prod_backup` in psql, or privately replace a password
   literal in `ALTER ROLE facilityflow_prod_backup LOGIN PASSWORD '...';` via the
   owner connection. Never save/share password-bearing SQL or screenshots.
4. GitHub environment **production-backup** must allow only `master` and temporarily
   the reviewed setup branch for initial rehearsal. It has no required reviewer
   on scheduled jobs. Store these environment secrets:

   | Secret | Value |
   | --- | --- |
   | BACKUP_DATABASE_PASSWORD | New backup-role password; never app/migrator/owner password |
   | BACKUP_ENCRYPTION_PASSPHRASE | Independent randomly generated passphrase, at least 32 characters |

   Keep a recoverable encryption passphrase in the team's password manager. Losing
   it makes the encrypted backups unusable. Remove the setup-branch permission
   after reviewed merge; other repository writers must not receive backup access.

## Encrypted backup and restore rehearsal

`production-backup.yml` runs every 12 hours (margin against the 24-hour RPO target),
or manually. Native PostgreSQL17.11 tools run inside a disposable CI service. The
source is explicitly facilityflow_prod with the read-only backup identity and
verify-full TLS. pg_dump uses custom format, retains ACLs/RLS and all application
schema objects/data, and enables row security with full read policies. Database
roles are provisioned separately; no owner/migrator password is in the backup job.

The temporary plaintext dump is encrypted with GPG AES256. Only the encrypted
dump and checksum are uploaded, retained for 14 days. The actual encrypted artifact
is decrypted, checked byte-for-byte, restored into a fresh **local CI database**,
then checked for schema version6, account readability and restricted runtime
permissions. Roles are recreated without login in that disposable service. Remote
Production connection settings are explicitly replaced before restore. Nothing
is restored into Production or development. Plaintext files are deleted on exit;
the container is destroyed with the job. Failures create a GitHub issue.

Record successful artifact upload, restore result and measured elapsed time
(target RTO 4h). A workflow schedule is not proof of a current backup: inspect the
last successful artifact and alerts. Failed uploads fail the job. GitHub delays,
disabled schedules, credential expiry and artifact retention are operational risks;
keep an off-platform copy of a verified encrypted backup for the submission.

To rehearse before merge, manually dispatch the existing CI/CD workflow on the
setup branch. Its manual-only operations jobs call the backup/restore workflow
and synthetic alert exercise. They never stage or promote Production. Normal PR
CI still deploys Preview and smoke-tests development without Production secrets.

For actual recovery: stop application writes, provision the approved restricted
roles on a trusted replacement server, restore the encrypted archive into a fresh
database named facilityflow_prod using its migrator identity with --no-owner while
retaining ACLs/policies, verify schema/runtime restrictions and data integrity, then
stage/smoke/human-promote updated connection settings. A restore rehearsal on CI
does not authorize overwriting the live database. Use the deployment owner's
incident/change procedure for a real recovery.

## Application rollback and final submission

The pre-cutover artifact uses the development database and is not a valid rollback
target. Once a newly tested isolated candidate is promoted, the preceding isolated
Production deployment is eligible for `.github/workflows/rollback.yml`. Dispatch
with its known-good URL and confirmation ROLLBACK; protected human approval is
still required. Check live alias and health/authentication afterwards, then stage
and promote the desired release again if needed. Record both artifact IDs and
post-rollback checks; no schema/data rollback is performed by application rollback.

Before submission, retain fresh Preview smoke, backup/restore, alert/notification
and rollback evidence. Commit/push the setup artifacts; merge only after green CI
and teammate approval. The submission branch is master. Outstanding evidence must
remain labelled pending rather than inferred from the presence of automation.

References: [GitHub scheduled-event limitations](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule),
[pg_dump](https://www.postgresql.org/docs/17/app-pgdump.html),
[pg_restore](https://www.postgresql.org/docs/17/app-pgrestore.html).
