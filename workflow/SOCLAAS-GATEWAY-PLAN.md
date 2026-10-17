# SoCLaaS Gateway Pre-implementation Plan

Status: historical pre-implementation architecture plan, 16 October 2026. The
plan itself authorized no implementation; subsequent approved increments 2A–2F
now provide the provider core, HTTPS adapter, lifecycle wiring, the first
Technician Work Plan slice and durable distributed admission. Retry, telemetry
and compressed-response work remain subsequent increments.

This plan sequences the approved requirements in `workflow/specs/` without
changing them. Material behavior that the approved v0.1 specifications do not
resolve is listed as a human decision gate. The Analyst, Architect and Security
Reviewer roles were invoked for this planning phase; their sanitized handoffs
are under `logs/handoffs/2026-10-16-soclaas-gateway-*-handoff.md`.

## Scope and source requirements

The shared gateway is governed by REQ-SH-018, REQ-AI-001–010 and
AI-SEC-001–030. Role eligibility and context remain governed by
REQ-RQ-012–017, REQ-TECH-012–017 and REQ-MGR-020–025. The approved
`PRODUCT-SPEC`, `TECHNICAL-SPEC`, `INTEGRATION-SPEC`, `AI-SECURITY-SPEC` and
role specifications remain authoritative.

The phase stops after Increment S5 and human architecture review. Application
code, automated tests, `.env.example`, `application.yml`, database migrations
and deployment configuration remain unchanged.

## Corrected module shape

The `ai` module is deep: role callers receive typed advisory operations and safe
failure categories while configuration, provider envelopes, bearer
authentication, prompt construction, readiness, deadlines, retries, limits,
strict parsing, safety/disclosure checks, usage extraction and telemetry remain
inside the implementation.

The external seam is feature-specific rather than a public generic gateway:

```text
SmartReportAssistant.suggest(current principal, requester input/reference)
WorkPlanAssistant.suggest(current principal, Request reference)
TriageAssistant.suggest(current principal, Request reference)
```

Exact parameters remain part of Increment S1. Actor identity and correlation ID
are server-derived. Callers cannot select a template, parser, model, feature
policy or arbitrary context map.

Behind this seam, an internal provider coordinator uses:

- a role-owned minimal persisted eligibility probe;
- a lazy allowlisted context loader, called only after local admission;
- a freshness snapshot and mandatory post-provider eligibility recheck;
- a closed, source-controlled feature definition;
- an internal SoCLaaS client seam with HTTPS and deterministic fake adapters;
- PostgreSQL-backed Account-rate and global-slot adapters;
- strict feature parsers, Work Plan safety policy and disclosure policy;
- metadata-only invocation telemetry.

The implementation must enforce this order:

1. POST, CSRF and current persisted active Account/role.
2. Cheap browser-input limits and server-generated correlation ID.
3. Minimal persisted object/feature eligibility probe.
4. Valid configuration plus atomic Account-rate/global-slot admission.
5. Lazy allowlisted context load and freshness snapshot.
6. Normalization, JSON serialization and field/aggregate prompt limits.
7. Bounded configured-model readiness.
8. Fixed system message plus one structured untrusted-data message; no tools,
   history, retrieval or response persistence.
9. One original deadline over the permitted provider attempt and retry.
10. Bounded provider-envelope decoding and token extraction.
11. Strict feature parsing, safety and disclosure checks.
12. Fresh Account/role/object/state/catalogue eligibility recheck.
13. Durable terminal disposition/usage recording and fenced lease release.
14. Only a validated typed, encoded and non-cacheable suggestion is returned.

Denial before step 5 performs zero protected context loads and zero provider or
readiness calls. A late result is discarded after any failed step-12 recheck.
No AI path calls a business mutation.

## Iterative increments

Each increment is specification or architecture work only. Its exit gate must be
reviewed before the next increment starts.

### S1 — External seam, authorization plan and terminal outcomes

Produce:

- the three feature-specific interfaces and a closed internal feature matrix;
- server-derived principal/correlation rules;
- mandatory eligibility probe, lazy context loader, freshness snapshot and
  post-provider recheck contracts;
- a complete result/disposition model, including access/state change after
  provider use.

Exit gate:

- no eager protected context or caller-selected feature/actor crosses the seam;
- denial and in-flight revocation traces have one defined terminal outcome;
- role owners confirm responsibility for their eligibility probes;
- any required `ai_invocation` status/schema revision is approved before coding.

### S2 — Exact role context and prompt-input contract

Produce:

- exact per-feature field allowlists and typed context records;
- Requester Update selection, ordering, count and overflow behavior;
- state/catalogue freshness rules;
- approved playbook identifier/version/selection rules, or an explicit rule that
  no playbook excerpt is supplied;
