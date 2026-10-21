# Post-merge deployment acceptance

Deployment operational acceptance passed for PR17's released implementation.
The desired PR17 artifact is live after an actual protected rollback/restoration
rehearsal. This record covers deployment checks, not general load/security/AI
quality acceptance or guaranteed future RPO/RTO and schedule timing.

## Reviewed source and current release

- Requirements: NFR-DEP-001, NFR-REL-002, NFR-OPS-001, NFR-SEC-001, NFR-DOC-001.
- PR17 approved by teammate ngkhengyang and merged at
  f56d15e6e4406936a6d277abdb1e3f1517d62153 on21October2026 11:12:23SGT.
- [Release39537286873](https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/actions/runs/39537286873):
  test/package, secret scan, container build, staged authenticated smoke and
  protected Production promotion succeeded. User approved the protected gate.
- Desired artifact: https://facilityflow-7fiqyuutw-yooplo.vercel.app,
  dpl_APo9iVtBBeKBpsBBMAXWeZpmciVj. Independent alias API confirmed this deployment
  on https://facilityflow-ai.vercel.app after restoration.

## Actual rollback and restoration

- [Rollback39538161985](https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/actions/runs/39538161985),
  job119019539131, succeeded after separate human approval. Actual live alias
  resolved back to dpl_8MmfcCaFSfsCoVmgWJGsVzkMG1sQ /
  facilityflow-3vhxmdjas-yooplo.vercel.app; independent healthHTTP200/statusUP.
- Original release promotion job rerun without rebuild: attempt2/job119020134233
  succeeded after separate human approval. Overall release attempt2 succeeded.
  Live alias returned to desired PR17 artifact; healthHTTP200/statusUP, login200,
  anonymous /manager and /requester/requests302 to /login.
- User confirmed final live Manager sign-in, prod-smoke Requester sign-in and
  synthetic AI suggestions all work. This is user-observed browser evidence.
- Neither rollback nor restoration restored a database or changed application data.
  Both artifacts use the isolated Production database; pre-isolation artifacts
  were excluded. No live requests were submitted by the agent.

## Operational workflows and recovery

- Both operational workflows active on master. Temporary setup-branch backup
  policy63736117 removed; independently confirmed only master is allowed.
- User confirmed tightened production-backup-role.sql rerun successfully through
  owner connection. This is user-reported SQL execution, not an agent database call.
- [Master backup39537377934](https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/actions/runs/39537377934)
  succeeded: encrypted export, decrypt/compare, isolated PostgreSQL restore,
  schema/account/runtime restriction checks and encrypted artifact upload.
- Artifact12318247050 contains only encrypted dump and basename SHA256 sidecar.
  Independent download verified checksum and portable filename. Encrypted local
  copy retained at ignored local-data/backups/production-backup-39537377934.zip;
  no passphrase collected or plaintext retained. Prior rehearsal measured20s;
  see operations log for its scope and dataset limitation.
- Live monitor39537535771 and final restored-release monitor39538564532 passed.
  Initial monitor39537379986 recorded one failure, no outage alert; an independent
  probe returnedHTTP200/statusUP after22.6s, beyond the monitor's20s timeout.
  Subsequent healthy checks reset the count; the transient was not suppressed.
- Synthetic third-failure alert23 creation/recovery and notification delivery were
  already verified before merge. Notification receipt was user-confirmed.

Schedules are enabled on master and manually verified, but the first timer-triggered
executions were not yet observed. GitHub schedules are best-effort. The next owner
must inspect scheduled activity, backup artifacts/retention and alerts; measured
restore time does not guarantee future recovery duration. Documentation-only
acceptance records are separate from the already reviewed/released implementation.
