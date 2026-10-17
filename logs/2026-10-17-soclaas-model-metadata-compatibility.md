# SoCLaaS model metadata compatibility fix

- Goal: restore readiness for the configured `qwen3.8:27b` model after the
  provider added context-limit and nested `soclaas` metadata to `/v1/models`.
- Requirements: REQ-AI-003/006 and AI-SEC-010/020.
- Source: confirmed local failure report, `ProviderEnvelopeDecoder`, current
  integration/security specifications and existing provider tests. No provider
  credential, raw production response or unrelated Request data was accessed.
- Change: allowlisted `context_length`, `context_window`, `max_context_length`,
  `max_model_len` and `soclaas` at each model entry. They remain unparsed inert
  metadata; every other unlisted model-entry field still fails closed.
- Regression evidence: a synthetic response with `qwen3.8:27b`, every new field
  and nested `soclaas` content must decode and drive lifecycle readiness to
  `READY`. Existing malformed-envelope coverage retains explicit rejection of an
  unknown top-level model-entry field.
- Red-capable reproduction: the focused decoder/runtime command ran 81 tests
  before the production change and failed exactly the two new compatibility
  cases with `ProviderFailure`.
- Focused verification: `gradlew.bat test --tests
  sg.edu.nus.facilityflow.ai.provider.ProviderEnvelopeDecoderTest --tests
  sg.edu.nus.facilityflow.ai.provider.SocLlmProviderRuntimeFactoryTest
  --no-daemon` — `BUILD SUCCESSFUL` in 17s; all 81 focused tests passed.
- Full verification at the time of this compatibility commit: `BUILD
  SUCCESSFUL` in 3m31s; 478 tests, 0 failures, 0 errors and 0 skipped. The
  current HEAD evidence refresh reran the full suite in 3m15s with 477 tests,
  0 failures, 0 errors and 0 skipped. `git diff --check` passed.
- Human teammate and Security Reviewer review remain pending.
- Agent/session role: primary Codex implementation session. Corrections/risks:
  added fields are inert metadata only; unknown model-entry fields remain
  fail-closed.
- Reuse: new MP3 compatibility work; no MP2 source was consulted or copied.
