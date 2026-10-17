# Requirement Traceability

Status: approved specification baseline v0.1, 12 October 2026. All listed
requirements are approved; none is implemented or verified by approval alone.

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
| REQ-SH-006 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 audit storage/grants; Manager on-behalf Request event; remaining event-producing services pending | DatabaseMigrationTest runtime permissions; ManagerRepositoryIntegrationTest actor/target/action assertion | partial; teammate review pending |
| REQ-SH-007 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 Account/audit delete denied; remaining entities/UI pending | DatabaseMigrationTest runtime permissions; Part 2b log | partial |
| REQ-SH-008–010 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | Manager deterministic queue/filters/date bounds; retained on-behalf form and pending-submit protection | ManagerInputTest and ManagerRepositoryIntegrationTest; browser checks pending | Manager first slice locally verified; other roles/forms pending |
| REQ-SH-011–013 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | pending | product acceptance pending | approved |
| REQ-SH-014 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | Manager on-behalf Request and audit share one service transaction; other business operations pending | DatabaseMigrationTest rollback; ManagerRepositoryIntegrationTest injected audit failure rollback | partial; teammate review pending |
| REQ-SH-015 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | DatabaseInitializer explicit demo mode | DatabaseInitializerTest seed/rollback/idempotence; Part 3 log | locally verified; hosted pending |
| REQ-SH-016 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | DatabaseInitializer default Manager bootstrap | DatabaseInitializerTest bootstrap/concurrency/profile/no overwrite; Part 3 log | locally verified; hosted pending |
| REQ-SH-017 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | bootstrap/demo exceptions supplied; ordinary account authorization pending | initialization tests only | partial |
| REQ-SH-018 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | AiAccessGuard authorization-before-context seam | AiAccessGuardTest; assistant endpoints pending | partial |
| NFR-SEC-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | security evidence pending | approved |
| NFR-PERF-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | 50-user performance result pending | approved |
| NFR-A11Y-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | Chrome/accessibility checks pending | approved |
| NFR-REL-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | fallback/restore evidence pending | approved |
| NFR-OPS-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | monitoring/log evidence pending | approved |
| NFR-DEP-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pull-request/master CI runs clean check, JAR packaging and optional JavaScript checks; Preview/Production deployment still pending | `.github/workflows/ci.yml`; local equivalent build evidence in Manager session log | partial; GitHub execution and deployment separation pending |
| NFR-DOC-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | Manager first-slice guide, traceability, reuse and session/handoff records | affected documents on `feat/manager-workflow` | partial; teammate review pending |
| REQ-RQ-001–011 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | pending | RQ-A01–A07 pending | approved |
| REQ-RQ-012–017 | [Smart Report Assistant](specs/ROLE-REQUESTER.md#smart-report-assistant) | pending | RQ-AI01–AI03 pending | approved |
| REQ-TECH-001–011 | [Technician manual workflow](specs/ROLE-TECHNICIAN.md#manual-workflow-requirements) | pending | TECH-A01–A06 pending | approved |
| REQ-TECH-012–017 | [Work Plan Assistant](specs/ROLE-TECHNICIAN.md#work-plan-assistant) | pending | TECH-AI01–AI03 pending | approved |
| REQ-MGR-001–003 | [Manager request/review](specs/ROLE-MANAGER.md#request-and-review-requirements) | Manager queue/search/filter/order; active Requester on-behalf creation with Recording Actor | ManagerInputTest; ManagerRepositoryIntegrationTest ordering/literal search/create/inactive/rollback | first slice locally verified; details, browser and teammate review pending |
| REQ-MGR-004–009 | [Manager request/review](specs/ROLE-MANAGER.md#request-and-review-requirements) | pending | MGR-A01–A05 pending | approved |
| REQ-MGR-010–011 | [Manager request/review](specs/ROLE-MANAGER.md#request-and-review-requirements) | status/priority counts, active Technician workload; read-only Request/actor/action/date audit filters | ManagerRepositoryIntegrationTest workload, action-filter and missing-association regressions | first slice locally verified; browser and teammate review pending |
| REQ-MGR-012 | [Manager request/review](specs/ROLE-MANAGER.md#request-and-review-requirements) | pending with lifecycle operations | MGR-A01–A05 pending | approved |
| REQ-MGR-013–015 | [Manager accounts](specs/ROLE-MANAGER.md#account-administration) | pending | MGR-A06 pending | approved |
| REQ-MGR-016–019 | [Category and skill administration](specs/ROLE-MANAGER.md#category-and-skill-administration) | pending | MGR-A07 pending | approved |
| REQ-MGR-020–025 | [Triage Assistant and matching](specs/ROLE-MANAGER.md#triage-assistant-and-deterministic-candidate-ranking) | pending | MGR-A08–A10 pending | approved |
| REQ-AI-001–006 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | pending | gateway contract tests pending | approved |
| REQ-AI-007–010 | [Usage and audit metadata](specs/INTEGRATION-SPEC.md#usage-and-audit-metadata) | pending | telemetry/redaction tests pending | approved |
| AI-SEC-001–022 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | pending | AIS-T01–T14 pending | approved |
| AI-SEC-023–027 | [Human/process gates](specs/AI-SECURITY-SPEC.md#human-and-development-process-gates) | pending | AIS-T15 and review evidence pending | approved |
| AI-SEC-028–030 | [Response freshness and leakage controls](specs/AI-SECURITY-SPEC.md#response-freshness-and-leakage-controls) | pending | AIS-T16–T18 pending | approved |

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
