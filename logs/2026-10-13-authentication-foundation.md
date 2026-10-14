# 2026-10-13 Authentication Foundation

- Date/timezone: 13 October 2026, Asia/Singapore.
- AI/tool/agent role: Codex implementation and verification session; no
  subagents used.
- Task: implement and push the authentication baseline needed by the three role
  slices and future SoC LLM endpoints.
- Prompt/intention summary: continue from the prior MP3 discussion, choose the
  authentication assignment, follow the MP3 description and lecture security/
  specification principles, and update GitHub quickly without overwriting the
  existing planning-document edit.
- Sources/approved revision: `origin/master` commit `0778d6e`; approved product,
  technical, integration and AI-security specifications; MP2 authentication
  specification at the verified source commit recorded in `workflow/REUSE.md`.
- Requirement IDs: REQ-SH-001–005, REQ-SH-012, REQ-SH-018, NFR-SEC-001,
  NFR-A11Y-001 and AI-SEC-001. Several are only partially implemented as shown
  in `workflow/TRACEABILITY.md`.
- Files affected: Gradle build/wrapper; identity, AI guard and web Java sources;
  Flyway migration/configuration/templates; tests; README, guides, security,
  decisions, reuse and traceability records.
- Important decisions: pin Java 25/Gradle 9.1.0/Spring Boot 4.1.1; use Argon2id;
  use JDBC-backed browser sessions; revalidate persisted active state, role and
  session version per request; authorize before AI context construction or model
  invocation. See ADR-003.
- AI suggestions accepted: service-level authorization in addition to route
  rules; uniform login failure; explicit secure-cookie serializer; H2
  PostgreSQL-mode tests for the fast suite; preserve the dirty DOCX by using an
  isolated worktree.
- AI suggestions rejected/deferred: no hard-coded demo accounts or partial demo
  seed because the approved seed must be atomic with representative Requests;
  no OAuth, registration, MFA, email verification or password-recovery scope;
  no claim of actual PostgreSQL/browser verification.
- Verification commands/results/evidence: `./gradlew compileJava --stacktrace`
  passed on Java 25.0.4.1. Final `./gradlew clean check && git diff --check`
  passed on 13 October 2026 with 16 tests, zero failures and generated JaCoCo
  output. Tests cover the behaviors listed in `workflow/TRACEABILITY.md`.
- Corrections/unresolved questions: the first compile exposed an unmanaged
  Bouncy Castle version; version 1.82 was pinned. Spring Boot 4 requires its
  dedicated Web MVC test, Flyway and JDBC-session starters; they were added
  after initial failures. Spring Session did not inherit servlet-cookie flags,
  so an explicit cookie serializer was added and the full suite rerun. Actual
  PostgreSQL integration, browser/accessibility checks, account bootstrap,
  account administration and all assistant endpoints remain pending.
- Security considerations: no credentials or secret values were added. Login
  failures are generic, hashes are salted/memory-hard, sessions are invalidated
  on logout/deactivation/session-version change, CSRF and restrictive headers
  remain enabled, and denied AI access produces no context or model call.
- Human reviewer/outcome: pending; AGENTS.md requires teammate review before
  merge.
