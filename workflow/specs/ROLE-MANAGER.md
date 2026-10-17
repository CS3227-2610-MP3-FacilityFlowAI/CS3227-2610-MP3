# Facilities Manager Specification

Status: approved baseline v0.1, 12 October 2026. Owner: yu-sutong. Gateway
revision candidate v0.2 received stakeholder approval on 16 October 2026;
Manager role-owner and teammate review of the revision remains pending. This is
not an implementation or verification claim. Shared rules are in
[PRODUCT-SPEC](PRODUCT-SPEC.md).

## Objective and screens

The Facilities Manager controls triage and lifecycle operations, administers
Accounts and catalogues, reviews work, and explains actions through audit history.
Required routes are overview, all-Request queue/detail, record-on-behalf,
triage/assignment, completed-work review, Accounts, Category Catalogue, Skill
Catalogue/Technician skills, audit viewer, change password, and Triage Assistant.

## Request and review requirements

- **REQ-MGR-001:** Managers may view every Request and its complete permitted
  history. Search covers display ID, title, location, Requester and Technician;
  filters cover status, category, priority, Technician and creation dates.
- **REQ-MGR-002:** Default queue order is OPEN first by reported urgency
  Emergency to Low then oldest creation; active work next by Manager priority
  Critical to Low then oldest update; terminal/review states last by newest
  update; display ID breaks ties.
- **REQ-MGR-003:** A Manager may record a valid OPEN Request for an existing
  Requester; that Requester is owner and the Manager is Recording Actor.
- **REQ-MGR-004:** A Manager shall set Manager Priority and choose one active
  Technician when assigning OPEN to ASSIGNED.
- **REQ-MGR-005:** A Manager may reassign ASSIGNED or IN_PROGRESS to a different
  active Technician with a reason, returning it to ASSIGNED while preserving
  Work Logs and authorship.
- **REQ-MGR-006:** A Manager may cancel OPEN, ASSIGNED or IN_PROGRESS with a reason.
- **REQ-MGR-007:** After reviewing Work Logs and Resolution Summary, a Manager may
  confirm and accept COMPLETED to CLOSED or return it to ASSIGNED with an active
  Technician and reason.
- **REQ-MGR-008:** A Manager may reopen CLOSED to ASSIGNED with an active
  Technician and reason.
- **REQ-MGR-009:** A Manager may correct title, description, location, category,
  reported urgency and Manager priority in any state without changing ownership,
  status, history, assignment history, completion data, IDs or historical
  timestamps. A successful correction advances the system-managed `updatedAt`
  timestamp and records an Audit Event; callers cannot set timestamps directly.
- **REQ-MGR-010:** The overview shows counts by status and priority and each active
  Technician's workload, defined as ASSIGNED plus IN_PROGRESS Requests.
- **REQ-MGR-011:** The audit viewer supports Request, actor, action and date-range
  filters and is read-only.
- **REQ-MGR-012:** Managers use named lifecycle operations and shall never edit a
  raw status value.

## Account administration

- **REQ-MGR-013:** A Manager may create Accounts for any role, reactivate or
  deactivate Accounts, change another Account's role and reset another Account's
  password, subject to shared validation and Audit Events.
- **REQ-MGR-014:** A Manager may not deactivate or change the role of their own
  current Account, reset their own password through the Manager reset operation,
  or leave zero active Managers.
- **REQ-MGR-015:** A Technician with ASSIGNED or IN_PROGRESS work cannot be
  deactivated or changed to another role until every active Request is reassigned
  or cancelled. An inactive Technician cannot receive or retain an active
  assignment. Non-Technician Accounts cannot receive assignments.

## Category and skill administration

- **REQ-MGR-016:** Managers may create and rename categories in a specialized
  settings page. Names are trimmed, case-insensitively unique, 2–50 code points,
  and cannot be blank.
- **REQ-MGR-017:** Removing a category requires selection of a different active
  replacement. Existing Requests migrate to the replacement atomically with
  Audit Events; failure leaves catalogue and Requests unchanged. Other cannot be
  removed unless another active category is designated as replacement.
