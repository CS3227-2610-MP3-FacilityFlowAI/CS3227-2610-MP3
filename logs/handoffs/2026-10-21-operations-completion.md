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

Latest hosted status: corrected run39463571383 actually created/recovered alert23;
notification receipt pending. Fresh Preview run39462104875 passed authenticated
smoke. Backup failed AUTHENTICATION_FAILED before dump completion; user password
reset/secret correction required. Both secret names present. No backup artifact or
restore success claimed. See logs/2026-10-21-operations-completion.md for URLs.

Credential correction accepted: backup job118771107050 in run39464057541 passed
actual encrypted backup/restore in20s; artifact12283374254 downloaded encrypted to
ignored local-data/backups and checksum verified. Failure issues21/24 closed.
Notification receipt, reviewed merge/schedule activation, protected rollback and
final post-promotion remain pending. No passphrase read or live restore performed.

## Notification receipt and pre-release baseline

User confirmed receipt of alert23 notification through GitHub/email. Full manual
rehearsal run39464057541 passed test/package, secret scan, container build,
backup/restore and alert exercise; deployment jobs intentionally skipped.
Live alias independently rechecked: facilityflow-3vhxmdjas-yooplo.vercel.app,
id dpl_8MmfcCaFSfsCoVmgWJGsVzkMG1sQ, READY Production, source313ca61d,
health HTTP200/statusUP. This is the isolated known-good baseline for the later
protected rollback rehearsal; no rollback has yet been executed.

User will arrange teammate review. PR17 remains open; no human review/merge
observed. AGENTS.md and workflow/agents/README.md require teammate review and
protected Production approval. Next after approved merge: verify default-branch
schedules; remove temporary chore/separate-production-database permission from
production-backup; verify staged candidate and human-approved promotion; rehearse
protected application rollback to the recorded isolated baseline, then restore
the desired release and verify alias/health/authentication. Do not restore a live
database or use the pre-isolation development-connected artifact. No authorization
to bypass human gates is implied by this handoff. Suggested skills: facilityflow
handoff, Supabase/Postgres best practices and Ponytail. See Operations.md.
