# T5 implementation-phase contract

User approval: “Proceed with T5, but pause after the implementation phase, do not despawn agents, just pause the process and state the next step.” Baseline c15d26e, clean technician-workflow branch. This overrides the normal automatic progression to verification/review/commit: author implementation, tests, guides and acceptance preparation only; perform static integration inspection, then pause. No compilation, test/script/browser execution, candidate acceptance, reviewer dispatch, staging, commit, push, merge or deployment before resume. Keep all agents available/idle; do not terminate them. Existing T0–T4 evidence remains historical. New work must say authored/unverified, never verified.

## Scope and authority

PRODUCT-SPEC REQ-SH-002/004/006/009/010/012/014/018, shared account password rule (8–24 Unicode code points, no composition rule), NFR-SEC-001/002, NFR-A11Y-001/002, NFR-REL-001 and NFR-DOC-001; ROLE-TECHNICIAN manual and Work Plan workflows. Existing UTC+Asia/Singapore display and manual validation/retained drafts remain. Identity slice remains accountable to yooplo; this approved shared own-password contribution transfers no ownership. No Manager reset/account administration, other role manual workflows, catalogue/migration/dependency/global workflow changes, release performance/backups/production work or live provider/database.

## Fixed identity interface and behavior

New identity classes: PasswordChangeService(CurrentAccountProvider, PasswordChangeRepository); void changeOwnPassword(String currentPassword, String newPassword, String confirmPassword). No target-account parameter: capture current authenticated CurrentAccountSnapshot server-side. New PasswordChangeRepository: void changeOwnPassword(CurrentAccountSnapshot actor, String currentPassword, String newPassword). New JdbcPasswordChangeRepository(DataSource, PasswordEncoder) implements it. Existing encoder bean is reused, including legacy PBKDF2 verification and Argon2 encoding. New PasswordChangeException has Code INVALID_INPUT, INCORRECT_CURRENT_PASSWORD, ACCESS_CHANGED, UNAVAILABLE; constructors (Code) and (Code, Map<String,String> fieldErrors), code() and fieldErrors(). Messages/maps are fixed safe values; discard operational exception causes/SQL/hash/password values. Do not introduce a DTO that auto-renders secret values.

Require current password nonempty and at most 24 Unicode code points; new password 8–24 Unicode code points unchanged (no trimming, normalization or composition rule); confirmation exact equality and required. A whitespace-only password of valid length is permitted by the no-composition rule. No new-password-differs rule. Reject invalid lengths before hashing. Error field keys currentPassword/newPassword/confirmPassword; fixed messages and empty maps permitted on operational/authorization failures. Revalidate required current/new input in direct persistence seam too. Do not echo/log/store submitted passwords in model, flash/session, audit, exceptions or record toString.

One short READ_COMMITTED REQUIRES_NEW transaction locks current user_account FOR UPDATE. Before reading password hash for verification/mutation, require persisted active account, same enum role and exact nonnegative session version as captured actor. Missing/inactive/mismatched snapshot => ACCESS_CHANGED, no mutation/audit. Verify current password against locked persisted hash; mismatch => INCORRECT_CURRENT_PASSWORD. Encode new password using injected maintained encoder, update only password_hash, version=version+1 and updated_at using database UTC time; retain immutable/account fields and session_version. Append exactly one ACCOUNT_PASSWORD_CHANGED audit with actor=target=current account, target_type ACCOUNT, safe structured details such as {changeType: OWN}; no password/hash fields. Audit/persistence/encoder failure leaves account+audit unchanged and maps to safe UNAVAILABLE without cause. Locking plus verifying latest stored password serializes concurrent changes; stale old passwords cannot overwrite a newer different password. Identical new/current allowed; no idempotency claim without an explicit token.

Own-password change retains the current session and does not mutate session_version or rotate/invalidate other sessions; REQ-SH-004 specifies invalidation for logout/deactivation/Manager reset separately. Do not log out or replace the principal/security context on own-password success. Existing role/session filter and current-account provider remain unchanged. Preserving session_version also preserves current AI snapshot validity; ordinary Request/account changes keep their existing gates.

## Fixed web/UI interface

New web SharedPagesController(PasswordChangeService). Protected GET /account/password -> account/change-password; POST /account/password accepts optional currentPassword/newPassword/confirmPassword parameters and delegates to service; successful POST uses fixed context-relative303 /account/password?changed, GET shows fixed success notice without any password. Invalid input/current password400, changed access403, unavailable503; render same view with fieldErrors/passwordError only, never raw secret fields. Set Cache-Control no-store on all shared pages/failures. POST remains CSRF-protected by existing security chain. Protected GET /about -> about. Add standard title/username/displayName/role from authenticated CurrentAccountPrincipal, not request parameters. Account/password GET returns no fields from persisted hash.

New shared authenticated header fragment templates/fragments/authenticated-header.html with fragment header: identity, role-relevant main workspace (enum-mapped fixed route), Change password, About, POST CSRF logout. Use it in technician queue/detail, existing dashboard and new password/about views. Do not expose navigation for other roles. All links/actions context-relative, no user-supplied redirect. Navigation hiding is not authorization. Add skip-to-main, semantic navigation, main id, consistent visible focus and responsive wrapping for760x600,1024x700,1440x900. Preserve escaped history/input and explicit date/time/zone already rendered. Password fields required, autocomplete current-password/new-password, labels and error associations, guidance for Unicode lengths; do not use HTML UTF16 minlength/maxlength to contradict code-point bounds. Password fields intentionally clear on server failure rather than rendering credentials back; ordinary business drafts stay preserved.

