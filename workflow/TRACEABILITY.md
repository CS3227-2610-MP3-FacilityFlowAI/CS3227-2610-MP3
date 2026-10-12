# Requirement Traceability

Status: approved specification baseline v0.1, 12 October 2026. Authentication
foundation implementation and local test evidence were added on 13 October
2026; teammate review and release verification remain pending.

Ranges may share a row only while every ID has the same source, status and empty
implementation/evidence fields. Split a range before implementation when files,
tests or status differ. Never mark verified without executed command/result or
review evidence.

| Requirement IDs | Authoritative section | Implementation | Test/evidence | Status |
| --- | --- | --- | --- | --- |
| REQ-SH-001–003 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | `identity/`, `web/SecurityConfiguration.java`, `web/RoleLoginSuccessHandler.java`, role controllers/templates and migration V1 | `SecurityIntegrationTest` login, role-routing and denial tests; `./gradlew test` passed 13 Oct 2026 | implemented and locally verified; teammate review pending |
| REQ-SH-004 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | logout plus persisted active/role/session-version refresh in `web/PersistentAccountVerificationFilter.java`; account-change use cases pending | `SecurityIntegrationTest` logout, deactivation, role-change and version-invalidation tests passed 13 Oct 2026 | partially implemented; teammate review pending |
| REQ-SH-005 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | Spring Session JDBC migration/configuration and `sessionCookieSerializer` | secure/HttpOnly/SameSite/browser-cookie test passed 13 Oct 2026 | implemented and locally verified; teammate review pending |
| REQ-SH-006–011 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | pending | product acceptance pending | approved |
| REQ-SH-012 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | role routes and authenticated header in `templates/dashboard.html`; change-password/about pending | role isolation/header rendering covered by `SecurityIntegrationTest`; `./gradlew test` passed 13 Oct 2026 | partially implemented; teammate review pending |
| REQ-SH-013–017 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | pending | product acceptance pending | approved |
| REQ-SH-018 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | authorization-first `ai/AiAccessGuard.java`; assistant endpoints/mutation contract pending | `AiAccessGuardTest`; `./gradlew test` passed 13 Oct 2026 | partially implemented; teammate review pending |
| NFR-SEC-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | Spring Security role/CSRF/session/header configuration, parameterized JDBC repository and Thymeleaf encoding | `SecurityIntegrationTest` plus `IdentityAuthorizationTest`; `./gradlew test` passed 13 Oct 2026 | authentication portion implemented; PostgreSQL/browser verification pending |
| NFR-SEC-002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | environment-only datasource secrets and credential-erasing session principal | session serialization test excludes plaintext/hash; `./gradlew clean check` passed 13 Oct 2026 | authentication portion implemented; deployment secret scan pending |
| NFR-PERF-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | 50-user performance result pending | approved |
| NFR-A11Y-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | labeled server-rendered login/logout forms | no-JavaScript markup integration test passed 13 Oct 2026; Chrome/keyboard/contrast checks pending | partially implemented |
| NFR-A11Y-002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | Chrome/responsive checks pending | approved |
| NFR-REL-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | fallback/restore evidence pending | approved |
| NFR-OPS-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | monitoring/log evidence pending | approved |
| NFR-DEP-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | deployment separation pending | approved |
| NFR-DOC-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | documentation/reuse/process review pending | approved |
| REQ-RQ-001–011 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | pending | RQ-A01–A07 pending | approved |
| REQ-RQ-012–017 | [Smart Report Assistant](specs/ROLE-REQUESTER.md#smart-report-assistant) | pending | RQ-AI01–AI03 pending | approved |
| REQ-TECH-001–011 | [Technician manual workflow](specs/ROLE-TECHNICIAN.md#manual-workflow-requirements) | pending | TECH-A01–A06 pending | approved |
| REQ-TECH-012–017 | [Work Plan Assistant](specs/ROLE-TECHNICIAN.md#work-plan-assistant) | pending | TECH-AI01–AI03 pending | approved |
| REQ-MGR-001–012 | [Manager request/review](specs/ROLE-MANAGER.md#request-and-review-requirements) | pending | MGR-A01–A05 pending | approved |
| REQ-MGR-013–015 | [Manager accounts](specs/ROLE-MANAGER.md#account-administration) | pending | MGR-A06 pending | approved |
| REQ-MGR-016–019 | [Category and skill administration](specs/ROLE-MANAGER.md#category-and-skill-administration) | pending | MGR-A07 pending | approved |
| REQ-MGR-020–025 | [Triage Assistant and matching](specs/ROLE-MANAGER.md#triage-assistant-and-deterministic-candidate-ranking) | pending | MGR-A08–A10 pending | approved |
| REQ-AI-001–006 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | pending | gateway contract tests pending | approved |
| REQ-AI-007–010 | [Usage and audit metadata](specs/INTEGRATION-SPEC.md#usage-and-audit-metadata) | pending | telemetry/redaction tests pending | approved |
| AI-SEC-001 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | `identity/IdentityAuthorization.java` and `ai/AiAccessGuard.java` | denied-before-context-and-call test in `AiAccessGuardTest`; `./gradlew test` passed 13 Oct 2026 | guard implemented; endpoint integration/review pending |
| AI-SEC-002–022 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | pending | AIS-T01–T14 pending | approved |
| AI-SEC-023–027 | [Human/process gates](specs/AI-SECURITY-SPEC.md#human-and-development-process-gates) | pending | AIS-T15 and review evidence pending | approved |
| AI-SEC-028–030 | [Response freshness and leakage controls](specs/AI-SECURITY-SPEC.md#response-freshness-and-leakage-controls) | pending | AIS-T16–T18 pending | approved |

## Update rule

The implementing pull request replaces pending with exact source/test paths for
its IDs, records the approved specification commit, and leaves unrelated rows
unchanged. Verification records the environment, command, date, result and
reviewer; a test file without an executed result is implemented, not verified.
