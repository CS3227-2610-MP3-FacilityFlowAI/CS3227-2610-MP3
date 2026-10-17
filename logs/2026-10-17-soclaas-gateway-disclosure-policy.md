# Request-specific disclosure policy increment

- Increment: `security(ai): enforce request-specific output disclosure policy`
- Requirements: AI-SEC-016/030, REQ-AI-008.
- Change: provider adapters assemble narrow deny material server-side for each
  request. It includes provider-secret matching without exposing the key,
  normalized fixed delimiters/canaries/template content, and Technician
  request/assignee identifiers. The deny material is never placed in the model
  prompt. Matches fail closed as `AI_INVALID_OUTPUT` before typed results are
  returned.
- Focused verification: `./gradlew.bat test --tests
  sg.edu.nus.facilityflow.ai.provider.SocLlmWorkPlanAssistantTest --tests
  sg.edu.nus.facilityflow.ai.provider.SocLlmSmartReportAssistantTest --tests
  sg.edu.nus.facilityflow.ai.provider.SocLlmTriageAssistantTest --no-daemon` —
  BUILD SUCCESSFUL. Tests cover a Technician display-ID canary and unique
  normalized unauthorized-context canaries for Smart Report and Triage.
- Review/approval: implementation Security Reviewer and fresh verifier remain
  pending; no approval is claimed from these local tests.
- Corrections/risks: Requester/Manager role orchestration is not present, so
  account-specific deny material will be expanded only in those authorized
  seams after their pending role review.
