# SoCLaaS completion output compatibility

- Source: redacted live `qwen3.8:27b` Chat Completions response supplied by the
  developer, followed by local contract tests.
- Observed failure: the 800-token completion was exhausted by reasoning,
  `message.content` was null or truncated, `finish_reason` was `length`, and the
  strict provider decoder rejected current inert SoCLaaS/vLLM metadata.
- Fixes: declare the exact bounded Work Plan JSON schema in the system message;
  request `reasoning_effort: none`; accept only the confirmed inert completion
  metadata while retaining strict content, finish-reason, usage and unknown-field
  validation.
- Secrets and raw protected Request data were not recorded.
- Verification:
  `./gradlew.bat test --tests
  sg.edu.nus.facilityflow.ai.provider.ProviderEnvelopeDecoderTest --tests
  sg.edu.nus.facilityflow.ai.provider.SocLlmHttpsProviderAdapterTest --tests
  sg.edu.nus.facilityflow.ai.provider.SocLlmWorkPlanAssistantTest` passed locally.