- Unicode normalization, delimiter encoding and code-point counting rules;
- distinct early browser-body and post-context serialized-prompt limits.

Exit gate:

- each role owner approves its context projection and eligibility states;
- 12,000/12,001-character examples have deterministic outcomes;
- no identifier, Account data, unrelated Request, Audit Event, session, secret
  or unapproved playbook can enter context;
- oversize input is rejected rather than silently truncated.

### S3 — Configuration, egress, readiness and provider transport

Produce:

- the complete environment-variable contract with `AI_ENABLED` defaulting to
  false, safe parsing/ranges and mandatory hard maxima;
- canonical construction of `/v1/models` and `/v1/chat/completions`;
- HTTPS/origin/userinfo/query/fragment/redirect/TLS policy;
- bounded response-body/decompression policy;
- readiness state machine and single-flight recovery behavior;
- accepted provider envelope, failure mapping, original-deadline and retry rules.

Exit gate:

- deployment configuration can only preserve or tighten the 30-second,
  800-token, five-in-flight, five-per-minute and 30-per-hour maxima;
- invalid/placeholder/missing configuration makes AI unavailable without loading
  protected context or blocking core startup;
- bearer credentials cannot cross an origin or redirect;
- every synthetic provider condition in REQ-AI-006 has a planned outcome.

### S4 — Prompt, parsing, safety and disclosure

Produce:

- versioned two-message templates for all three features;
- strict JSON/envelope/schema behavior, including duplicate keys, trailing
  content, null/absent values, catalogue snapshots and truncated output;
- exact plain-text, control-character, HTML and Markdown handling;
- a human-reviewed Work Plan safety policy and multilingual/obfuscated corpus;
- prompt delimiter, known-secret, unauthorized-object and protected-identifier
  disclosure canaries/deny rules.

Exit gate:

- role owner and Security Reviewer review each template and output schema;
- schema-valid unsafe or disclosing output is rejected as a whole;
- AI-SEC-012 is not reduced to keyword matching;
- all planned cases have expected results but are not described as executed.

### S5 — Distributed limits, leases and telemetry lifecycle

Produce:

- the exact logical-invocation/provider-attempt accounting rules;
- PostgreSQL-time rolling-window admission and transaction behavior;
- fenced global leases, expiry, renewal, loss, cancellation and stale-release
  rules across multiple application instances;
- telemetry lifecycle/status model for local rejection, provider use, retry,
  timeout, invalid output, cancellation and post-call access discard;
- explicit telemetry-persistence failure behavior and sanitized fallback logging.

Exit gate:

- concurrent, retry, process-death, lease-expiry and database-failure examples
  each have one deterministic result;
- a stale owner cannot release another invocation's re-leased slot;
- provider usage cannot be mislabeled `SUCCEEDED` after output is discarded;
- schema changes or residual risks receive the required human disposition;
- the complete S1–S5 architecture is approved before implementation begins.

## Human decisions required before implementation

1. Exact normalization and serialized character-count rule.
2. Exact per-feature Request fields and Requester Update selection/order/limits.
3. Approved playbooks, or confirmation that the initial Work Plan sends none.
4. `Retry-After`, retry delay and minimum remaining-deadline rules.
5. Model-readiness retry/cache/single-flight behavior.
6. Whether Account limits count logical invocations, provider attempts or both.
7. Global-slot acquisition span, lease duration, renewal and loss behavior.
8. Telemetry persistence failure behavior.
9. A terminal status for access/state change after provider use.
10. Exact accepted OpenAI-compatible response envelope and body-size limit.
11. Plain-text/active-markup rejection rules.
12. Work Plan safety detector and reviewed corpus.
13. Disclosure deny material beyond known secrets, delimiters and test canaries.
14. Whether display-time catalogue/state changes invalidate an otherwise
    authorized suggestion.

Answers that change behavior or storage must update the authoritative
specification or migration design and receive the human review required by
AI-SEC-024. Agent output and this plan do not constitute that approval.

## Planned security and contract evidence

The later verification specification must include wrong-role/cross-object and
in-flight-revocation zero-call tests; forged actor/feature/correlation inputs;
configuration/redirect/TLS attacks; multi-instance limit and lease recovery;
all retry/deadline cases; exact prompt-size boundaries; malformed/oversized
provider envelopes; strict JSON/schema/markup cases; Work Plan safety corpus;
disclosure canaries; telemetry failures; CSRF; non-cacheable responses; and
proof that AI code never calls business mutation repositories.

These are planned cases, not executed evidence.
