# SoCLaaS gateway readiness recovery increment

- Increment: `fix(ai): recover readiness after negative cache`
- Requirements: AI-SEC-020, NFR-REL-001, REQ-AI-004/005.
- Change: lifecycle readiness now asks the provider core for its current
  generation. A call after the 60-second negative cache expires starts or joins
  a fresh readiness generation instead of replaying the startup stage.
- Focused verification: `./gradlew.bat test --tests
  sg.edu.nus.facilityflow.ai.provider.SocLlmProviderRuntimeFactoryTest
  --no-daemon` — BUILD SUCCESSFUL.
- Full verification: `./gradlew.bat test --no-daemon` — BUILD SUCCESSFUL;
  475 tests, 0 failures, 0 errors, 0 skipped.
- Review/approval: implementation review remains pending; v0.2 role-owner,
  Security Reviewer, teammate and fresh-verifier approvals are not claimed.
- Corrections/risks: this increment does not authorize the still-pending
  Requester/Manager orchestration or Work Plan safety-corpus work.
