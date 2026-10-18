# MP2 Reuse Ledger

16 October Requester step 7: the Node/CDP browser/load runner, opt-in Gradle
task, graceful application/session restart regression, versioned 12-case AI
corpus and gateway evaluation test are **new** MP3 test/build work. The related
DeveloperGuide, plan, traceability, session evidence and handoff sections are
**new** documentation. No MP2 source was consulted or copied in this step
(zero copied MP2 lines). Acceptance/security scenarios come from approved MP3
specifications; no third-party implementation source was copied. Existing MP3
fixtures and standard Node/Chrome interfaces are reused internally. Exact
final code/test/documentation line totals and teammate review remain pending.

## Baseline

- Source repository: `CS3227-2610-MP2-FacilityFlow/CS3227-2610-MP2`
- Verified source commit: `9819b857d880134c1fc1ffef8a8bcd5d6e536f43`
  (29 September 2026, MP2 v1.0.1 merge on `master`)
- Target repository: `CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3`

The source commit was verified against the MP2 `master` branch on 12 October
2026. Every reused item must be recorded before its pull request is merged.

## Classifications

- **Unchanged:** copied without behavior changes.
- **Adapted:** derived from MP2 with material changes for the web architecture.
- **Concept only:** domain knowledge or scenarios reused, but implementation is
  new.
- **New:** no MP2 source was used.

## Ledger

