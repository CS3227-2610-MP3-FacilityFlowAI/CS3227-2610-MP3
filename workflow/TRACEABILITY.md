# Requirement Traceability

Status: approved specification baseline v0.1, 12 October 2026. The gateway
revision candidate v0.2 received stakeholder approval on 16 October 2026;
affected role-owner, Security Reviewer, teammate and fresh-reviewer review
remains pending. Iteration 2A provider-core/readiness decisions received
stakeholder direction and approval on 16 October 2026; specification Security
Reviewer and teammate review remains pending. No revision is approved or
verified by passing tests alone. The earlier implementation Security Reviewer
NO-GO covered then-open lock-safety, read-only-handle, package-visibility and
decoder-diagnostic findings; later remediation commits and tests provide local
evidence for those fixes, while the required human reviews remain pending.
Iteration 2B HTTPS-adapter requirements are based on provider-core commit
`897937d`; adapter implementation, focused tests and fresh Security Reviewer
approval are complete. Iteration 2C lifecycle implementation and focused tests
are complete; fresh Security Reviewer GO; teammate review and hosted release
verification remain pending. Iteration 2D Technician Work Plan implementation,
focused parser/authorization/freshness tests and full-suite verification are
complete. Iteration 2E adds process-local shared admission, rolling-window and
permit-release tests. Iteration 2F adds PostgreSQL-time Account buckets and
owner/version-fenced global leases with isolated migration/admission tests;
durable telemetry, bounded retry, strict feature contracts and disclosure checks
are implemented in later increments. Compressed responses were reverted to the
identity-only contract. Fresh Security Reviewer, teammate, role-owner and
fresh-verifier review remain pending.

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
| REQ-RQ-012–017 | [Smart Report Assistant](specs/ROLE-REQUESTER.md#smart-report-assistant) | typed Smart Report provider seam with approved urgency vocabulary and non-empty suggestion invariant; role orchestration pending | `SocLlmSmartReportAssistantTest` invalid urgency/empty-result cases; RQ-AI01–AI04 orchestration cases pending | provider contract locally verified; v0.2 role-owner/teammate review pending |
| REQ-TECH-001–011 | [Technician manual workflow](specs/ROLE-TECHNICIAN.md#manual-workflow-requirements) | pending | TECH-A01–A06 pending | approved |
| REQ-TECH-012–017 | [Work Plan Assistant](specs/ROLE-TECHNICIAN.md#work-plan-assistant) | `WorkPlanAssistant`, `TechnicianWorkPlanService`, `AiInvocationCoordinator`, `JdbcAiAdmissionBackend`, bounded JDBC context/freshness repository, typed Work Plan decoder, disclosure policy and POST `/technician/requests/{displayId}/work-plan` seam | `SocLlmWorkPlanAssistantTest`, `TechnicianWorkPlanServiceTest`, `AiInvocationCoordinatorTest`, `DatabaseMigrationTest`, `WorkPlanAuthorizationBoundaryTest`, focused Gradle suite | Technician slice and gateway controls locally verified; AI-SEC-028 current-session revalidation now covered by captured identity/session snapshot and persisted recheck; approved safety corpus and role-owner/teammate/security review remain pending |
| REQ-MGR-001–012 | [Manager request/review](specs/ROLE-MANAGER.md#request-and-review-requirements) | pending | MGR-A01–A05 pending | approved |
| REQ-MGR-013–015 | [Manager accounts](specs/ROLE-MANAGER.md#account-administration) | pending | MGR-A06 pending | approved |
| REQ-MGR-016–019 | [Category and skill administration](specs/ROLE-MANAGER.md#category-and-skill-administration) | pending | MGR-A07 pending | approved |
| REQ-MGR-020–025 | [Triage Assistant and matching](specs/ROLE-MANAGER.md#triage-assistant-and-deterministic-candidate-ranking) | typed Triage provider seam with closed priority vocabulary, unique/disjoint skill tags and mutation-free deterministic ranker; role orchestration pending | `SocLlmTriageAssistantTest`; `TriageCandidateRankerTest` duplicate-tag rejection and deterministic ordering; MGR-A08–A11 orchestration cases pending | provider/ranker contract locally verified; v0.2 role-owner/teammate review pending |
| REQ-AI-001, REQ-AI-003–004 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | `.env.example`, `application.yml`, `SocLlmProperties`, `AiConfiguration`, `AiConfigurationValidator`, `AiSettingsConfiguration`, `AiProviderLifecycle`, `AiProviderLifecycleConfiguration`, `SocLlmHttpsProviderAdapter`, `ProviderHttpTransport`, `ProviderCredentialCapability`, strict model-envelope compatibility metadata | existing configuration evidence; `SocLlmHttpsProviderAdapterTest`; `AiProviderLifecycleConfigurationTest`; `ProviderEnvelopeDecoderTest`; `SocLlmProviderRuntimeFactoryTest` including `qwen3.8:27b` readiness | configuration, adapter/lifecycle and current SoCLaaS model metadata locally verified; teammate/release review pending |
| REQ-AI-002, REQ-AI-005–006 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | `SocLlmProviderCore`, `ProviderFailure`, `SocLlmHttpsProviderAdapter`, `SocLlmProviderRuntimeFactory`, non-reasoning structured completion requests, current inert SoCLaaS/vLLM completion metadata, public `WorkPlanAssistant` seam and strict Work Plan decoder | provider-core tests including separate executor ownership; adapter/lifecycle readiness tests; `ProviderEnvelopeDecoderTest`; `SocLlmWorkPlanAssistantTest`; full Gradle suite | 2A/2B/2C/2D implementation and current completion compatibility locally verified; fresh 2D security and teammate/release review pending |
| REQ-AI-007–010 | [Usage and audit metadata](specs/INTEGRATION-SPEC.md#usage-and-audit-metadata) | `AiInvocationTelemetry`, JDBC STARTED/terminal persistence, safe redaction, provider usage/retry metadata and failure withholding | `JdbcAiInvocationTelemetryTest`, coordinator/provider retry tests, `TechnicianWorkPlanServiceTest`, full Gradle suite | implementation locally verified; reviewed schema revision, role-owner/Security Reviewer/teammate verification pending |
| AI-SEC-001–007 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | authorization-first `AiAccessGuard`, Technician Work Plan role/service seam, shared coordinator and PostgreSQL-backed Account/global admission | `AiAccessGuardTest`, `WorkPlanAuthorizationBoundaryTest`, `TechnicianWorkPlanServiceTest`, `AiInvocationCoordinatorTest`, `DatabaseMigrationTest`; broader AIS-T01–T08 pending | partial; 2D–2F role/admission slices locally verified; telemetry and review pending |
| AI-SEC-008 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | configuration hard ceilings plus process-local fallback and PostgreSQL-time Account/global admission; Technician use-case deadline created once at entry and propagated through readiness, context, provider and Delegating feature seams | configuration boundary tests, `AiInvocationCoordinatorTest`, `TechnicianWorkPlanServiceTest` deadline propagation, `AiFeatureContractTest` delegate forwarding, `DatabaseMigrationTest` and full check | deadline propagation locally verified; Requester/Manager orchestration and reviewer verification pending |
| AI-SEC-009–010 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | bounded decoder plus identity-only Iteration 2B status/hand-off; retry execution remains separately implemented and compressed responses deferred | provider-core tests plus `SocLlmHttpsProviderAdapterTest` identity-encoding/body-closure and bounded-output cases; retry tests; compressed-wire tests intentionally absent per spec | identity-only transport locally verified; compressed-response scope deferred; reviewer verification pending |
| AI-SEC-011–018 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | Work Plan prompt template/version with exact result schema, allowlisted context projection, versioned multilingual/obfuscation-aware/negation-aware `WorkPlanSafetyPolicy`, strict typed/safety decoder, shared telemetry/retry behavior and fail-closed disclosure policy | `SocLlmWorkPlanAssistantTest`, `WorkPlanSafetyPolicyTest`, telemetry/retry tests and provider-package suite; broader AIS-T09–T14 corpus review pending | deterministic safety-policy implementation locally verified; human-reviewed corpus and final review remain pending |
| AI-SEC-019 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | server-side raw binding, official-origin validation, placeholder/control rejection, redacted diagnostics, opaque credential capability, HTTPS adapter and startup-safe lifecycle wiring | existing configuration tests; provider-core tests; adapter credential/TLS/redirect/transport cases; lifecycle disabled/unavailable/available readiness cases | Iteration 2B/2C implementation locally verified; fresh Security Reviewer GO; teammate/release review pending |
| AI-SEC-020 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | startup-safe disabled/unavailable/available configuration states; generation-aware readiness lifecycle with negative-cache recovery, separate discovery/provider executors, idempotent close and inert current SoCLaaS model metadata | `ProviderEnvelopeDecoderTest`; `SocLlmProviderRuntimeFactoryTest` including `qwen3.8:27b`, post-negative-cache recovery and concurrent joiners; provider-core executor ownership and close-race tests | readiness recovery/model metadata compatibility locally verified; fresh Security Reviewer, teammate and release review pending |
| AI-SEC-021–022 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | V3/V5 rolling-window storage, PostgreSQL-time Account admission and owner/version-fenced global leases; prompt templates remain feature-owned | `DatabaseMigrationTest` durable admission/stale-release cases and AIS-T10 pending | partial; distributed admission locally verified; telemetry/retry/prompt review pending |
| AI-SEC-023–027 | [Human/process gates](specs/AI-SECURITY-SPEC.md#human-and-development-process-gates) | pending | AIS-T15 and review evidence pending | approved |
| AI-SEC-028–030 | [Response freshness and leakage controls](specs/AI-SECURITY-SPEC.md#response-freshness-and-leakage-controls) | Technician persisted assignment/status/version freshness query, output identifier/markup/control rejection, server-assembled normalized deny material and `Cache-Control: no-store` response | role-loss race, stale-eligibility, malformed/unsafe/disclosure-canary output tests; current-session principal revalidation, controller/header integration and broader AIS-T16–T18 pending | Work Plan disclosure subset locally verified; AI-SEC-028 current-session revalidation remains unproven; Smart Report/Triage provider disclosure tests pass; role-owner, Security Reviewer and teammate review pending |

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