- **REQ-MGR-018:** Managers may create, rename and deactivate Skill Catalogue tags.
  Tags are trimmed, case-insensitively unique, 2–50 code points.
- **REQ-MGR-019:** Each active Technician may have zero or more active skill tags.
  Updates are audited. Deactivating a tag removes it from future selection but
  preserves historical AI/audit metadata by immutable tag ID and recorded label.

## Triage Assistant and deterministic candidate ranking

The LLM recommends triage facts; the application ranks candidates; the Manager
makes every final decision.

- **REQ-MGR-020:** Triage is available to a Manager for an OPEN Request and when
  reconsidering category, priority or assignment for an otherwise eligible
  non-terminal Request. It is unavailable for CANCELLED; CLOSED requires reopen
  before assignment work.
- **REQ-MGR-021:** Model context contains only the selected Request's title,
  description, location, current category, reported urgency, Manager priority
  and status; at most the latest ten Requester Updates; active category labels;
  active skill labels; and fixed output rules. Select Updates by creation time
  descending then immutable ID descending and send the selected set
  oldest-to-newest. Sort active labels case-insensitively then by immutable ID,
  but never send the IDs. It excludes Request/Account internal or display IDs,
  Technician identities, workloads, passwords, unrelated Requests and mutation
  capabilities. Oversize context is rejected rather than silently truncated
  beyond the specified latest-ten Update selection.
- **REQ-MGR-022:** The typed result contains suggested category, Manager priority,
  clarification questions, required skill tags, optional skill tags and rationale
  as specified in [INTEGRATION-SPEC](INTEGRATION-SPEC.md).
- **REQ-MGR-023:** After valid AI output, the deterministic application ranks only
  active Technicians who contain every required tag. Rank by: more optional tags
  matched; lower workload under REQ-MGR-010; case-insensitive username; immutable
  Account ID. If no Technician has every required tag, show no candidate and
  explain that the Manager must choose manually or adjust accepted tags.
- **REQ-MGR-024:** The Manager confirms, edits or rejects category, priority and
  skill recommendations individually. Candidate ranking is labelled as a
  suggestion. No Technician is assigned until the Manager explicitly chooses
  one and submits the ordinary authorized assignment operation.
- **REQ-MGR-025:** Saving triage re-reads current state and active Account/catalogue
  data. Stale recommendations, AI failure, malformed output or no candidate never
  block manual triage and never change a Request. Before displaying an AI result,
  the server also rechecks Account/role/object access, Request state/version and
  category/skill catalogue freshness. Any change, including a now-inactive
  suggested value, discards the result as `DISCARDED_ACCESS_CHANGED`; it is never
  recorded successful.

## Acceptance scenarios

| ID | Given / when | Required result | Forbidden result |
| --- | --- | --- | --- |
| MGR-A01 | OPEN + active Technician + priority | atomic ASSIGNED transition/audit | partial assignment |
| MGR-A02 | inactive/non-Technician selected | field error; unchanged Request | assignment |
| MGR-A03 | IN_PROGRESS reassigned with reason | ASSIGNED to new Technician; history preserved | former Technician write |
| MGR-A04 | COMPLETED accepted | CLOSED with completion retained | direct raw status edit |
| MGR-A05 | COMPLETED returned | ASSIGNED with reason/active Technician | erased completion history |
| MGR-A06 | last active Manager targeted | rejection | zero active Managers |
| MGR-A07 | category removed with replacement | atomic migration and audits | orphan/invalid category |
| MGR-A08 | AI recommends skills | deterministic candidate order, Manager choice | LLM or ranker assigns |
| MGR-A09 | injected Request asks for secrets/actions | safe schema/rejection | data disclosure or mutation |
| MGR-A10 | concurrent triage changed state/catalogue | stale save rejected/refresh | obsolete recommendation applied |
| MGR-A11 | Request state/version or category/skill catalogue changes in flight | entire result discarded with safe refresh guidance | stale suggestion displayed or recorded successful |

Tests cover every lifecycle operation, account guard, catalogue migration
rollback, tag management, exact ranking/ties, no-match behavior, role/object
authorization, prompt injection, malformed output and AI fallback.
