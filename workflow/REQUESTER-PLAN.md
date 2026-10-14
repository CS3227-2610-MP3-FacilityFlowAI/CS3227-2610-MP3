# Requester development plan

Prepared 14 October 2026 (Asia/Singapore). Owner: Requester role owner (`yooplo`
in AGENTS.md). Status: proposed execution plan against approved specifications
v0.1, 12 October 2026. Part 1 build scaffold implemented and tested on 14 October;
authentication, database integration and Requester features remain pending.

Part 2a: Supabase PostgreSQL connection configuration/checker prepared on
14 October at the user's request. User subsequently reported the live connection
succeeded and authorized resuming database implementation. Part 2b supplies
tested account/audit/session schema and role setup; hosted migration pending.
Follow `docs/DatabaseMigrations.md`. This does not complete the full step 2 gate.

15 October update: V1 hosted metadata/permissions evidenced in user screenshots;
runtime connection success is user-reported. Part 3 supplies remaining schema
and opt-in demo/bootstrap initialization. Apply/verify via DatabaseCompletion.md.
The full step 2 exit still needs CI, hosted verification and deployment evidence;
authentication/Requester services remain pending. User .gitignore edits preserved.

This plan sequences existing requirements; it does not change them. The user
authorized implementation in small parts on 14 October 2026, after the original
planning-only request. Report completed work, remaining work and the next part
at each checkpoint; clarify material unresolved behavior before implementing it.

## Scope and sources

Deliver the Requester workspace and Smart Report Assistant, plus the assigned
shared authentication/access-control foundation. Complete code, persistence
integration, authorization tests, documentation, reuse records and reflection
evidence for this slice. Keep Technician and Manager business operations with
their owners; consume their agreed shared interfaces.

Authoritative sources: [Requester specification](specs/ROLE-REQUESTER.md),
[Product specification](specs/PRODUCT-SPEC.md),
[Technical specification](specs/TECHNICAL-SPEC.md),
[Integration specification](specs/INTEGRATION-SPEC.md),
[AI security specification](specs/AI-SECURITY-SPEC.md), and
[team plan](PLAN.md). Track evidence in [TRACEABILITY](TRACEABILITY.md).

Do not add self-registration, attachments, notifications, persistent AI chat,
autonomous actions or other excluded features. Reported Urgency belongs to the
Requester; Manager Priority is read-only for the Requester.

## Shared dependencies to settle first

| Dependency | Accountable owner | Requester needs |
| --- | --- | --- |
| Framework/build/deployment foundation | Team; deployment: yu-sutong | One agreed Spring scaffold, pinned versions, CI and a viable deployment path; no separate Requester app |
| PostgreSQL/Flyway, audit and transaction integration | yu-sutong | Accounts, categories, Requests, Requester Updates, versions, display IDs, sessions and atomic audit writes |
| Shared Request lifecycle/read models | Team, preserving slice ownership | Named operations and role-scoped results; shared validation, concurrency and safe error handling |
| Shared SoCLaaS gateway | ngkhengyang | Typed result/failure interface, deterministic fake adapter, limits, model readiness and redacted telemetry |
| Manager account administration | yu-sutong, using Requester-owned identity foundation | Deactivation/reset/role changes call shared session/access controls; no duplicate security implementations |

Agree interface responsibilities and the smallest needed contracts before
implementation. Keep schema changes in coordinated Flyway migrations. Do not
wait until final integration to discover incompatible contracts.

## Implementation order

Complete each numbered step's exit gate before relying on it in the next step.
Each feature step includes its focused tests; step 9 consolidates verification.

### 1. Confirm scope, contracts and reuse

- Inventory actual source/build state before coding. Current tracked `src/`
  contains only README; `bin/` and `build/` do not prove source implementation.
  Future application configuration belongs in source resources, not generated
  `bin/main/application.yml`.
- Map REQ-RQ-001 through REQ-RQ-017 and shared identity requirements to small PRs.
  Agree the dependencies above and record any design decisions.
- Confirm acceptance details for any material ambiguity, including the exact
  own-password-change interaction if existing specs do not resolve it. Update
  and review the relevant spec before implementing unresolved behavior.
- Compare selected MP2 logic/tests with the recorded baseline; enter each reused
  file/section in REUSE.md before merging. Replace JavaFX and SQLite as planned.
