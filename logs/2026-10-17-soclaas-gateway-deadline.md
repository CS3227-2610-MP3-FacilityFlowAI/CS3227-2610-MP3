# SoCLaaS gateway end-to-end deadline increment

- Increment: `fix(ai): enforce one end-to-end invocation deadline`
- Requirements: AI-SEC-008/009, REQ-AI-005, NFR-REL-001.
- Change: the Technician use-case creates one absolute deadline after role
  authorization; admission, readiness, context loading, freshness checks and
  provider adapters observe that same deadline. Provider feature seams now
  accept the deadline explicitly and no longer reset it.
- Focused verification: `./gradlew.bat test --tests
  sg.edu.nus.facilityflow.technician.TechnicianWorkPlanServiceTest
  --tests sg.edu.nus.facilityflow.ai.provider.SocLlmWorkPlanAssistantTest
  --tests sg.edu.nus.facilityflow.ai.provider.SocLlmSmartReportAssistantTest
  --tests sg.edu.nus.facilityflow.ai.provider.SocLlmTriageAssistantTest
  --no-daemon` — BUILD SUCCESSFUL.
- Additional deadline regression: `TechnicianWorkPlanServiceTest` proves a
  context delay does not move the provider deadline.
- Review/approval: implementation review remains pending; no role-owner,
  Security Reviewer, teammate or fresh-verifier approval is claimed.
- Corrections/risks: durable terminal persistence remains bounded by the
  existing telemetry seam; Requester/Manager orchestration remains pending.
