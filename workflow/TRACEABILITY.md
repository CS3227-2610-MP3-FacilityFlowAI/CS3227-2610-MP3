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
| REQ-SH-004 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 sessions/version, logout/revalidation; PasswordChangeService, JdbcIdentityOperations and PasswordController retain sessions on own-password change | DatabaseMigrationTest atomic password/stale credentials; SecurityIntegrationTest all-role password/session checks; PasswordChangeServiceTest persisted-account denial | partial; Manager account-change use cases and hosted/browser checks pending |
| REQ-SH-005 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 private JDBC sessions and secure browser-scoped cookie configuration | SecurityIntegrationTest cookie/session assertions; browser checks pending | integrated; teammate review pending |
| REQ-SH-006 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 audit storage/grants; password/login events; Requester create/edit/cancel/update events | DatabaseMigrationTest password/audit rollback; SecurityIntegrationTest login failure; RequesterRequestIntegrationTest every mutation audit/rollback | partial; other role event services pending |
| REQ-SH-007 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 Account/audit delete denied; remaining entities/UI pending | DatabaseMigrationTest runtime permissions; Part 2b log | partial |
| REQ-SH-008 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | Requester own list/recent order, visible filters/reset and distinct empty states | RequesterRequestIntegrationTest ties, inactive categories, reset/empty/no matches; step 4 log | Requester implemented locally; other lists and browser checks pending |
| REQ-SH-009 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | RequesterRequests local-time presentation and RequestFilters inclusive Singapore day bounds over UTC persistence | RequestFiltersTest and RequesterRequestIntegrationTest local zone/midnight boundaries; step 4 log | Requester implemented locally; other roles and browser checks pending |
| REQ-SH-010 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | RequestDraft and RequestFilters validation, retained Requester forms, V5 request_submission and pending-submit script | RequestDraftTest, RequestFiltersTest, RequesterRequestIntegrationTest, RequesterRequestControllerTest, src/test/js/request-form.test.cjs | partial; other role forms and browser checks pending |
| REQ-SH-011 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | Requester cancellation page defaults to Keep request and requires reason/explicit confirmation in service | RequesterRequestIntegrationTest cancellation decision/confirmation/reason checks | partial; other role destructive actions pending |
| REQ-SH-012 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | AuthenticatedPageAdvice, shared header, About/password pages and Requester dashboard/manual navigation | SecurityIntegrationTest and RequesterRequestIntegrationTest role/page checks | partial; remaining role navigation and browser checks pending |
| REQ-SH-013 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | JdbcRequesterRequestRepository owner-scoped history projection | RequesterRequestIntegrationTest excludes Work Logs, private Manager events and structured details at result | partial; Technician/Manager history views pending |
| REQ-SH-014 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | TransactionTemplate password and all Requester write/audit operations | DatabaseMigrationTest and RequesterRequestIntegrationTest audit rollback/retry/concurrent mutations | partial; other role business use cases pending |
| REQ-SH-015 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | DatabaseInitializer explicit demo mode | DatabaseInitializerTest seed/rollback/idempotence; Part 3 log | locally verified; hosted pending |
| REQ-SH-016 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | DatabaseInitializer default Manager bootstrap | DatabaseInitializerTest bootstrap/concurrency/profile/no overwrite; Part 3 log | locally verified; hosted pending |
| REQ-SH-017 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | bootstrap/demo exceptions supplied; ordinary account authorization pending | initialization tests only | partial |
| REQ-SH-018 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | AiAccessGuard authorization-before-context seam | AiAccessGuardTest; assistant endpoints pending | partial |
| NFR-SEC-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | security evidence pending | approved |
| NFR-PERF-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | 50-user performance result pending | approved |
| NFR-A11Y-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | Chrome/accessibility checks pending | approved |
| NFR-REL-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | fallback/restore evidence pending | approved |
| NFR-OPS-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | monitoring/log evidence pending | approved |
| NFR-DEP-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | deployment separation pending | approved |
| NFR-DOC-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | documentation/reuse/process review pending | approved |
| REQ-RQ-001 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | RequesterRequestService dashboard; owner-scoped six-state counts, five recent reports and dashboard template | RequesterRequestIntegrationTest own/foreign/on-behalf reads, zero counts/recent ordering; step 4 log | implemented; locally verified; hosted/browser/review pending |
| REQ-RQ-002 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | RequestDraft, RequesterRequestService/Repository, create controller/template, V5 submission records | RequestDraftTest and RequesterRequestIntegrationTest create, overposting, rollback, concurrent replay and display-ID exhaustion (RQ-A01/02/06) | implemented; local evidence in step 2 log; hosted/browser/review pending |
| REQ-RQ-003 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | RequestFilters; parameterized literal search and combined status/category/local date SQL; retained GET filter form | RequestFiltersTest and RequesterRequestIntegrationTest literal search, AND filters, inclusive date boundaries, inactive categories and invalid filters; step 4 log | implemented; locally verified; hosted/browser/review pending |
| REQ-RQ-004 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | JdbcRequesterRequestRepository shared list/recent ordering | RequesterRequestIntegrationTest creation-time ties and five recent order | implemented; local evidence in steps 2/4 logs; review pending |
| REQ-RQ-005 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | RequesterRequests.Detail and requester/detail.html | RequesterRequestIntegrationTest detail fields, local times and permitted history | implemented; local evidence in step 2 log; hosted/browser/review pending |
| REQ-RQ-006 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | RequesterRequestService.edit; owner/state/version-locked SQL; reused create/edit form | RequesterRequestIntegrationTest owned OPEN edit, stale assignment/category, replay/concurrent edits and rollback (RQ-A04/06/07) | implemented; local evidence in step 3 log; hosted/browser/review pending |
| REQ-RQ-007 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | RequesterRequestService.cancel and explicit cancellation form; audited public reason | RequesterRequestIntegrationTest confirmation/default decision, terminal rejection, races and rollback (RQ-A05/06) | implemented; local evidence in step 3 log; hosted/browser/review pending |
| REQ-RQ-008 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | RequesterRequestService.addUpdate; non-terminal state/version guard; atomic update/version/audit | RequesterRequestIntegrationTest all six states, replay, author derivation, races and rollback; RequestDraftTest Unicode note bounds | implemented; local evidence in step 3 log; hosted/browser/review pending |
| REQ-RQ-009 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | Owner predicates on counts/search/list/detail and allowlisted history SQL projection; safe role view models | RequesterRequestIntegrationTest foreign/nonexistent parity, forged owner/search exclusion and exclusion of private data before rendering (RQ-A03/07) | implemented; local evidence in steps 2/4 logs; review pending |
| REQ-RQ-010 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | Immutable report inputs; bounded edit/cancel/update operations; fixed cancellation transition and role routes | RequesterRequestIntegrationTest overposting, foreign objects, wrong-role HTTP and direct-service denials | Requester boundary locally implemented; full cross-role lifecycle integration pending |
| REQ-RQ-011 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | Transactional mutations, expected-version conflicts, retained forms and centralized safe failure guidance | RequesterRequestIntegrationTest all mutation rollback/retry, stale/repeated/concurrent requests; RequesterRequestControllerTest late denial | implemented; local evidence in step 3 log; hosted/browser/review pending |
| REQ-RQ-012–017 | [Smart Report Assistant](specs/ROLE-REQUESTER.md#smart-report-assistant) | pending | RQ-AI01–AI03 pending | approved |
| REQ-TECH-001–011 | [Technician manual workflow](specs/ROLE-TECHNICIAN.md#manual-workflow-requirements) | pending | TECH-A01–A06 pending | approved |
| REQ-TECH-012–017 | [Work Plan Assistant](specs/ROLE-TECHNICIAN.md#work-plan-assistant) | pending | TECH-AI01–AI03 pending | approved |
| REQ-MGR-001–012 | [Manager request/review](specs/ROLE-MANAGER.md#request-and-review-requirements) | pending | MGR-A01–A05 pending | approved |
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