- Arrange early Vercel/Spring/session feasibility work with the deployment owner;
  follow TECHNICAL-SPEC's revision process if the approved hosting flow fails.

**Exit:** scoped task list, agreed dependency contracts and no material unresolved
behavior for the first implementation slice. Sources: NFR-DOC-001, NFR-DEP-001,
AI-SEC-024–027 and technical design acceptance.

### 2. Establish the shared walking skeleton

- Coordinate one Java/Spring/Gradle foundation with the team, module boundaries,
  basic templates, and isolated PostgreSQL test setup.
- Integrate agreed account/category/Request/audit/session migrations and local
  configuration. No secrets in tracked defaults; no developer/production DB in
  tests. Demo initialization is explicit and atomic; production bootstrap is
  separate and uses deployment secrets.
- Establish CI compilation, focused tests and scanning before feature PRs.
  Validate a minimal Preview deployment early with the deployment owner.

**Exit:** reproducible local startup, database connectivity/migrations, isolated
test execution and CI evidence. Shared team work, not Requester-only ownership.
Sources: REQ-SH-015–017, NFR-SEC-002, NFR-DEP-001, TECHNICAL-SPEC.

### 3. Implement authentication and access control

- Login/logout, adaptive salted password hashing, generic login failure for
  unknown/wrong-password/inactive Accounts, and role-based workspace routing.
- Spring Session JDBC; browser-session cookie with specified security flags;
  no inactivity timeout. Re-check persisted Account active state, role and
  session version on protected operations.
- Shared own-password-change flow retains the current session as specified.
  Logout, deactivation and Manager password reset invalidate affected sessions;
  eligible role changes use the persisted role on the next request. Provide
  integration support for Manager operations without building Manager screens.
- CSRF protection, encoded output, safe errors and required security headers.
  Account/session mutations and login success have the required audit behavior.
- Test all three roles, direct wrong-role access, disabled Accounts, old sessions,
  logout/reset/deactivation, role changes and invalid/missing CSRF. Verify
  account-administration guards with the Manager owner.

**Exit:** authenticated Requester reaches only its workspace; direct requests
cannot bypass role checks; session changes work across server executions.
Sources: REQ-SH-001–006, REQ-SH-017, NFR-SEC-001–002, TECHNICAL-SPEC.

### 4. Build the first manual slice: create, list and detail

- Add the shared authenticated shell with role/username, workspace, change
  password, about and logout; Requester navigation exposes only its operations.
- Create form: title, description, location, active category and Reported
  Urgency. Apply the approved Unicode length, trimming and enum rules server-side.
- Derive owner and Recording Actor from the authenticated Account; clients cannot
  set owner, status, assignment, Manager Priority, IDs or timestamps.
- Atomically create one OPEN Request with a database display ID and Audit Event;
  redirect/show the persisted result. Handle duplicate pending submission,
  repeated commands, display-ID exhaustion and storage/audit failure safely.
- Add an own-Request list and detail view with Requester-visible status history,
  reasons and updates. Exclude Work Logs, private Manager data and structured
  audit details from server results, including Requests created on the owner's
  behalf by a Manager.
- Field errors preserve recoverable input; denied foreign IDs reveal no record
  details/existence. Test two Requesters, tampered fields, min/max Unicode limits,
  invalid/inactive categories, duplicate submission and transaction rollback.

**Exit:** login → create → own list → detail works with persistence; cross-owner
reads/writes fail and audit failure leaves no orphan Request.
Sources: REQ-RQ-002, 005, 009–011; REQ-SH-006–007, 010, 012–014;
RQ-A01–03, A06–07.

### 5. Complete eligible edits, cancellation and updates

- Edit the five report fields only for an owned persisted OPEN Request.
- Cancel only an owned OPEN Request, with explicit confirmation and a 5–500
  code-point reason. Never hard-delete it.
- Append a 1–1,000 code-point Requester Update to owned non-terminal Requests,
  including COMPLETED awaiting review; reject CLOSED and CANCELLED.
- Re-read Account, ownership, state, version and active catalogue values inside
  ordinary use cases. Commit mutation plus audit atomically; stale pages must
  not overwrite newer work or bypass lifecycle restrictions.
