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
| REQ-SH-018 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | AiAccessGuard; SmartReportService/Controller persisted authorization, review UI and SoClaasTransport | guard/gateway/HTTP/PostgreSQL/DOM tests; steps 5/6 logs | Requester locally implemented; live smoke deferred; other roles/browser/hosted pending |
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
| REQ-RQ-012 | [Smart Report Assistant](specs/ROLE-REQUESTER.md#smart-report-assistant) | SmartReportService/POST controller and create/owned OPEN edit review panel | RequesterRequestIntegrationTest role/owner/state/account/session/in-flight denial and rendered panel; steps 5/6 logs | implemented locally; browser/hosted/review pending; live smoke deferred |
| REQ-RQ-013 | [Smart Report Assistant](specs/ROLE-REQUESTER.md#smart-report-assistant) | typed context with current input and active category labels only; fixed schema/rules and two-message transport | SmartReportGatewayTest, SoClaasTransportTest payload; RequesterRequestIntegrationTest foreign/ID exclusions | locally verified with local providers; live smoke deferred |
| REQ-RQ-014 | [Smart Report Assistant](specs/ROLE-REQUESTER.md#smart-report-assistant) | SmartReportSchema strict typed parser after provider-envelope validation | gateway schema/Unicode/types tests and SoClaasTransportTest malformed/truncated/tool/overflow tests | locally verified; live smoke deferred |
| REQ-RQ-015 | [Smart Report Assistant](specs/ROLE-REQUESTER.md#smart-report-assistant) | create.html labelled separate review; smart-report.js explicit individual acceptance/edit/discard | src/test/js/smart-report.test.cjs and RequesterRequestIntegrationTest rendered create/edit; step 6 log | implemented locally; real-browser/accessibility/review pending |
| REQ-RQ-016 | [Smart Report Assistant](specs/ROLE-REQUESTER.md#smart-report-assistant) | ordinary create/edit submission only; advisory result cannot write or authorize | PostgreSQL zero-write checks and review-to-ordinary-submit/stale AI-flag denial; DOM no-auto-submit checks | locally verified; browser/full cross-role integration pending |
| REQ-RQ-017 | [Smart Report Assistant](specs/ROLE-REQUESTER.md#smart-report-assistant) | safe categorized failure replies and DOM manual input preservation; unavailable on missing AI config | gateway/HTTP failure tests; PostgreSQL no writes; DOM failures, discard, stale/late input and no-script markup | locally verified; browser/hosted pending; live smoke deferred |
| REQ-TECH-001–011 | [Technician manual workflow](specs/ROLE-TECHNICIAN.md#manual-workflow-requirements) | pending | TECH-A01–A06 pending | approved |
| REQ-TECH-012–017 | [Work Plan Assistant](specs/ROLE-TECHNICIAN.md#work-plan-assistant) | pending | TECH-AI01–AI03 pending | approved |
| REQ-MGR-001–012 | [Manager request/review](specs/ROLE-MANAGER.md#request-and-review-requirements) | pending | MGR-A01–A05 pending | approved |
| REQ-MGR-013–015 | [Manager accounts](specs/ROLE-MANAGER.md#account-administration) | pending | MGR-A06 pending | approved |
| REQ-MGR-016–019 | [Category and skill administration](specs/ROLE-MANAGER.md#category-and-skill-administration) | pending | MGR-A07 pending | approved |
| REQ-MGR-020–025 | [Triage Assistant and matching](specs/ROLE-MANAGER.md#triage-assistant-and-deterministic-candidate-ranking) | pending | MGR-A08–A10 pending | approved |
| REQ-AI-001 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | SoClaasTransport approved HTTPS origin and server Bearer header | SoClaasTransportTest URL/missing config/header/redirect checks | implemented; local HTTP contract verified; live smoke deferred |
| REQ-AI-002 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | fixed system and one serialized user message; non-streaming POST with no tools/history/response ID | gateway context checks and exact HTTP payload test | locally verified; live smoke deferred |
| REQ-AI-003 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | configured SOCLAAS_MODEL checked by bounded first-use GET /models with cache/backoff | local provider supported/missing/malformed/failed discovery/no startup I/O tests | implemented locally; actual model readiness unverified |
| REQ-AI-004 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | server-only base URL/key/model plus bounded AI enable/deadline/output/rate/concurrency settings | gateway/PostgreSQL limits and HTTP URL/config tests | locally implemented; separate environment keys/hosted checks pending |
| REQ-AI-005 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | AiGateway typed Call/Result and safe failures; Smart Report only | gateway, HTTP and PostgreSQL tests | locally implemented for Smart Report; other features pending |
| REQ-AI-006 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | production SoClaasTransport and test-only FakeAiTransport/loopback HTTP server | success/malformed/delay/status/network/deadline/local HTTP contracts | implemented locally; external smoke deferred |
| REQ-AI-007 | [Usage and audit metadata](specs/INTEGRATION-SPEC.md#usage-and-audit-metadata) | JdbcAiOperations metadata with provider counts and null missing usage | gateway usage/retry tests; PostgreSQL metadata/freshness assertions | Smart Report locally verified; live usage pending |
| REQ-AI-008 | [Usage and audit metadata](specs/INTEGRATION-SPEC.md#usage-and-audit-metadata) | V3 append-only metadata and restricted projection; no prompt/output columns | PostgreSQL migration grants and invocation metadata assertions | partial; Manager aggregate view pending |
| REQ-AI-009 | [Usage and audit metadata](specs/INTEGRATION-SPEC.md#usage-and-audit-metadata) | pending | portal reconciliation pending | approved |
| REQ-AI-010 | [Usage and audit metadata](specs/INTEGRATION-SPEC.md#usage-and-audit-metadata) | invocation telemetry separate from ordinary audit; suggestion has no write authority | RequesterRequestIntegrationTest zero business events/writes | Smart Report locally verified; other roles pending |
| AI-SEC-001 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | persisted Smart Report Account/role/owner/OPEN authorization | PostgreSQL denial tests and AiAccessGuardTest | Requester service locally verified; other assistants pending |
| AI-SEC-002 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | authorization and admission precede context/provider | no-category/no-provider denial/limit spy assertions | Requester service locally verified; other assistants pending |
| AI-SEC-003 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | versioned fixed system prompt; provenance-labelled serialized data object | gateway direct/encoded/multilingual/delimiter payload test | locally verified at fake seam; live evaluation pending |
| AI-SEC-004 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | allowlisted current input and category labels only | gateway/context and PostgreSQL foreign/identifier exclusions | Requester service locally verified; other assistants pending |
| AI-SEC-005 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | transport sends exactly model/stream/max_tokens/system-user messages; no tools or mutation seam | exact HTTP payload and zero business-write tests | locally verified; live smoke deferred |
| AI-SEC-006 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | stateless per-call context/two-message HTTP payload; no raw content persistence | exact HTTP payload, metadata schema and PostgreSQL assertions | Smart Report locally verified; live smoke deferred |
| AI-SEC-007 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | field maxima and conservative 12000-unit serialized system/data bound | gateway input/aggregate limit tests | Smart Report locally verified |
| AI-SEC-008 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | deadline/token caps, bounded workers, HTTP cancellation/body cap and PostgreSQL rolling quota/slot leases | gateway/HTTP deadline/no-queue and PostgreSQL minute/hour/global/concurrent tests | Smart Report locally verified; actual provider smoke deferred |
| AI-SEC-009 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | one retry for network/503 or short deadline-fitting 429; no retries for invalid output/readiness/config failures | gateway and local HTTP mapping/retry/deadline/overflow tests | locally verified; actual provider smoke deferred |
| AI-SEC-010 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | SmartReportSchema strict JSON and semantic constraints | gateway malformed/duplicates/trailing/type/boundary tests | Smart Report locally verified; other schemas pending |
| AI-SEC-011 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | plain-text rejection, encoded JSON and DOM textContent-only review | gateway malicious output and DOM malicious field/question/rationale checks | locally verified; real-browser checks pending |
| AI-SEC-012 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | pending | Technician safety detector/evaluation pending | approved |
| AI-SEC-013 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | labelled review with explicit individual acceptance/edit/discard; no mutation calls | DOM acceptance/discard/no-submit and PostgreSQL zero-write assertions | Smart Report locally verified; browser pending |
| AI-SEC-014 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | accepted values use ordinary create/edit service and current state/version/category checks | PostgreSQL review-to-submit and stale assignment denial; mutation regressions | locally verified; browser/full cross-role pending |
| AI-SEC-015 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | immutable assistant inputs; server actor/context; AI flags never authorize submission | PostgreSQL actor/accepted/status/priority overposting and stale AI-flag denial | Smart Report locally verified |
| AI-SEC-016 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | safe failure replies and manual fields remain usable during/following AI failure | gateway/HTTP failures, PostgreSQL no writes and DOM input/error/late/discard checks | locally verified; browser/hosted pending |
| AI-SEC-017 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | JdbcAiOperations allowlisted metadata only | PostgreSQL exact columns/values, no input/output and no business events | Smart Report locally verified; live usage pending |
| AI-SEC-018 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | V3 append-only invocation storage; absent usage null | migration grants, gateway absent usage and PostgreSQL metadata | locally verified; live usage pending |
| AI-SEC-019 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | server-only SoCLaaS config, HTTPS origin restriction/no redirects, known-secret output and model-misconfiguration rejection | HTTP URL/header/redirect/null-secret-model tests and gateway secret rejection; step 6 fresh verification | locally implemented; separate hosted keys/live smoke pending |
| AI-SEC-020 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | bounded first-use model discovery; no startup I/O; success cache/failure cooldown | HTTP discovery/missing/malformed/error/no-startup-I/O tests | locally implemented; actual provider model check deferred |
| AI-SEC-021 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | PostgreSQL atomic rolling admission and versioned expiring global slots | PostgreSQL quota/concurrency/stale lease release tests | locally verified; deployment concurrency checks pending |
| AI-SEC-022 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | source-controlled smart-report-v1 prompt/schema | gateway prompt/schema tests | locally implemented; human PR review pending |
| AI-SEC-023–027 | [Human/process gates](specs/AI-SECURITY-SPEC.md#human-and-development-process-gates) | pending | AIS-T15 and review evidence pending | approved |
| AI-SEC-028 | [Response freshness and leakage controls](specs/AI-SECURITY-SPEC.md#response-freshness-and-leakage-controls) | server post-response access/version/category checks plus browser changed-input/late-result discard | PostgreSQL revocation/assignment/catalogue and DOM stale/late/cancel cases | Requester locally verified; other roles/browser pending |
| AI-SEC-029 | [Response freshness and leakage controls](specs/AI-SECURITY-SPEC.md#response-freshness-and-leakage-controls) | same-origin POST with CSRF, persisted scope/input/admission before catalogue/context/model probe/provider | HTTP CSRF/role/scope, no-context and DOM payload checks | Requester locally verified; browser pending |
| AI-SEC-030 | [Response freshness and leakage controls](specs/AI-SECURITY-SPEC.md#response-freshness-and-leakage-controls) | fail-closed schema/disclosure markers, known secret/identifier checks and adapter canaries | gateway system-only/unauthorized canary, secret and identifier rejection | fake seam locally verified; live/semantic leakage evaluation pending |

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
