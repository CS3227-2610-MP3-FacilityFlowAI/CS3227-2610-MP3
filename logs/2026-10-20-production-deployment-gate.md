# 20 October 2026 independent Production deployment gate

- User task: address master Production-candidate smoke failure caused by missing
  environment-scoped smoke credentials.
- Authority: NFR-DEP-001, NFR-DOC-001, approved technical release specification.
- Baseline: deployment branch2699447; current master9151650 (PR13 merge), PR16
  remains open according to GitHub API. Existing uncommitted edits preserved.
- Verified remote failure: run39382394925 failed Stage Production candidate at
  Smoke test staged candidate; Production promotion skipped.
- Root cause: VERCEL_DEPLOY_ENABLED enabled both Preview and master staging,
  while only Preview had been configured. Environment secrets do not transfer.
- Fix: require separate PRODUCTION_DEPLOY_ENABLED=true in addition to shared
  deploy switch and master push. Missing/unset/false defaults to no staging.
  Add credential preflight before linking/staging Production. Preview gate and
  protected promotion gate unchanged; bypass secret remains conditional on
  platform protection, not required for unprotected deployments.
- Files: ci-cd.yml, deployment-gates.test.cjs, Deployment.md, scoped evidence,
  reuse/traceability and handoff updates. No secret copied or value recorded.
- Executed: regression evaluates actual workflow expressions over9 switch
  combinations, wrong-branch/PR/manual events, Preview enablement and fork deny.
  Executes actual preflight shell with four credential cases, checks no canary
  secret appears in output. Existing synthetic smoke regression passed;
  git diff --check passed. Full Java suite not repeated for automation-only fix.
- Fresh verifier: pending. Hosted new-revision CI and subsequent master check
  pending. Historical failed run is not retroactively repaired by this change.
- Human teammate review required before merge. No merge/promotion performed.
- Next: review/push PR16 update; leave Production deploy/promotion false until
  separate Production database/config/secrets/protected environment are ready.
Fresh verifier independently reran gate/preflight regression, existing synthetic
smoke regression and whitespace: all passed; no blockers. Confirmed environment
secret scope, preflight ordering before staging, unchanged protected promotion
and Preview/fork behavior. No external settings/secrets/deployment mutated.
