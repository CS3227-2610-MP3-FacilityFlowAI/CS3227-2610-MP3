# Requester output schema fix

- Date/timezone: 17 October 2026, Asia/Singapore.
- AI/tool/agent role: Codex; direct implementation and verification.
- Task/intention: fix repeated `AI_INVALID_OUTPUT` from the Requester Smart Report gateway.
- Sources: approved INTEGRATION-SPEC prompt contract and Smart Report result;
  ROLE-REQUESTER REQ-RQ-013/014/017; existing provider adapter and tests.
- Root cause: the prompt asked for the requested schema but did not supply it.
  The strict parser correctly rejected model-invented structures.
- Files: `SocLlmSmartReportAssistant`, `SocLlmSmartReportAssistantTest`,
  `RequesterSmartReportService`, and the SMART_REPORT entry in `AiFeatureCatalog`.
- Fix: transmit exact scalar/array types, optional null values, field names,
  limits and catalogue rules; request concise raw JSON with no wrapper or provenance objects.
  Preserve strict output validation. Version the prompt and invocation metadata as `smart-report-v2`.
- Verification: the new transmitted-field checks failed before the fix. A temporary
  synthetic-data JUnit probe used the production runtime, HTTPS adapter, shared core
  and typed parser; ordinary requester output failed before the fix and succeeded
  afterward. A second live case with missing location/category/urgency also succeeded.
  The temporary probe was removed afterward.
- Regression command: `./gradlew.bat test --tests 'sg.edu.nus.facilityflow.ai.*'
  --tests '*RequesterSmartReportServiceTest' --tests '*ManagerTriageServiceTest'
  --tests '*SecurityIntegrationTest' --console=plain` passed 443 tests with
  zero failures, errors or skips across the combined requester/manager fixes.
- Accepted/rejected suggestions: accepted prompt alignment; did not relax validation
  or increase the configured output-token cap.
- Security: live inputs were synthetic; no database access was needed for those
  probes. Credentials stayed in process environment; no raw outputs or credentials recorded.
- Retest: restart the application with its existing environment, log in as Requester,
  obtain a fresh post-login CSRF token and repeat the same Smart Report POST.
- Limits/review: live checks cover the provider/typed-output path, not the user's
  Postman session. Probabilistic model output can still fail validation. Human review pending.