- Preserve form input with corrective guidance on recoverable failures. Test
  Manager assignment/category changes between load and submit, concurrent edits,
  cancellation/update races, rollback and foreign/wrong-role commands.
- Prove Requesters cannot assign, reprioritize, start, complete, close, reopen or
  set raw status via forged HTTP requests.

**Exit:** every permitted Requester mutation works only in its specified state;
stale/forbidden operations produce no partial writes.
Sources: REQ-RQ-006–011; REQ-SH-006–007, 010–011, 014; RQ-A04–06.

### 6. Complete dashboard and discovery

- Dashboard: own status counts and recent Requests, including Manager-recorded
  Requests owned by this Requester.
- Search display ID/title/location with trimmed case-insensitive matching;
  filters for status, category and inclusive local creation dates.
- Default order: creation descending, then display ID ascending. Display UTC
  timestamps in an unambiguous local date/time/zone; date filter ends before the
  next local day. Add visible filters, one-action reset and distinct empty versus
  zero-result messages.
- Test counts/scope, ties, mixed-case search, date boundaries and combined filters.
  Check labels, keyboard access, focus, field errors and supported viewport sizes
  throughout steps 4–6 rather than postponing accessibility.

**Exit:** full manual Requester workflow is usable with AI entirely disabled.
Sources: REQ-RQ-001, 003–005, 009; REQ-SH-008–010;
NFR-A11Y-001–002, NFR-REL-001.

### 7. Add the Smart Report service against the fake gateway

- Use the shared gateway interface and fake adapter. Authorize active Requester
  create context or owned persisted OPEN edit context before collecting data.
- Send only current rough/form text, active category values and fixed field
  rules. No account lists, identifiers, other Requests, Work Logs or private data.
- Version the prompt and use the exact strict Smart Report JSON schema from
  INTEGRATION-SPEC: optional draft fields/category/urgency, 0–5 questions and
  rationale; require at least one suggestion or question. Reject unknown fields,
  invalid values, empty optional strings and excessive lengths.
- Integrate shared input/rate/concurrency checks before context/provider calls,
  CSRF, bounded deadline/retry, disclosure checks and redacted metadata. Re-check
  active role and object access after provider response. Re-read state/catalogues
  again at later business submission.
- Exercise successful, disabled, malformed, delayed, 400/401/403/429/503 and
  connection-failure results deterministically; denied access makes zero calls.

**Exit:** authorized typed suggestions or safe categorized failures, with zero
business mutations from AI. Sources: REQ-RQ-012–014, 016–017; REQ-SH-018;
REQ-AI-001–010; AI-SEC-001–011, 013–022, 028–030; RQ-AI02–03.

### 8. Add AI review UI and live SoCLaaS integration

- Embed the assistant in create/eligible edit forms. Show an explicitly labelled
  suggestion panel separate from the business form. Accept individual fields,
  edit accepted values or discard all; never auto-save or auto-submit.
- Show questions/rationale safely, loading/limit/error feedback and a usable
  manual path without losing form input. A late response must not silently
  overwrite current edits; applying a suggestion requires the user's action.
- Submit through existing create/edit operations with normal validation and
  authorization. Reject forged AI acceptance/result metadata as authority.
- With the gateway owner, validate server-only configuration/model discovery and
  a small synthetic-data live SoCLaaS smoke check in non-production. Record safe
  token/latency/status metadata, never raw prompts/output or API keys.

**Exit:** rough report → suggestion review → explicit ordinary submission works;
discarding/failing AI produces no Request mutation and preserves manual input.
Sources: REQ-RQ-015–017, REQ-SH-018, NFR-REL-001; RQ-AI01–03;
AI-SEC-013–020, 023, 028–030.

### 9. Verify integration, security and usability

- With teammates, run create → Manager assignment → Technician start/log/complete
  → Manager close. Verify Requester views after each state, return/reassignment,
  reopen, cancellation and Manager creation on behalf; private data stays hidden.
- Complete RQ-A01–A07 and RQ-AI01–AI03, persistence/restart, audit rollback,
  duplicate-click, stale-state and authorization evidence.
