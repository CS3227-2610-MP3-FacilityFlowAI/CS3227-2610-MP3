# Smart Report result contract increment

- Increment: `fix(requester-ai): enforce the Smart Report result contract`
- Requirements: REQ-RQ-014, AI-SEC-010/011/016.
- Change: the provider parser now explicitly enforces Low/Normal/High/
  Emergency urgency values and requires at least one draft/suggestion or
  missing-information question before constructing a typed result. Existing
  strict fields, catalogue, size, normalization, markup and control checks are
  retained.
- Focused verification: `./gradlew.bat test --tests
  sg.edu.nus.facilityflow.ai.provider.SocLlmSmartReportAssistantTest
  --no-daemon` — BUILD SUCCESSFUL.
- Review/approval: provider contract is locally verified; role orchestration
  and v0.2 role-owner/teammate review remain pending.
- Corrections/risks: this increment does not add the Requester HTTP/service
  seam, which remains blocked by the pending role review noted in the handoff.
