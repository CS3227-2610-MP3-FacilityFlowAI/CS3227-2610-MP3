# Requirement Traceability

Status: approved specification baseline v0.1, 12 October 2026. The gateway
revision candidate v0.2 received stakeholder approval on 16 October 2026;
affected role-owner, Security Reviewer, teammate and fresh-reviewer review
remains pending. Iteration 2A provider-core/readiness decisions received
stakeholder direction and approval on 16 October 2026; specification Security
Reviewer and teammate review remains pending. No revision is implemented or
verified by approval alone. Iteration 2A specification Security Reviewer findings
were addressed in the specification on 16 October 2026; implementation, tests
and teammate review remain pending. The implementation Security Reviewer records
a NO-GO pending implementation and verification of the documented lock-safety,
read-only-handle, package-visibility and decoder-diagnostic remediations.

Ranges may share a row only while every ID has the same source, status and empty
implementation/evidence fields. Split a range before implementation when files,
tests or status differ. Never mark verified without executed command/result or
review evidence.

| Requirement IDs | Authoritative section | Implementation | Test/evidence | Status |
| --- | --- | --- | --- | --- |
| REQ-SH-001 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | private V1 account roles; identity/web login and role routes | DatabaseMigrationTest and SecurityIntegrationTest; merge log | integrated; merge validation recorded below; teammate review pending |
| REQ-SH-002?003 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | IdentityAuthorization, SecurityConfiguration, private JdbcAccountRepository and delegating password verifier | IdentityAuthorizationTest, SecurityIntegrationTest including legacy PBKDF2 login, PostgreSQL repository regression | integrated; teammate review pending |
| REQ-SH-004 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 session/version storage, logout and persisted-account revalidation | DatabaseMigrationTest session cascade and SecurityIntegrationTest invalidation | partial; account-change use cases pending |
| REQ-SH-005 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 private JDBC sessions and secure browser-scoped cookie configuration | SecurityIntegrationTest cookie/session assertions; browser checks pending | integrated; teammate review pending |
| REQ-SH-006 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 audit storage/grants; event-producing services pending | DatabaseMigrationTest runtime permissions; Part 2b log | partial |
| REQ-SH-007 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 Account/audit delete denied; remaining entities/UI pending | DatabaseMigrationTest runtime permissions; Part 2b log | partial |
| REQ-SH-008–013 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | pending | product acceptance pending | approved |
| REQ-SH-014 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 transactional storage; business service transaction scope pending | DatabaseMigrationTest rollback; Part 2b log | partial |
| REQ-SH-015 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | DatabaseInitializer explicit demo mode | DatabaseInitializerTest seed/rollback/idempotence; Part 3 log | locally verified; hosted pending |
| REQ-SH-016 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | DatabaseInitializer default Manager bootstrap | DatabaseInitializerTest bootstrap/concurrency/profile/no overwrite; Part 3 log | locally verified; hosted pending |
| REQ-SH-017 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | bootstrap/demo exceptions supplied; ordinary account authorization pending | initialization tests only | partial |
| REQ-SH-018 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | AiAccessGuard authorization-before-context seam | AiAccessGuardTest; assistant endpoints pending | partial |
| NFR-SEC-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | web/application security evidence pending | approved |
| NFR-SEC-002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | secret-free defaults plus redacted AI configuration with package-limited raw properties and API-key access | AiConfigurationValidatorTest, AiSettingsConfigurationContextTest and AiConfigurationSecurityRegressionTest; gateway configuration log | partial; configuration locally verified; repository/deployment secret separation pending |
| NFR-PERF-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | 50-user performance result pending | approved |
| NFR-A11Y-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | Chrome/accessibility checks pending | approved |
| NFR-REL-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | fallback/restore evidence pending | approved |
| NFR-OPS-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | monitoring/log evidence pending | approved |
| NFR-DEP-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | deployment separation pending | approved |
| NFR-DOC-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | documentation/reuse/process review pending | approved |
| REQ-RQ-001–011 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | pending | RQ-A01–A07 pending | approved |
| REQ-RQ-012–017 | [Smart Report Assistant](specs/ROLE-REQUESTER.md#smart-report-assistant) | pending | RQ-AI01–AI04 pending | v0.1 approved; v0.2 stakeholder-approved 16 Oct; role-owner/teammate review pending |
| REQ-TECH-001–011 | [Technician manual workflow](specs/ROLE-TECHNICIAN.md#manual-workflow-requirements) | pending | TECH-A01–A06 pending | approved |
| REQ-TECH-012–017 | [Work Plan Assistant](specs/ROLE-TECHNICIAN.md#work-plan-assistant) | pending | TECH-AI01–AI04 pending | v0.1 approved; v0.2 stakeholder-approved 16 Oct; role-owner/teammate review pending |
| REQ-MGR-001–012 | [Manager request/review](specs/ROLE-MANAGER.md#request-and-review-requirements) | pending | MGR-A01–A05 pending | approved |
| REQ-MGR-013–015 | [Manager accounts](specs/ROLE-MANAGER.md#account-administration) | pending | MGR-A06 pending | approved |
| REQ-MGR-016–019 | [Category and skill administration](specs/ROLE-MANAGER.md#category-and-skill-administration) | pending | MGR-A07 pending | approved |
| REQ-MGR-020–025 | [Triage Assistant and matching](specs/ROLE-MANAGER.md#triage-assistant-and-deterministic-candidate-ranking) | pending | MGR-A08–A11 pending | v0.1 approved; v0.2 stakeholder-approved 16 Oct; role-owner/teammate review pending |
| REQ-AI-001, REQ-AI-003–004 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | `.env.example`, `application.yml`, `SocLlmProperties`, `AiConfiguration`, `AiConfigurationValidator`, `AiSettingsConfiguration`; model discovery pending | `AiConfigurationValidatorTest`, `AiSettingsConfigurationContextTest`, `AiConfigurationSecurityRegressionTest`; `logs/2026-10-16-soclaas-gateway-configuration.md`; `logs/handoffs/2026-10-16-soclaas-gateway-config-developer-handoff.md`; `logs/handoffs/2026-10-16-soclaas-gateway-config-tester-handoff.md`; `logs/handoffs/2026-10-16-soclaas-gateway-config-security-review.md`; `logs/handoffs/2026-10-16-soclaas-gateway-config-fresh-verifier.md`; iteration 2A exact model-envelope/readiness tests pending | configuration locally verified; provider/model discovery not implemented; iteration 2A specification security findings addressed; teammate review pending |
| REQ-AI-002, REQ-AI-005–006 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | pending | iteration 2A provider-port/fake/decoder, monitor-safety, read-only-handle, package-visibility and readiness/chat race tests pending | implementation Security Reviewer NO-GO; documented remediation, implementation/tests, security re-review and teammate review pending |
| REQ-AI-007–010 | [Usage and audit metadata](specs/INTEGRATION-SPEC.md#usage-and-audit-metadata) | pending | telemetry/redaction tests and reviewed schema revision pending | v0.1 approved; v0.2 stakeholder-approved 16 Oct; Security Reviewer/role-owner/teammate review pending |
| AI-SEC-001–007 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | authorization-first identity seam only; provider port/fake and gateway/prompt controls pending | AiAccessGuardTest; iteration 2A provider-port/raw-decoder, read-only-handle and visibility tests plus AIS-T01–T08 pending | partial identity only; implementation Security Reviewer NO-GO; remediation implementation/tests, security re-review and teammate review pending |
| AI-SEC-008 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | configuration hard ceilings implemented; runtime deadline/output/concurrency/rate enforcement pending | configuration boundary tests and full check | partial; configuration locally verified |
| AI-SEC-009–010 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | pending | iteration 2A streaming bounded-decoder read/close canaries, provider-core cases and later retry cases pending | implementation Security Reviewer NO-GO; documented decoder remediation, implementation/tests, security re-review and teammate review pending |
| AI-SEC-011–018 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | V3 metadata storage prerequisite only; gateway behavior pending | AIS-T09–T14 and gateway cases pending | partial storage; behavior pending |
| AI-SEC-019 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | server-side raw binding, official-origin validation, placeholder/control rejection and redacted settings diagnostics; Available-only core construction pending | 242 configuration tests including fresh security regressions; iteration 2A zero-core/work/call tests pending | partial configuration only; iteration 2A specification security findings addressed; implementation/tests and teammate review pending |
| AI-SEC-020 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | startup-safe disabled/unavailable/available configuration states; provider readiness/lifecycle wiring pending | Spring context binding tests; iteration 2A lock-safety, blocked-port, generation/timeout ordering and non-shared chat cancellation-race tests pending | partial configuration only; implementation Security Reviewer NO-GO; remediation implementation/tests, security re-review and teammate review pending |
| AI-SEC-021–022 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | V3 limiter storage prerequisite only; runtime limiter and prompt templates pending | AIS-T10 and prompt review/tests pending | partial storage; behavior pending |
| AI-SEC-023–027 | [Human/process gates](specs/AI-SECURITY-SPEC.md#human-and-development-process-gates) | pending | AIS-T15 and review evidence pending | approved |
| AI-SEC-028–030 | [Response freshness and leakage controls](specs/AI-SECURITY-SPEC.md#response-freshness-and-leakage-controls) | pending | AIS-T16–T18 and `no-store` cases pending | v0.1 approved; v0.2 stakeholder-approved 16 Oct; Security Reviewer/role-owner/teammate/fresh review pending |

## Update rule

Part 3 storage prerequisites: `V2__maintenance_records.sql`,
`V3__skills_ai_storage.sql`, `identity/DatabaseInitializer.java`,
`DatabaseMigrationTest` and `identity/DatabaseInitializerTest`. V2 supports
REQ-RQ-002/008, REQ-TECH-006/007, REQ-MGR-016/017 and shared history/UTC/transaction
storage; V3 supports REQ-MGR-018/019/023, REQ-AI-007–010 and AI-SEC-008/017/021.
Role endpoints, eligible actors/assignments, accepted AI tags, telemetry handling
and actual limit algorithms remain pending; their full requirements are not
marked implemented from table creation. See Part 3 log for precise executed
evidence and independent failed-test verification. The source specs remain v0.1.

Part 2b exact artifacts: `src/main/resources/db/migration/V1__identity_audit_sessions.sql`,
`workflow/database/bootstrap-roles.sql`, `application-database-migrate.yml` and
`src/test/java/sg/edu/nus/facilityflow/database/DatabaseMigrationTest.java`.
Executed evidence: `logs/2026-10-14-requester-part-2b-database-foundation.md`.
Partial rows describe database prerequisites only; hosted migrations and actual
authentication/Requester services remain unverified/unimplemented.

Part 2a supports the TECHNICAL-SPEC database configuration prerequisite with
`src/main/resources/application.yml`, PostgreSQL/JDBC dependencies,
`operations/DatabaseConnectionCheck.java` and isolated configuration/check tests.
See `logs/2026-10-14-requester-part-2a-supabase.md`. TLS configuration binding and
safe read-only check behavior were unit-tested; no hosted database, certificate,
schema, migrations, sessions or full NFR was verified.

Part 1 supports the TECHNICAL-SPEC stack/structure prerequisite only:
`build.gradle`, `settings.gradle`, Gradle wrapper, `FacilityFlowApplication`,
source `application.yml` and `FacilityFlowApplicationTest`. See
`logs/2026-10-14-requester-part-1-build.md` for executed startup/build evidence.
No functional requirement or complete quality requirement is marked verified
by this scaffold; the table above retains those pending statuses.

The implementing pull request replaces pending with exact source/test paths for
its IDs, records the approved specification commit, and leaves unrelated rows
unchanged. Verification records the environment, command, date, result and
reviewer; a test file without an executed result is implemented, not verified.

## Master/database merge integration

REQ-SH-001-005, NFR-SEC-001-002 and AI-SEC-001 also have the authentication
implementation brought from master: `identity/`, `web/`, role templates,
`SecurityIntegrationTest`, `IdentityAuthorizationTest` and `AiAccessGuardTest`.
Private-schema queries and prefixed PBKDF2 verification preserve the V1-V3
storage/initialization artifacts above; the competing V1 is a test-only H2 fixture.
Merge validation evidence is recorded in `logs/2026-10-15-master-database-merge.md`.
Teammate review and browser/hosted release verification remain pending.

Assignment review correction (16 October 2026): REQ-MGR-004 storage integrity
is strengthened by `V4__require_assignment_timestamp.sql`, requiring timestamps
for new assignments while preserving migrated unknown times (REQ-TECH-003). `DatabaseMigrationTest.requiresAssignmentTimestamp` covers
assigned/active/completed/closed/cancelled states and valid paired records.
See `logs/2026-10-16-assignment-review-fixes.md` for executed validation.

Initializer PR suggestions (16 October): REQ-SH-014-016 / NFR-SEC-002 now have
explicit development-only demo gating and transaction-local five-second lock
waits, with regression tests in DatabaseInitializerTest. Completion metadata
retention after reopening is asserted under REQ-MGR-007/008. Fresh review identified a legacy exception; the user confirmed preserving it.
V4 now retains existing unknown times until reassignment, with an actual V3-to-V4
legacy-data upgrade regression. Hosted application remains unverified.
