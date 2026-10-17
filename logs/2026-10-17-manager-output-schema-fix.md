# Manager output schema fix

- Date/timezone: 17 October 2026, Asia/Singapore.
- AI/tool/agent role: Codex; direct implementation and verification.
- Task/intention: fix repeated `AI_INVALID_OUTPUT` from the Manager Triage gateway.
- Sources: approved INTEGRATION-SPEC prompt contract and Triage result;
  ROLE-MANAGER REQ-MGR-021/022/025; existing provider adapter and tests.
- Root cause: the prompt mentioned a schema without providing it. Returned
  category/priority/provenance objects did not match the required flat typed result.
- Files: `SocLlmTriageAssistant`, `SocLlmTriageAssistantTest`, `ManagerTriageService`,
  and the TRIAGE entry in `AiFeatureCatalog`.
- Fix: supply all six exact fields, raw-JSON instructions, scalar/array types,
  enum/length limits, active catalogue rules, unique disjoint skill tags and
  empty-list behavior. Request concise output. Keep strict parsing and authorization.
  Version the invocation metadata as `triage-v2`.
- Verification: transmitted-field regression checks failed before the fix. A temporary
  synthetic-data JUnit probe through the production runtime, HTTPS adapter, shared
  core and typed parser failed before the fix and passed afterward. A second live
  case with an empty skill catalogue and no Manager priority also passed.
  The temporary live probe was removed afterward.
- Regression command: `./gradlew.bat test --tests 'sg.edu.nus.facilityflow.ai.*'
  --tests '*RequesterSmartReportServiceTest' --tests '*ManagerTriageServiceTest'
  --tests '*SecurityIntegrationTest' --console=plain` passed 443 tests with
  zero failures, errors or skips across the combined requester/manager fixes.
- Accepted/rejected suggestions: accepted schema alignment; did not increase
  the 800-token cap or allow unvalidated partial results.
- Security: live probes used synthetic inputs and no database access. No credentials,
  raw provider outputs or protected data recorded. Candidate ranking stays deterministic.
- Retest: restart with the existing environment, log in as Manager, obtain a fresh
  post-login CSRF token and repeat POST `/manager/requests/{displayId}/triage`.
- Limits/review: live checks cover provider output validation, not the user's
  Postman session. Model output remains probabilistic. Human review pending.
