# Compressed-response scope remediation

- Increment: `revert(ai): defer compressed provider responses`
- Requirements: AI-SEC-010/019 and INTEGRATION-SPEC transport/decoder rules.
- Change: removed gzip negotiation, decompression and the unapproved compressed-
  wire byte cap. The HTTPS adapter again requests `Accept-Encoding: identity`
  and accepts only absent/identity response encoding; compressed support remains
  deferred until an approved revision defines wire/decompressed limits,
  encoding-chain policy, cleanup, telemetry and abuse tests.
- Focused verification: `./gradlew.bat test --tests
  sg.edu.nus.facilityflow.ai.provider.SocLlmHttpsProviderAdapterTest --tests
  sg.edu.nus.facilityflow.ai.provider.ProviderArchitectureTest --no-daemon` —
  pending in this working increment.
- Review/approval: this is a scope correction to the current authoritative
  specification; fresh Security Reviewer and teammate verification remain
  pending.
- Corrections/risks: the historical Iteration 2I log is retained as evidence of
  the reverted experiment; it is not evidence that compressed transport is
  currently supported.
