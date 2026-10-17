# SoCLaaS gateway error diagnostics

- Request: add console error logging and rough error-type indicators in gateway
  API responses.
- Requirements: REQ-AI-004/005/006, AI-SEC-017/019.
- Implementation: added shared failure descriptions and metadata-only WARN
  logging; all existing feature failure payloads retain their code and include
  a fixed message. Existing HTTP statuses and successful payloads are unchanged.
  Provider discovery/final completion logs identify the stage and safe transport
  or decoder reason. Missing model, empty discovery response, readiness expiry,
  scheduler/executor rejection, input/output validation and disclosure rejection
  have stable diagnostic reasons. Startup logs disabled AI at INFO and invalid
  configuration issue enums at WARN.
- Privacy: no raw provider bodies, prompts, context, credentials, exception
  messages or stack traces are logged. Unknown exceptions produce
  INTERNAL_FAILURE. Public error messages remain broad and do not reveal
  provider internals.
- Tests: new requirement-based response-serialization and provider-diagnostic
  tests cover every existing failure category, missing model, invalid discovery
  envelope, rejected provider authentication, malformed output and unexpected
  canary-bearing exceptions. Existing HTTP tests verify code/message fields for
  Smart Report, Triage and Work Plan.
- Verification: focused provider/diagnostic/configuration suite passed, 171
  tests. The broader command
  `./gradlew.bat test --tests 'sg.edu.nus.facilityflow.ai.*' --tests '*SecurityIntegrationTest' --no-daemon --console=plain`
  passed, 440 tests across 22 suites, zero failures/errors/skips.
  `git diff --check` passed. Tests use deterministic synthetic provider data;
  no live SoCLaaS invocation was made.
- Skill: cs3227-unit-test guided requirement-based regression cases, including
  captured-log leakage checks. No storage behavior was changed.
- Review: teammate/security review remains pending. No commit or push requested
  for this change.