| MP2 source | MP3 destination | Classification | Reused lines | Changed/new lines | Reason and acknowledgement | Pull request |
| --- | --- | --- | ---: | ---: | --- | --- |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` at verified MP2 commit | same wrapper paths | Unchanged | 349 text lines plus one binary wrapper JAR | 0 | Reuse the checksum-pinned Gradle 9.1.0 bootstrap already used by the team; no application behavior reused | `feat/authentication-foundation` (review pending) |
| `specs/components/authentication-authorization.md` at verified MP2 commit | MP3 identity/web/AI guard behavior and tests under `src/` | Concept only | 0 copied code lines | 1,149 new source/resource/test lines | Adapt account, generic-login-failure, role, ownership, assignment and deactivation scenarios to Spring Security/JDBC/PostgreSQL; JavaFX session implementation not copied | `feat/authentication-foundation` (review pending) |

## Planned treatment

| MP2 asset | MP3 treatment |
| --- | --- |
| Lifecycle states and transition rules | Reuse behavior; adapt tested Java domain logic |
| Role permissions and audit semantics | Reuse requirements and selected service checks |
| Domain models and validation | Adapt selected Java classes for Spring and PostgreSQL |
| Tests and fixtures | Reuse scenarios; adapt code to Spring integration tests |
| JavaFX interface | Do not reuse |
| SQLite storage | Reuse schema concepts only; PostgreSQL/Flyway implementation is new |
| Documentation | Reuse factual domain descriptions with acknowledgement; rewrite for the released web product |

## Current evidence and measurement detail

Requester step 6 (16 October): advisory review/accept/discard workflow follows
**concept-only** behavior retained in approved MP3 Requester/AI specs, with zero
copied MP2 code lines. No MP2 source consulted or copied. `SoClaasTransport`,
runtime bean/configuration and typed transport failure additions, review template/
JavaScript/CSS, local HTTP and DOM checks, PostgreSQL review-to-ordinary-submit
test and guide/traceability/session/handoff updates are **new** MP3 work.
No migration or dependency changes; final measurements and teammate review pending.

Requester step 5 (16 October): Smart Report advisory/context/schema concepts are
**concept-only** behavior retained in approved MP3 Requester/Integration/AI Security
specs, with zero copied MP2 code lines. No MP2 source consulted/copied this step.
`ai/` Smart Report input/service/schema, gateway/transport and persistence seams,
disabled runtime adapter, bounded gateway, JDBC limits/telemetry, owner/OPEN-version
read, POST controller, fake/gateway/PostgreSQL tests and associated guides/process
records are **new** MP3 implementation. Existing V1–V5 are unchanged; no dependency
was added. Final measurement and human teammate review remain pending.

Requester step 4 (16 October): dashboard/status counts, five recent reports,
literal search and combined filters follow **concept-only** Requester discovery
behavior already retained in approved MP3 specs (zero copied code lines).
No additional MP2 source consulted or copied. `RequestFilters`, use-case/repository/
controller additions, dashboard/list templates and CSS, filter/PostgreSQL tests,
authentication-only dashboard stub and guide/traceability/session/handoff updates
are **new** MP3 implementation. No migration or dependency changes. Final line
measurement and teammate review remain pending on `feat/requester-workflow`.

Requester step 3 (16 October): edit/cancel/Requester Update use cases, Request
version/state locking, reason/note validation, shared form/action templates and
tests are **concept-only** reuse of behaviors already approved in MP3 product/
Requester specs and retained MP2 lifecycle concepts (zero copied code lines).
No additional MP2 source consulted or copied this step. The Spring/JDBC service
and repository additions, typed failure classes, view-model version/category
fields, shared error handling, named-submitter pending protection, tests and
guide/session/handoff updates are **new** MP3 implementation. No migrations,
dependencies or additional role operations added. Final line measurements and
teammate review remain pending on `feat/requester-workflow`.

Requester step 2 (16 October): `RequestDraft`, Requester use cases/repository,
controller, templates and tests are **concept-only** reuse of the approved MP2
Requester workflow/field rules and `RequesterDashboardView.showDetail` at the
verified baseline (zero copied code lines). MP2 detail inspection confirms the
report/status/urgency/priority/timestamp presentation without internal completion
metadata. Spring MVC/Thymeleaf/PostgreSQL implementation is new. V5 migration,
schema readiness check, durable submission handling, pending-submit script and
Node self-check, CSS, About page, guide updates and session/handoff records are
**new** MP3 work. Existing V1–V4 production migrations are unchanged. Final line
measurement and teammate review remain pending on `feat/requester-workflow`.

Requester step 1 (16 October): `PasswordChangeService`, `JdbcIdentityOperations`,
`PasswordController`, password template and associated tests reuse MP2
`AuthenticationService.changePassword` and `ApplicationRouter` password-page
behavior at the verified baseline as **concept only** (zero copied code lines).
Current/new password inputs, Unicode limits, current-session retention and
password audit semantics are retained; the Spring/JDBC implementation is new.
`AuthenticatedPageAdvice`, shared header edits, login audit failure handling,
`CurrentAccount.sessionVersion`, H2 fixture additions and guide/session records
are **new** MP3 changes. No JavaFX or SQLite code copied. Final line measurements
and teammate review remain pending; branch `feat/requester-workflow`.

Facilities Manager first slice (17 October): overview, deterministic queue,
combined filters, on-behalf creation and audit-viewer behavior are
**concept-only** reuse of approved MP2 domain behavior and MP3 requirements.
No MP2 JavaFX or SQLite source was copied. The Spring MVC/JDBC implementation,
templates, CI workflow and PostgreSQL tests are new MP3 work. Two MP2 findings
were intentionally converted into new regression tests: action filtering and a
missing Request association remaining null rather than becoming a sentinel ID.
Exact final line measurements and teammate review remain pending.

Part 3 V2/V3, initialization source/tests, database test extensions and completion
guide/ADR-006 are **new** MP3 code/documentation, not MP2-derived. Demo usernames,
development-only password and lifecycle scenarios are **concept-only** reuse
already retained in approved PRODUCT/TECHNICAL specs; new Java/SQL implements them.
No MP2 source consulted/copied this session. Spring Security's PBKDF2 API supplies
hashing; no third-party implementation source copied. V1/upstream session DDL is
unchanged. Exact final reuse measurement remains release work.

Part 2b identity/audit constraints, role provisioning, RLS/grants, Flyway profiles,
DatabaseMigrationTest and DatabaseMigrations guide are **new** MP3 work; no MP2
files copied. The session-table section of V1 adapts **upstream Spring Session
JDBC 4.1.1**, Apache-2.0, with schema qualification and new role security; it is
not MP2 reuse. Source: cached artifact's `schema-postgresql.sql`, linked in
docs/DatabaseMigrations.md. Embedded Postgres 2.2.2/Postgres 17.7.0 is a new
test-only dependency. Final line measurements remain pending release.

Part 2a Supabase JDBC configuration, DatabaseConnectionCheck and its tests,
SupabaseSetup guide and ADR-004 are **new** MP3 work; no MP2 source consulted or
copied. Existing build/application/startup-test documentation is adapted from
the new Part 1 scaffold, not MP2. These are prerequisite artifacts, not measured
final production reuse totals.

Part 1 build scaffold is **new**, not derived from MP2: `build.gradle`,
`settings.gradle`, `src/main/java/sg/edu/nus/facilityflow/FacilityFlowApplication.java`,
`src/main/resources/application.yml` and its startup test. Gradle wrapper scripts
and JAR are generated by Gradle 9.1.0 (upstream Gradle templates, not MP2).
Setup/documentation changes describe these new artifacts; no MP2 source was
consulted or copied. See ADR-003 and the Part 1 session log. Final measurements
remain pending until release.

No MP2 application source/tests are present at specification time. Treatment
rows above are plans, not completed code reuse. MP2 specification concepts were
consulted for the MP3 draft; exact source sections and actual reused amounts
must be recorded before each implementing pull request is merged.

Each ledger entry must record component/section, original commit/location,
target, classification (unchanged, adapted, concept-only or new), actual MP3
changes, approximate amount with units, justification, acknowledgement and
comparison evidence. Separate code, test and documentation totals; do not submit
planned estimates as actual reuse percentages.

| Component / section | Source / location | Target | Classification | Changes for MP3 | Approximate amount / units | Justification | Acknowledgement / evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Initial MP3 scaffolding from the 6 October 2026 setup session | Current user request and existing MP3 guidance; no MP2 files consulted | New scaffolds and documentation additions listed in logs/2026-10-06-repository-scaffolding.md | New | Initial process/placeholders | File inventory in session report; no MP2 copied lines identified | Establish requested workflow | AI-assisted with Codex; human review pending |
| Authentication/authorization behavior | MP2 `specs/components/authentication-authorization.md`, verified source commit above | `src/main/java/sg/edu/nus/facilityflow/identity`, `src/main/java/sg/edu/nus/facilityflow/web`, `src/main/java/sg/edu/nus/facilityflow/ai/AiAccessGuard.java`, and related tests | Concept only | 0 copied code lines; 1,149 new source/resource/test lines | Replace JavaFX/in-memory sessions with Spring Security, JDBC sessions, PostgreSQL/Flyway contracts and authorization-before-AI; reuse named scenarios and invariants only | AI-assisted implementation; `git diff`/line count and test log dated 13 October 2026; teammate review pending |
| Gradle wrapper bootstrap | MP2 `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties`, verified source commit above | Same MP3 paths | Unchanged | 349 text lines and one binary JAR unchanged | Preserve checksum-pinned Gradle 9.1.0 wrapper; dependency/build definitions in `build.gradle` and `settings.gradle` are new | File hashes/diff available on review branch; teammate review pending |
| SoCLaaS gateway, provider adapters, feature contracts and AI tests | No MP2 implementation source; MP3 `workflow/specs/INTEGRATION-SPEC.md` and `AI-SECURITY-SPEC.md` | `src/main/java/sg/edu/nus/facilityflow/ai`, role AI seams and gateway tests | New | Provider configuration, HTTPS identity transport, readiness, admission, telemetry, retry, strict typed contracts, disclosure policy and Technician orchestration are new MP3 code; no MP2 lines copied | Implement the approved MP3 SoCLaaS boundary with server-side authorization, limits and safe fallback | AI-assisted implementation; review range `e784d48..HEAD`; focused/full test logs dated 16–17 October 2026; teammate/Security Reviewer verification pending |

Detailed provenance of pre-existing documents and future concept-only reuse:
TBD after checking MP2. Preserve existing content until comparison supports
accurate classification.

16 October assignment review fix: V4, its regression test and the evidence-date
clarification are new MP3 work; no MP2 source consulted or copied. Existing
applied migrations are unchanged. See the dated review-fix log.

Additional 16 October initializer review fixes (profile guard, bounded lock wait,
regressions and guide updates) are new MP3 work; no MP2 source copied.

18 October Requester/master integration: merge resolutions and compatibility
updates are new MP3 integration work. Requester migration moved from V5 to V6
without SQL changes; master V5 preserved. No additional MP2 source copied;
existing per-slice reuse provenance above remains authoritative.