Generic external static/js/form-submission.js handles only form[data-pending-form] on login/password/logout: one pending submit, disable submit buttons only, aria-busy and restore on pageshow, no field reads/storage. Existing command forms remain on technician-commands.js with no duplicate handler. Root permits only /js/form-submission.js publicly so login can load it; all protected pages remain authenticated. Existing /css/** public styles can be reused (technician.css expanded for shared layout, explicit background/text/high-contrast focus/errors and responsive forms). No inline script/style or new dependency.

UI also owns technician-work-plan.js for narrow lifecycle polish: immediately disable AI request when manual submit requires refresh, clear obsolete pending/status text appropriately when advice is invalidated, preserve generation/abort suppression and all existing schema/safety/plain-text/manual boundaries. Keep all seven contracted DOM IDs and safety/stop first; no new AI/server contract or business operation. Avoid unrelated redesign.

## Exact ownership

- Backend: new src/main/java/sg/edu/nus/facilityflow/identity/{PasswordChangeService,PasswordChangeRepository,JdbcPasswordChangeRepository,PasswordChangeException}.java; own backend return handoff.
- UI: new web/SharedPagesController.java; new templates/fragments/authenticated-header.html, templates/account/change-password.html, templates/about.html; existing templates/dashboard.html, login.html, technician/queue.html, technician/request-detail.html; existing static/css/technician.css; new static/js/form-submission.js; existing static/js/technician-work-plan.js; own UI return handoff. No other Java/security edits.
- Tester: new src/test/java/sg/edu/nus/facilityflow/identity/{PasswordChangeServiceTest,JdbcPasswordChangeRepositoryTest,PasswordChangeFixtures}.java; new web/SharedPagesControllerTest.java; new src/test/js/form-submission.test.cjs; existing src/test/js/technician-work-plan.test.cjs; new logs/technician/T5-BROWSER-ACCEPTANCE.md; own test return handoff. No production edits or test execution.
- Root: existing SecurityConfiguration.java exact public script matcher, existing SecurityIntegrationTest.java integration test authoring, UserGuide/DeveloperGuide, TRACEABILITY/REUSE/technician ACCEPTANCE-MAP, T5 dispatch/contracts/report/pause/evidence. Existing workflow config/specs, migrations and build files reserved unchanged.

## Tests/acceptance to author, not execute yet

Service: captured current actor, nonnegative snapshot, 7/8/24/25 code-point new passwords including supplementary characters/whitespace, current/confirmation errors, no trim, invalid input no persistence, dependency-safe failures. Real isolated PostgreSQL with existing migration/restricted-role pattern (not SQLite): all roles, active/exact role/session, hash replacement and legacy verification, version+UTC audit, session-version retention, incorrect password, audit rollback and concurrent changes using row locks/latest hash. Tests use synthetic data only. JUnit5 with manual/standalone Spring7 contexts; no new dependencies.

Standalone MVC and root real security/Thymeleaf tests: role-specific shared navigation, required/error-associated fields, no secret echo or session serialization, fixed303/no-store, auth/CSRF/non-user-targeted action, unavailable/failure fallback, unchanged session/cookie next request. Separate service doubles in H2 web fixture from real PostgreSQL repository evidence. Script tests execute actual scripts when resumed: pending duplicate suppression, independent forms, pageshow restore, no password reads/storage; Work Plan discard/retry/bfcache/late result/manual fallback boundaries. Browser checklist covers latest stable desktop Chrome/version recording, keyboard-only complete manual lifecycle+password/AI fallback, all three viewport sizes, labels/focus/contrast/zoned dates, forbidden actions and no-JS manual usability; leave every execution item pending.

## Pause boundary

All leaves author files and return skill-compliant durable+OS-temp handoffs, then remain idle/available. Root inspects static integration/contracts and documents authored scope and unresolved risks, with all execution pending. Do not preflight, test, browse app, dispatch reviewer or commit. On explicit resume: compile all changed tests, minimal isolated PostgreSQL password/audit smoke and script checks; fix mechanical issues with retained owners, freeze candidate, one combined independent review concurrent with scoped execution; browser acceptance and full check/bootJar, final verdict and local exact commit only when authorized to continue. Existing human merge/gateway/release gates remain. Runtime/token metadata goes only in conversation/ignored build artifacts.


## Resume authorization

User subsequently said “Resume”, revoking the implementation-phase pause and
authorizing the remaining preflight, batched independent review, scoped/browser
acceptance, integrated check and local verified implementation/evidence commit.
Retained backend/UI/tester models and ownership remain. Coordinator additionally
owns new identity/PasswordChangeSecurityIntegrationTest.java to verify the real
maintained encoder + restricted PostgreSQL account/audit + filters/JDBC sessions
without a service double. All fixtures are disposable and synthetic; no live
provider or hosted database. Earlier pause records retain their historical status.
No push, merge or deployment. Browser tool currently reports local browser use
disabled in Work settings; do not claim browser acceptance or bypass that setting.
Proceed with unaffected local automated checks and record any outstanding gate.

Demonstrated NFR-SEC-002 correction during resumed preflight: the real session
serialization regression reached a failed-login case and found an attempted
credential inside Spring's default session-stored AuthenticationException.
Coordinator's existing SecurityConfiguration ownership now also replaces
failureUrl with a fixed credential-free redirect handler, removing any existing
authentication exception attribute and storing no new failure. Generic failure
message/redirect and CSRF semantics stay unchanged. Existing root MVC and new
real PostgreSQL/session tests exercise the correction; independent reviewer must
verify source and relevant direct execution. No new authentication policy.
