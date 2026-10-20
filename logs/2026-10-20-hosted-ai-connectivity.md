# 20 October 2026 hosted AI connectivity investigation

- Task: diagnose Requester AI 503 in deployed Preview; NFR-DEP-001,
  REQ-AI-004/006, AI-SEC-017/019/020 apply.
- Agent: Codex; Ponytail and Context7 used; fresh verification required.
- Verified privately via Vercel CLI: AI_ENABLED=true, approved base URL matches,
  model configured, key variable present. Secret values neither printed nor saved
  to project files. Temporary pulled configuration removed immediately.
- Actual Preview logs: stage=READINESS reason=NETWORK_FAILURE and feature=
  SMART_REPORT code=AI_UNAVAILABLE. One-hour log query exceeded Hobby retention;
  five-minute query succeeded. No raw requests/responses/exceptions recorded.
- Credential-free local Python and Java probes both reached the approved models
  endpoint with verified HTTPS and received expected unauthenticated HTTP 401.
  This establishes local reachability only, not Vercel egress behavior.
- Change: add closed transport diagnostic reasons DNS_FAILURE, TLS_FAILURE,
  CONNECTION_FAILURE, HTTP_IO_FAILURE and INTERNAL_FAILURE, without changing
  user-facing failures, retries, TLS checks, authorization or data writes.
- Tests: focused provider diagnostics/adapter suites and bootJar pending.
  New redaction regression covers nested exceptions and all transport categories.
- Unresolved: exact deployed transport failure; no speculative timeout, network,
  CSP or TLS-verification weakening. Hosted AI success remains unverified.
- Human review: pending. No merge or production promotion authorized/performed.
Focused provider diagnostics/adapter run passed: 30 tests across two suites,
zero failures/errors/skips, JaCoCo and bootJar; duration 54s. Fresh verifier found
no blockers: closed enums only, bounded traversal, redaction and unchanged
failure/timeout/cancel/TLS semantics. User independently supplied same safe
READINESS/NETWORK_FAILURE code. Next: deploy diagnostic revision through PR CI
and inspect only enum reasons. Hosted root cause remains unresolved.
