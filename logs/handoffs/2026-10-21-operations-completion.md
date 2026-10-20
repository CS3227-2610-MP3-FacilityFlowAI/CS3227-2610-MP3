# Operations completion handoff

- Source: root Codex; destination: deployment_verifier for read-only review.
- User authority: finish remaining submission deployment requirements, including
  backups/restore, monitoring/alerts, Preview verification, rollback and reviewed
  submission branch. User selected GitHub issue alerts and will configure a new
  encryption secret when tested workflow is ready.
- IDs: NFR-REL-002, NFR-OPS-001, NFR-SEC-001/002, NFR-DEP-001, NFR-DOC-001.
- Inputs: current approved specs, production cutover log, operations files below.
- New scope: .github/scripts/health_monitor.py and *_test.py, production-backup.sh,
  production-monitor.yml, production-backup.yml, manual operations rehearsal jobs
  in ci-cd.yml, production-backup-role.sql, expanded ProductionDatabaseIsolationTest.
- Semantics: 5-minute external health schedule, persisted GitHub issue state,
  third-failure alert/dedup/recovery, isolated synthetic exercise. Backup every
  12h, restricted read-only role with explicit RLS SELECT, AES256 artifact only,
  actual encrypted artifact decrypted and restored in disposable postgres17.11
  service. Restore commands override all remote connection settings; no live
  restore. Recovered runtime/schema restrictions and elapsed RTO checked.
- Verified so far: monitor unit tests, shell syntax and ProductionDatabaseIsolationTest
  (including backup role read/no writes/no cross-environment access) passed. Full
  application checks running. No actual hosted backup/restore/alert exercise yet.
- Request: inspect security/correctness and completeness; flag blockers. Read-only,
  no secrets/config files, cloud/DB changes, messages, deploy, commit or merge.
  No tool/API response should print keys or raw environment data.
- Suggested skills: Supabase/Postgres best practices, Ponytail review, handoff.
- Known limits: GitHub scheduled jobs may be delayed/dropped; no aggregate 5xx
  telemetry exists on this free stack. Backup secret setup/role SQL user execution
  and actual rehearsal still needed. Human teammate review required before merge;
  protected production approval remains. Current Production must stay available.
- Next: return review findings; root owns fixes, artifacts, credentials guidance,
  branch push/PR and verification. No broader authority delegated.
- Status: ready for review. Evidence must distinguish planned from executed.

## Verification update

- Full Gradle check/bootJar passed (9m52s); focused latest database isolation and
  backup-role regression passed separately (36s). An earlier overlapping test
  attempt hit a Windows report lock; sequential rerun passed.
- Nine Python monitor/backup-boundary tests passed. Actionlint v1.7.12 and diff
  whitespace checks passed. Independent deployment_verifier cleared monitor
  partial-state-write reconciliation fixes after review.
- User confirmed BACKUP_ENCRYPTION_PASSPHRASE saved. Backup role/password setup
  guidance delivered; BACKUP_DATABASE_PASSWORD and hosted rehearsals pending.
- No hosted backup, notification receipt or rollback rehearsal is claimed yet.

Hosted rehearsal follow-up: run39462731538 created duplicate exercise state issues
18-20; no actual alert evidence accepted. Added per-invocation state issue identity
reuse and known-alert GET reconciliation for issue-list lag; 11 regression tests
cover this transport behavior. Backup secret was not yet present; user correcting.
Fresh verifier to review this fix before accepting rerun.