- Run Requester-relevant AIS-T01–08, T10–11, T13–18: direct/indirect/obfuscated
  injection, cross-object/role denial, malformed/oversized output, approval
  bypass, limits/retries, redaction, poisoned handoffs, access revocation during
  inference, invalid CSRF and hidden-context canary leakage. T09 and T12 belong
  to the other role features; collaborate on shared controls.
- Maintain a small versioned AI evaluation set with normal, ambiguous, boundary,
  failure and attack cases. Record useful-draft assessment separately from
  deterministic safety/schema results; do not claim deterministic tests prove
  model immunity to injection.
- Verify critical flows in current stable desktop Chrome, keyboard/focus/errors,
  contrast and specified viewports. Include authenticated Requester paths in the
  team's 50-user non-AI p95 ≤1 second performance check.

**Exit:** executed requirement-linked evidence, no unresolved blocking defects,
and teammate review. Fixes after failed tests/security findings get a fresh
verifier; do not weaken tests to pass. Sources: all REQ-RQ IDs, relevant shared
and AI controls, NFR-PERF-001, NFR-A11Y-001–002, AI-SEC-027.

### 10. Finish documentation and development evidence

- Update Requester/login/password sections of UserGuide to match tested behavior,
  including safe access/testing instructions, allowed states and AI fallback.
- Update DeveloperGuide for identity/session/access controls, Requester use cases,
  transactions, schemas, trust seams and actual test/setup commands.
- Split TRACEABILITY ranges into precise source/test/evidence entries for the
  implemented IDs. Finalize measured MP2 reuse and acknowledgements.
- Add concrete individual reflection examples for AI security, SDD and specialized
  agents. Record actual scoped analyst/developer/tester/reviewer work and handoffs;
  definitions alone do not establish multi-agent usage. Sequential sessions are
  sufficient; no runtime agent framework is required.
- Keep logs/handoffs sanitized and honestly mark pending human review. Contribute
  Requester content/screenshots to the team's GitHub Pages product site.

**Exit:** docs and reflection match the tested candidate; requirements have
implementation and executed evidence, reuse is quantified and teammate reviewed.
Sources: NFR-DOC-001, AI-SEC-024–027, assignment submission requirements.

### 11. Verify the deployed release and complete the slice

- With yu-sutong, smoke-test login, Requester create/list/detail/edit/update/cancel,
  password/logout, ownership denial and AI fallback on the tested release
  candidate. Verify database durability and distinct Preview/Production secrets
  and data; never use production data for attack tests.
- Obtain teammate review and passing required CI before merge. A human promotes
  the smoke-tested candidate under the approved deployment flow.
- Confirm Requester behavior on the promoted deployment. Collect team evidence
  for backup restore, rollback, health/monitoring and public app/product-site links.
- Confirm reviewed code/docs/evidence are on public master before 23 October 2026,
  14:00 SGT. Freeze changes after submission as the assignment requires.

**Exit:** Requester features and shared identity responsibilities are verified
online, with evidence and review. A locally working Requester slice alone does
not establish whole-product completion. Sources: NFR-DEP-001, NFR-REL-002,
NFR-OPS-001–002, technical deployment acceptance and assignment requirements.

## Work packaging and completion checklist

Prefer one reviewable PR per numbered implementation step, splitting shared
foundation work by the agreed owner when necessary. Include requirement IDs,
focused tests, traceability/docs/reuse updates and a sanitized session record in
each PR. Step 9 adds integration evidence; it does not delay earlier tests.

- [ ] Dependencies and material ambiguities resolved; plan reviewed.
- [ ] Shared authentication, sessions and access controls verified for all roles.
- [ ] Dashboard, list/search/filters, create, detail, edit, cancellation, updates,
  password change, about and logout complete.
- [ ] Own-record scope and Requester history privacy verified server-side.
- [ ] Smart Report Assistant works with fake and live SoCLaaS; advisory only.
- [ ] Validation, stale-state, rollback, authorization, AI failure/security,
  browser/accessibility and performance evidence recorded.
- [ ] Guides, traceability, reuse, logs, handoffs and individual reflection current.
- [ ] Teammate review, CI and deployed-release checks complete on public master.

All completion boxes remain unchecked: Part 1 is a build scaffold, not the
complete walking skeleton. See the Part 1 log for executed evidence. No commits,
deployment or changes to `MP3 instructions/` have been performed.
