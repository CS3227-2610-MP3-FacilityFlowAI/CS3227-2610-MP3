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
| REQ-SH-004 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 sessions/version, logout/revalidation; SharedPagesController, PasswordChangeService and JdbcPasswordChangeRepository retain sessions on own-password change; Manager account-change session-version invalidation | DatabaseMigrationTest atomic password/stale credentials; PasswordChangeSecurityIntegrationTest all-role password/session checks; JdbcPasswordChangeRepositoryTest persisted-account denial; ManagerRepositoryIntegrationTest password-reset version assertion | partial; Requester and Manager local evidence; hosted/browser/teammate review pending |
| REQ-SH-005 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 private JDBC sessions and secure browser-scoped cookie configuration | SecurityIntegrationTest cookie/session assertions; browser checks pending | integrated; teammate review pending |
| REQ-SH-006 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 audit storage/grants; password/login events; Requester create/edit/cancel/update events; Manager lifecycle, account, category and skill audit events; V1 append-only audit storage/grants; atomic Technician start/log/completion and Manager lifecycle/account/category/skill events | DatabaseMigrationTest password/audit rollback; SecurityIntegrationTest login failure; RequesterRequestIntegrationTest every mutation audit/rollback; ManagerRepositoryIntegrationTest action and audit rollback assertions; DatabaseMigrationTest permissions; JdbcTechnicianCommandRepositoryTest and completion tests actor/payload/rollback; ManagerRepositoryIntegrationTest actions and injected audit failures; [T2 report](../logs/technician/T2-REPORT.md), [T3 report](../logs/technician/T3-REPORT.md), [Manager lifecycle report](../logs/2026-10-18-manager-lifecycle-administration.md) | partial; combined role local evidence; human/hosted review pending |
| REQ-SH-007 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | V1 Account/audit delete denied; remaining entities/UI pending; V1/V2 append-only history grants; Technician append-only controls; Manager return/reopen retain Work Logs and completion attempts | DatabaseMigrationTest runtime permissions; Part 2b log; DatabaseMigrationTest runtime UPDATE/DELETE rejection; Technician completion-history tests; ManagerRepositoryIntegrationTest retained history; [T3 report](../logs/technician/T3-REPORT.md) | partial; combined role local evidence; human/hosted review pending |
| REQ-SH-008 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | Requester own list/recent order, visible filters/reset and distinct empty states; Manager deterministic queue and filters; Technician assigned queue and Manager all-request queue with deterministic ordering, literal combined filters, reset and empty states | RequesterRequestIntegrationTest ties, inactive categories, reset/empty/no matches; step 4 log; ManagerInputTest and ManagerRepositoryIntegrationTest; Technician query/PostgreSQL/rendering tests in [T1 report](../logs/technician/T1-REPORT.md); ManagerInputTest, ManagerRepositoryIntegrationTest and ManagerControllerTest | partial; combined role local evidence; human/hosted review pending |
| REQ-SH-009 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | RequesterRequests local-time presentation and RequestFilters inclusive Singapore day bounds over UTC persistence; Manager Singapore date bounds; UTC storage, Singapore Technician history timestamps and Manager inclusive Singapore date filters | RequestFiltersTest and RequesterRequestIntegrationTest local zone/midnight boundaries; step 4 log; ManagerInputTest and ManagerRepositoryIntegrationTest; T1/T2 PostgreSQL/history and MVC local-time checks; ManagerInputTest and ManagerRepositoryIntegrationTest date-boundary tests | partial; combined role local evidence; human/hosted review pending |
| REQ-SH-010 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | RequestDraft and RequestFilters validation, retained Requester forms, V6 request_submission and pending-submit script; Manager retained on-behalf form and pending-submit protection; Versioned Technician start/log/completion and Manager lifecycle forms; retained business drafts, field errors and pending-submit protection; own-password inputs cleared on failure | RequestDraftTest, RequestFiltersTest, RequesterRequestIntegrationTest, RequesterRequestControllerTest, src/test/js/request-form.test.cjs; ManagerControllerTest and ManagerRepositoryIntegrationTest; Technician controller/security/JavaScript checks in [T2 report](../logs/technician/T2-REPORT.md) and [T3 report](../logs/technician/T3-REPORT.md); ManagerControllerTest and ManagerRepositoryIntegrationTest; shared/password evidence in [T5 report](../logs/technician/T5-REPORT.md) | partial; combined role local evidence; human/hosted review pending |
| REQ-SH-011 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | Requester cancellation page defaults to Keep request and requires reason/explicit confirmation in service; pending destructive-action acceptance outside this shared-screen increment | RequesterRequestIntegrationTest cancellation decision/confirmation/reason checks; product acceptance pending | partial; combined role local evidence; human/hosted review pending |
| REQ-SH-012 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | AuthenticatedPageAdvice, shared header, About/password pages and Requester dashboard/manual navigation; T5 shared authenticated role navigation/header, password and About pages | SecurityIntegrationTest and RequesterRequestIntegrationTest role/page checks; Authored shared-page controller/security/Thymeleaf tests and browser checklist; [T5 report](../logs/technician/T5-REPORT.md) | partial; combined role local evidence; human/hosted review pending |
| REQ-SH-013 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | JdbcRequesterRequestRepository owner-scoped history projection; Technician assigned-only Requester Update/Work Log reads and Manager detail/history reads with retained original authorship | RequesterRequestIntegrationTest excludes Work Logs, private Manager events and structured details at result; T1 history/reassignment tests in [T1 report](../logs/technician/T1-REPORT.md); ManagerRepositoryIntegrationTest detail/history retention | partial; combined role local evidence; human/hosted review pending |
| REQ-SH-014 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | TransactionTemplate password and all Requester write/audit operations; Manager lifecycle/category/account/skill transactional writes and audits; Locked Technician state/version/log/completion-history writes and Manager lifecycle/category/account/skill writes commit with audits in service transactions | DatabaseMigrationTest and RequesterRequestIntegrationTest audit rollback/retry/concurrent mutations; ManagerRepositoryIntegrationTest injected audit rollback assertions; Technician PostgreSQL isolation/stale/concurrent/audit-history-fault rollback in [T2 report](../logs/technician/T2-REPORT.md) and [T3 report](../logs/technician/T3-REPORT.md); ManagerRepositoryIntegrationTest creation/assignment/category audit-failure rollback | partial; combined role local evidence; human/hosted review pending |
| REQ-SH-015 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | DatabaseInitializer explicit demo mode | DatabaseInitializerTest seed/rollback/idempotence; Part 3 log | partial; combined role local evidence; human/hosted review pending |
| REQ-SH-016 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | DatabaseInitializer default Manager bootstrap | DatabaseInitializerTest bootstrap/concurrency/profile/no overwrite; Part 3 log | partial; combined role local evidence; human/hosted review pending |
| REQ-SH-017 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | bootstrap/demo exceptions supplied; ordinary account authorization pending | initialization tests only | partial; combined role local evidence; human/hosted review pending |
| REQ-SH-018 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | AiAccessGuard; SmartReportService/Controller persisted authorization, review UI and SoClaasTransport; AiAccessGuard authorization-before-context seam | guard/gateway/HTTP/PostgreSQL/DOM tests; steps 5/6 logs; AiAccessGuardTest; assistant endpoints pending | partial; combined role local evidence; human/hosted review pending |
| NFR-SEC-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending; Existing server authentication/roles/CSRF/CSP plus Technician persisted object authorization, bound SQL, safe errors and escaped forms/history | web/application security evidence pending; Technician service/repository/controller/real MVC security tests; [T2 report](../logs/technician/T2-REPORT.md), 585-test full check passed | partial; combined role local evidence; human/hosted review pending |
| NFR-SEC-002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | secret-free defaults plus redacted AI configuration with package-limited raw properties and API-key access | AiConfigurationValidatorTest, AiSettingsConfigurationContextTest and AiConfigurationSecurityRegressionTest; gateway configuration log | partial; configuration locally verified; repository/deployment secret separation pending |
| NFR-PERF-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | opt-in requesterVerification with 50 distinct authenticated users, 400 non-AI GETs | 19 Oct merged rerun: local p95 567.09 ms, pool 2, zero errors; logs/2026-10-19-requester-technician-master-merge.md; historical runs retained | latest local gate PASS; server-only/deployed evidence pending |
| NFR-A11Y-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements), [MP3 UI spec](specs/user-interface.md) | shared workspace palette, focus and hidden-state rules; existing role labels and errors preserved | scoped security/controller and Node regressions; rendered Chrome palette/label/focus evidence in [UI refresh log](../logs/2026-10-19-responsive-ui.md) | local checks recorded in UI log; full accessibility and teammate review pending |
| NFR-A11Y-002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements), [MP3 adaptive presentation](specs/user-interface.md#adaptive-presentation) | workspace.css on all full pages; wrapping navigation, filter grids, stacked forms and table scroll containers | four-width Chrome layout/input-preservation checks in requesterVerification; [UI refresh log](../logs/2026-10-19-responsive-ui.md) | browser result tracked in UI log; hosted/mobile acceptance not claimed |
| NFR-REL-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | Smart Report safe manual fallback; transactional JDBC storage | gateway/HTTP/DOM fallback and graceful app/session restart regression; step 7 log | Requester local evidence; backup/restore/hosted pending |
| NFR-OPS-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | monitoring/log evidence pending | approved |
| NFR-DEP-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pull-request/master CI runs clean check, JAR packaging and optional JavaScript checks; Preview/Production deployment still pending | `.github/workflows/ci.yml`; local equivalent build evidence in Manager session log | partial; GitHub execution and deployment separation pending |
| NFR-DOC-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | Requester and Manager guides, traceability, reuse and session/handoff records; Combined Technician/shared-screen and Manager guides, traceability, reuse and session/handoff records | merged documentation and role verification logs; Technician T1-T5 reports; Manager foundation/triage/lifecycle reports; merged guides and requirement rows | partial; combined role local evidence; human/hosted review pending |
| REQ-RQ-001 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | RequesterRequestService dashboard; owner-scoped six-state counts, five recent reports and dashboard template | RequesterRequestIntegrationTest own/foreign/on-behalf reads, zero counts/recent ordering; step 4 log | implemented; locally verified; hosted/browser/review pending |
| REQ-RQ-002 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | RequestDraft, RequesterRequestService/Repository, create controller/template, V6 submission records | RequestDraftTest and RequesterRequestIntegrationTest create, overposting, rollback, concurrent replay and display-ID exhaustion (RQ-A01/02/06) | implemented; local evidence in step 2 log; hosted/browser/review pending |
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
| REQ-TECH-012–017 | [Work Plan Assistant](specs/ROLE-TECHNICIAN.md#work-plan-assistant) | `WorkPlanAssistant`, `TechnicianWorkPlanService`, `AiInvocationCoordinator`, `JdbcAiAdmissionBackend`, bounded JDBC context/freshness repository, typed Work Plan decoder, disclosure policy and POST `/technician/requests/{displayId}/work-plan` seam; Existing typed/safe provider and no-store POST endpoint; T4 read-only detail panel, plain-text client rendering and lifecycle discard; snapshot-required JDBC initial/final authorization with exact session/status/version and active category | `SocLlmWorkPlanAssistantTest`, `TechnicianWorkPlanServiceTest`, `AiInvocationCoordinatorTest`, `DatabaseMigrationTest`, `WorkPlanAuthorizationBoundaryTest`, focused Gradle suite; TechnicianWorkPlanServiceTest, JdbcTechnicianWorkPlanRepositoryTest, authenticated SecurityIntegrationTest, actual-script DOM/fetch tests and existing provider safety/injection/disclosure tests; [T4 report](../logs/technician/T4-REPORT.md) | partial; combined role local evidence; human/hosted review pending |
| REQ-TECH-001–003 | [Technician manual workflow](specs/ROLE-TECHNICIAN.md#manual-workflow-requirements) | TechnicianQueryService/JdbcTechnicianQueryRepository, immutable read models, TechnicianQueueController, combined dashboard/queue templates | TechnicianQueryServiceTest, JdbcTechnicianQueryRepositoryTest, TechnicianQueueControllerTest and authenticated SecurityIntegrationTest rendering; [T1 report](../logs/technician/T1-REPORT.md), 547-test full check passed | locally verified; teammate/browser/hosted review pending |
| REQ-TECH-004–006 | [Technician manual workflow](specs/ROLE-TECHNICIAN.md#manual-workflow-requirements) | TechnicianCommandService/JdbcTechnicianCommandRepository, command controller and versioned start/log forms | service/PostgreSQL/controller/authenticated-rendering/JS checks; [T2 report](../logs/technician/T2-REPORT.md) | locally verified; teammate/browser/hosted review pending |
| REQ-TECH-007–008 | [Technician manual workflow](specs/ROLE-TECHNICIAN.md#manual-workflow-requirements) | existing TechnicianCommandService/JdbcTechnicianCommandRepository completeWork, completion form/controller, persisted metadata/history | completion service/PostgreSQL/controller/authenticated MVC checks; [T3 report](../logs/technician/T3-REPORT.md) | locally verified; teammate/browser/hosted review pending |
| REQ-TECH-009 | [Technician manual workflow](specs/ROLE-TECHNICIAN.md#manual-workflow-requirements) | assigned-only Request detail, scoped full Requester Update/Work Log reads, retained historical authorship and safe 404 | PostgreSQL history/reassignment checks, service/controller tests and authenticated escaped-history rendering; [T1 report](../logs/technician/T1-REPORT.md), full check passed | locally verified; teammate/browser/hosted review pending |
| REQ-TECH-010–011 | [Technician manual workflow](specs/ROLE-TECHNICIAN.md#manual-workflow-requirements) | T2 locked persisted account/assignment/state/version writes; explicit start/log/completion endpoints only | stale/reassignment/concurrency/forbidden-route checks; [T2 report](../logs/technician/T2-REPORT.md) | manual start/log/completion locally verified; teammate/browser/hosted review pending |
| REQ-MGR-001–003 | [Manager request/review](specs/ROLE-MANAGER.md#request-and-review-requirements) | Manager queue/search/filter/order, Request detail/history and active Requester on-behalf creation with Recording Actor | ManagerInputTest; ManagerRepositoryIntegrationTest ordering/literal search/create/inactive/rollback/detail history | locally verified; browser and teammate review pending |
| REQ-MGR-004–009 | [Manager request/review](specs/ROLE-MANAGER.md#request-and-review-requirements) | named assign/reassign/cancel/close/return/reopen/correct service operations with state/version/active-Technician checks, atomic audit and preserved Work Log/completion history | ManagerRepositoryIntegrationTest valid transitions, stale/wrong-state/inactive-Technician rejection, history retention and injected audit rollback | locally verified; browser and teammate review pending |
| REQ-MGR-010–011 | [Manager request/review](specs/ROLE-MANAGER.md#request-and-review-requirements) | status/priority counts, active Technician workload; read-only Request/actor/action/date audit filters | ManagerRepositoryIntegrationTest workload, action-filter and missing-association regressions | first slice locally verified; browser and teammate review pending |
| REQ-MGR-012 | [Manager request/review](specs/ROLE-MANAGER.md#request-and-review-requirements) | controllers call named lifecycle operations; no raw status edit endpoint exists | ManagerRepositoryIntegrationTest lifecycle/state matrix | locally verified; teammate review pending |
| REQ-MGR-013–015 | [Manager accounts](specs/ROLE-MANAGER.md#account-administration) | create/state/role/password operations with session invalidation, self guard, active-assignment guard and active-Manager locking | ManagerRepositoryIntegrationTest self/Technician guards, reset/audit/session-version assertions | locally verified; concurrency/browser and teammate review pending |
| REQ-MGR-016–019 | [Category and skill administration](specs/ROLE-MANAGER.md#category-and-skill-administration) | category/skill create/rename/deactivate pages; atomic category replacement migration; active Technician skill editing with inactive-link preservation | ManagerRepositoryIntegrationTest migration success/rollback/audits and historical skill-link retention | locally verified; browser and teammate review pending |
| REQ-MGR-020–025 | [Triage Assistant and matching](specs/ROLE-MANAGER.md#triage-assistant-and-deterministic-candidate-ranking) | typed non-mutating Triage provider, bounded/fresh context, deterministic ranking, API/review surface, and human-approved OPEN assignment through the ordinary locked Manager operation | `SocLlmTriageAssistantTest`, `TriageCandidateRankerTest`, `ManagerTriageServiceTest`, `JdbcManagerTriageRepositoryTest`, `ManagerTriagePageControllerTest`, `SecurityIntegrationTest` and ManagerRepositoryIntegrationTest approved-triage/stale-save assertions | locally verified; real-browser and teammate review pending |
| REQ-AI-001, REQ-AI-003–004 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | `.env.example`, `application.yml`, `SocLlmProperties`, `AiConfiguration`, `AiConfigurationValidator`, `AiSettingsConfiguration`, `AiProviderLifecycle`, `AiProviderLifecycleConfiguration`, `SocLlmHttpsProviderAdapter`, `ProviderHttpTransport`, `ProviderCredentialCapability`, strict model-envelope compatibility metadata | existing configuration evidence; `SocLlmHttpsProviderAdapterTest`; `AiProviderLifecycleConfigurationTest`; `ProviderEnvelopeDecoderTest`; `SocLlmProviderRuntimeFactoryTest` including `qwen3.8:27b` readiness | configuration, adapter/lifecycle and current SoCLaaS model metadata locally verified; teammate/release review pending |
| REQ-AI-002, REQ-AI-005–006 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | `SocLlmProviderCore`, `ProviderFailure`, `SocLlmHttpsProviderAdapter`, `SocLlmProviderRuntimeFactory`, non-reasoning structured completion requests, current inert SoCLaaS/vLLM completion metadata, public `WorkPlanAssistant` seam and strict Work Plan decoder | provider-core tests including separate executor ownership; adapter/lifecycle readiness tests; `ProviderEnvelopeDecoderTest`; `SocLlmWorkPlanAssistantTest`; full Gradle suite | 2A/2B/2C/2D implementation and current completion compatibility locally verified; fresh 2D security and teammate/release review pending |
| REQ-AI-007–010 | [Usage and audit metadata](specs/INTEGRATION-SPEC.md#usage-and-audit-metadata) | `AiInvocationTelemetry`, JDBC STARTED/terminal persistence, safe redaction, provider usage/retry metadata and failure withholding | `JdbcAiInvocationTelemetryTest`, coordinator/provider retry tests, `TechnicianWorkPlanServiceTest`, full Gradle suite | implementation locally verified; reviewed schema revision, role-owner/Security Reviewer/teammate verification pending |
| AI-SEC-001–007 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | authorization-first `AiAccessGuard`, Technician Work Plan, Requester Smart Report and Manager Triage role/service seams, shared coordinator and PostgreSQL-backed Account/global admission | `AiAccessGuardTest`, `WorkPlanAuthorizationBoundaryTest`, `TechnicianWorkPlanServiceTest`, `RequesterSmartReportServiceTest`, `ManagerTriageServiceTest`, repository freshness tests, `AiInvocationCoordinatorTest`, `DatabaseMigrationTest`; broader AIS-T01–T08 pending | role/admission slices locally verified; review and broader corpus pending |
| AI-SEC-008 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | configuration hard ceilings plus process-local fallback and PostgreSQL-time Account admission; Technician, Requester and Manager use-case deadlines created once at entry and propagated through readiness, context, provider and Delegating feature seams; per-caller `AiReadinessDeadline` isolates stalled readiness expiry and permit release | configuration boundary tests, `AiInvocationCoordinatorTest`, role-service deadline tests, `AiReadinessDeadlineTest` deterministic expiry/late-result/shared-readiness coverage for all three roles, `AiFeatureContractTest` delegate forwarding and `DatabaseMigrationTest` | role deadline propagation and stalled-readiness expiry locally verified; reviewer verification pending |
| AI-SEC-009–010 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | bounded decoder plus identity-only Iteration 2B status/hand-off; retry execution remains separately implemented and compressed responses deferred | provider-core tests plus `SocLlmHttpsProviderAdapterTest` identity-encoding/body-closure and bounded-output cases; retry tests; compressed-wire tests intentionally absent per spec | identity-only transport locally verified; compressed-response scope deferred; reviewer verification pending |
| AI-SEC-011–018 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | Work Plan prompt template/version with exact result schema, allowlisted context projection, versioned multilingual/obfuscation-aware/negation-aware `WorkPlanSafetyPolicy`, strict typed/safety decoder, shared telemetry/retry behavior and fail-closed disclosure policy | `SocLlmWorkPlanAssistantTest`, `WorkPlanSafetyPolicyTest`, telemetry/retry tests and provider-package suite; broader AIS-T09–T14 corpus review pending | deterministic safety-policy implementation locally verified; human-reviewed corpus and final review remain pending |
| AI-SEC-019 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | server-side raw binding, official-origin validation, placeholder/control rejection, redacted diagnostics, opaque credential capability, HTTPS adapter and startup-safe lifecycle wiring | existing configuration tests; provider-core tests; adapter credential/TLS/redirect/transport cases; lifecycle disabled/unavailable/available readiness cases | Iteration 2B/2C implementation locally verified; fresh Security Reviewer GO; teammate/release review pending |
| AI-SEC-020 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | startup-safe disabled/unavailable/available configuration states; generation-aware readiness lifecycle with negative-cache recovery, separate discovery/provider executors, idempotent close and inert current SoCLaaS model metadata | `ProviderEnvelopeDecoderTest`; `SocLlmProviderRuntimeFactoryTest` including `qwen3.8:27b`, post-negative-cache recovery and concurrent joiners; provider-core executor ownership and close-race tests | readiness recovery/model metadata compatibility locally verified; fresh Security Reviewer, teammate and release review pending |
| AI-SEC-021–022 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | V3/V5 rolling-window storage, PostgreSQL-time Account admission and owner/version-fenced global leases; prompt templates remain feature-owned | `DatabaseMigrationTest` durable admission/stale-release cases and AIS-T10 pending | partial; distributed admission locally verified; telemetry/retry/prompt review pending |
| AI-SEC-023–027 | [Human/process gates](specs/AI-SECURITY-SPEC.md#human-and-development-process-gates) | pending | AIS-T15 and review evidence pending | approved |
| AI-SEC-028–030 | [Response freshness and leakage controls](specs/AI-SECURITY-SPEC.md#response-freshness-and-leakage-controls) | Technician assignment/status/version, Requester ownership/status/version/category and Manager Request/catalogue/session/candidate freshness queries; captured-session revalidation; output identifier/markup/control rejection; server-assembled normalized deny material; POST/CSRF/role enforcement and `Cache-Control: no-store` responses | role-service stale-eligibility tests, `JdbcRequesterSmartReportRepositoryTest`, `JdbcManagerTriageRepositoryTest`, malformed/unsafe/disclosure-canary provider tests and `SecurityIntegrationTest`; broader AIS-T16–T18 pending | Technician, Requester and Manager current-session/freshness/browser boundaries locally verified; role-owner, Security Reviewer and teammate review pending |

## Update rule

Requester step 7 evidence: [local verification record](../logs/2026-10-16-requester-step-7-verification.md)
maps RQ-A01–07, RQ-AI01–03 and applicable AIS cases to executed test methods.
REQ-RQ-001/002/005–008/015–016 now also have real Chrome happy-path evidence;
REQ-RQ-003 has browser zero-match guidance and REQ-RQ-009 foreign-content
exclusion. REQ-RQ-011 has a graceful app restart/JDBC-session regression.
Remaining browser qualifications in individual rows include latest-stable
confirmation, manual accessibility and cases not exercised in Chrome (such as
full search/filter combinations, AI failure and stale-response paths).
REQ-RQ-012–017 / AI-SEC-003/010/030 have a versioned 12-case deterministic fake
evaluation corpus, not a live model usefulness or injection-immunity result.
AI-SEC-013/014 and REQ-SH-012 have partial browser evidence for review/ordinary
submission and shared password/logout navigation. AIS-T15 formal poisoned-handoff
execution, full cross-role lifecycle, human review and hosted/live checks remain
open. Neither the load task nor section 9 exit is marked passing.

Technician T0 (18 October 2026) adds [implementation contracts](technician/CONTRACTS.md),
[requirement-to-test planning](technician/ACCEPTANCE-MAP.md) and
[T1 task packets](../logs/technician/T1-backend-task.md) under the scoped
Technician development workflow. This is design/evidence only: no application
implementation, test execution or requirement verification is claimed. Manual
REQ-TECH-001-011 and T1 implementation were pending at T0; current T1 status is
recorded above. Existing AI evidence and human-review statuses remain unchanged.
The [T0 report](../logs/technician/T0-REPORT.md)
records actual agent work and configuration/document checks.

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

## Requester-specific AI evidence retained during master integration

The shared/other-role gateway rows above and this Requester-specific table cover
different implementations. The review UI uses its existing SmartReportService
pipeline; master's `/requester/smart-report` provider seam is also present. This
merge does not claim they have been consolidated or that provider/hosted release
checks have passed. Existing performance failures remain recorded above.

| Requirement IDs | Authoritative section | Requester implementation | Requester test/evidence | Requester status |
| --- | --- | --- | --- | --- |
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
| AI-SEC-005 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | transport sends exactly model/stream/reasoning_effort=none/max_tokens/system-user messages; no tools or mutation seam | exact HTTP payload and zero business-write tests | locally verified; live smoke deferred |
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


## T5 resumed local verification

Own-password service/JDBC transaction, shared controller/header/password/About
pages, pending forms and accessibility/lifecycle polish address scoped
REQ-SH-002/004/006/009/010/012/014/018, NFR-SEC-001/002, NFR-A11Y-001/002,
NFR-REL-001 and NFR-DOC-001. User paused after authorship and explicitly resumed.
Historical pause/dispatch records remain history, not current execution status.
Service/real PostgreSQL/controller/security/script checks now passed locally.
PasswordChangeSecurityIntegrationTest covers actual encoder, JDBC sessions,
next-login password replacement and audit rollback. SecurityConfiguration's
credential-free failed-login redirect fixes observed session exception leakage;
session-byte and existing-session regressions cover it. Final integrated totals,
independent correction verification and local commit identity are recorded in
[T5 report](../logs/technician/T5-REPORT.md).

Browser/keyboard/viewport/contrast acceptance is blocked by disabled local
browser use in Work settings and remains pending; automated fixtures do not
verify it. Existing human role-owner/teammate/gateway/corpus/hosted-release gates
remain open. Earlier rows retain their scoped historical evidence, not blanket
release acceptance or completion of other roles' account-change use cases.

## Master/Technician integration verification

Master `f576db0` is combined with Technician `73748f6` in the resolved merge
candidate. Both role slices and shared password/navigation features are retained.
The combined local `check bootJar` passed 669 tests across 53 suites with zero
failures, errors or skips, plus JaCoCo, executable JAR packaging and all three
JavaScript checks. See the [merge-resolution report](../logs/2026-10-18-master-technician-merge-resolution.md)
for preservation checks and direct execution evidence. Resolution is staged;
human teammate review and the existing browser/hosted/safety gates remain open.

## 19 October Requester live transport fix

REQ-RQ-012–017, REQ-AI-001–006 and AI-SEC-008/010/019/020: the earlier
Requester live-smoke deferral annotations are superseded for the narrow synthetic
gateway scope by [live evidence](../logs/2026-10-19-requester-live-ai-smoke.md).
The initial truncation finding remains recorded. SoClaasTransport now sends
reasoning_effort=none; exact-payload regression and gateway suites passed (19
tests). Three rebuilt-production live gateway cases passed strict validation,
with no retries or business writes. Fresh verifier found no blockers. Full
authenticated live browser/service testing, other roles, broader response quality
and human safety review remain pending; this is not release approval.

19 October authenticated live continuation: the Requester create-assistant POST
passed three real SoCLaaS cases through actual Spring MVC/security/JDBC sessions
and disposable PostgreSQL. Anonymous, wrong-role and missing-CSRF checks passed;
no-store and invocation metadata verified, zero maintenance/submission/update/log
mutations. This supersedes authenticated endpoint deferral only; live browser,
owned-edit/other-role and broader quality/security checks remain pending. Exact
metrics and provenance are in the live evidence record above.
