# 20 October 2026 merged Production candidate verification

- Task: verify user-reported approval/merge and Production staging outcome.
- Authority: NFR-DEP-001 as amended by ADR-007, NFR-DOC-001; Production human
  promotion and NFR-REL-002/NFR-OPS-001 release gates remain in effect.
- GitHub API confirmed PR16 merged as313ca61d24b694ba47589e1cd615b99362cef384,
  with ngkhengyang approval. CI run39437565941 completed success on that revision.
- Test/package, secret scan and container build succeeded. Stage Production
  candidate succeeded, including smoke credential preflight, upload validation,
  staged Vercel deployment and health/login/authenticated safe-read smoke.
- GitHub deployment7202220659 succeeded at:
  https://facilityflow-qqhsys68l-yooplo.vercel.app
- Run evidence:
  https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/actions/runs/39437565941
- Production promotion job skipped. No promotion command invoked by this session;
  no domain-assignment or full public-release claim. Existing database reused
  under user-approved ADR-007, not a newly provisioned Production database.
- Production environment metadata listed required human reviewers. No secret
  values or unnecessary personal data inspected/recorded. Branch working files
  were clean before this evidence update.
- Remaining: backup/restore, monitoring/alert and exposed-credential-rotation
  evidence; human approval before promotion. No acceptance claimed for these.
- Verification method: read-only GitHub API checks; no additional browser/model
  calls or database queries/mutations. User approval of merge does not silently
  grant Production promotion or waive remaining specification acceptance.
- Human reviewer/outcome: ngkhengyang PR approval verified; release/promotion
  approval pending. Evidence is saved locally, not pushed to the merged branch.

## Subsequent approved promotion and live checks

- The user explicitly confirmed human approval for promotion and later reported
  the protected promotion job completed. This supersedes the pending-promotion
  status above; it does not establish completion of other release requirements.
- GitHub API independently verified CI run39437565941, attempt5, completed
  successfully at313ca61d24b694ba47589e1cd615b99362cef384. Test/package, secret scan,
  image build, Production staging and protected Production promotion succeeded.
- GitHub Production deployment7202875349 reported success for the tested candidate:
  https://facilityflow-p8kf451k1-yooplo.vercel.app
- Promotion job evidence:
  https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/actions/runs/39437565941/job/118695738972
- Public Production checks executed after promotion:
  https://facilityflow-ai.vercel.app/health returned HTTP200 with status UP;
  https://facilityflow-ai.vercel.app/login returned HTTP200 with username and
  password form controls. No credentials or form submissions used for these checks.
- The earlier promotion attempt failed with Vercel User not found (404). The
  user was guided to replace the Actions token; token contents and replacement
  were not independently inspected. Successful promotion does not establish the
  exact authentication failure cause or old-token revocation.
- No authenticated smoke or AI invocation repeated on the public domain by this
  session. Staged smoke passed in CI. Backup/restore, monitoring/alert, and the
  previously exposed provider-key rotation remain unverified.
- Evidence remains local; no new commit, push, merge, database change or rebuild
  was performed by this verification session.

## Provider-key rotation confirmation

- The user confirmed provider-key rotation completed. This supersedes the
  pending rotation status above and is user-reported evidence, not an independent
  inspection of provider credentials, revocation or deployed configuration.
- No key values read or recorded. A successful Production AI invocation after
  rotation has not been verified by this session.
- Backup/restore and monitoring/alert evidence remain outstanding.
