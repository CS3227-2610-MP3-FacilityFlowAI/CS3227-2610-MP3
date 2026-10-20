# Submission operations completion

Requirements: NFR-REL-002, NFR-OPS-001/002, NFR-SEC-001, NFR-DEP-001.

User approved GitHub issue alerts and confirmed the independent encryption
passphrase saved in the production-backup GitHub environment. The backup role
uses its own password; setup instructions provided without collecting secrets.

Implemented external health monitoring with third-failure issue alerts/recovery,
synthetic alert exercise, encrypted 12-hour backup and disposable PostgreSQL
restore verification. Scheduled workflows require reviewed merge to master.
GitHub scheduling is best-effort; no guaranteed five-minute wall-clock SLA.

Validation: full Gradle check/bootJar passed, latest focused database isolation
and backup permission regression passed; nine Python tests passed; actionlint
v1.7.12 passed. Earlier concurrent Gradle invocation failed from a Windows test
report lock; rerunning sequentially passed. Fresh verifier reviewed fixes for
alert creation/recovery API partial failures and unexpected backup privileges.

Actual backup/restore, notification delivery, fresh Preview and protected rollback
rehearsal remain pending. No live database restore or production change was
performed by these local checks. Teammate review is required before merge.

## Hosted rehearsal results

- Branch commits: 6b119fb, monitor identity fix5751831, safe diagnostics8ce24f5.
- Draft PR: https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/pull/17
- Fresh Preview run39462104875 passed test/package, secret scan, image build,
  deployment and authenticated smoke using corrected Preview configuration.
- Initial exercise in run39462731538 exited successfully but created duplicate
  state issues18-20; rejected as alert evidence. Fresh verifier reviewed the fix;
  12 Python regressions now pass, including safe backup error classification.
- Corrected hosted run39463571383 created alert23, posted recovery and closed it:
  https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/issues/23
  Notification receipt remains pending user confirmation. Duplicate synthetic
  states19/20 were closed; state18 retained. Live monitor state was not exercised.
- Both backup secret names are present in production-backup. Actual pg_dump failed
  with allowlisted AUTHENTICATION_FAILED; no encrypted artifact/restore accepted.
  User must privately reset backup-role password and update its environment secret.
  No secret values or raw database errors retained. Failure issue24 remains open.
- Daily schedule activation, backup/restore, protected rollback, reviewed merge
  and final post-promotion checks remain incomplete. Production unchanged.

## Credential correction and accepted backup/restore evidence

User confirmed privately updating backup-role password and matching environment
secret. Fresh workflow39464057541 backup job118771107050 succeeded:
https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/actions/runs/39464057541

- Export, AES256 encryption, decrypt/byte comparison, disposable PostgreSQL17.11
  restore, schema6/account read/runtime permission verification: passed in20s.
- Encrypted artifact12283374254, production-backup-39464057541-1, 36544 bytes;
  expiry 4 November2026 01:14:09 SGT. Archive contains exactly encrypted dump and
  SHA256 sidecar. Independently verified encrypted dump checksum after download.
- Encrypted off-platform copy saved under ignored local-data/backups/
  production-backup-39464057541.zip; no decryption passphrase collected or stored.
- Backup failure issues21/24 closed following successful verification. Actual
  Production/development data was never a restore destination.
- Alert exercise creation/recovery verified; notification receipt remains pending.
- Scheduled jobs need reviewed merge to master. Protected rollback rehearsal and
  final post-promotion checks still pending; this log does not claim all deployment
  requirements complete. Recovered rehearsal time is evidence for this dataset,
  not a guarantee of future incident recovery duration or GitHub schedule uptime.

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

## PR17 review corrections

Confirmed and corrected three review findings: rejected out-of-scope direct ACLs
for backup identity (including unrelated SELECT and other databases/object types),
made SHA256 sidecar portable after download, and corrected stale Product Spec
pending isolation status. Targeted database regressions passed46s;13Python tests
passed including real checksum verification from relocated artifact files. New
checksum test stubs Docker/GPG; prior hosted encryption/restore evidence remains
valid for its original run. Source role checks changed without executing live SQL.
Existing checksum sidecars from earlier artifacts still contain original runner
paths; their encrypted-file digests were verified programmatically as recorded.
