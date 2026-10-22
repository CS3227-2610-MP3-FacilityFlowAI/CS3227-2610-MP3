# Technician Specification

Status: approved v0.2, 22 October 2026. Owner: ngkhengyang. Outstanding human
approval, including the existing Work Plan safety policy and regression
examples, is resolved. Execution evidence is separate from approval; see
[current acceptance](../../docs/Acceptance.md). Shared rules are in
[PRODUCT-SPEC](PRODUCT-SPEC.md).

## Objective and screens

The Technician works from a personal assigned queue, records accountable
progress, and submits completed work for Manager review without seeing unrelated
work. Required routes are dashboard/queue, assigned-Request detail, start-work,
Work Log, completion, change password, and Work Plan Assistant.

## Manual workflow requirements

- **REQ-TECH-001:** Dashboard counts distinguish ASSIGNED, IN_PROGRESS and
  COMPLETED-awaiting-review, and dashboard, queue and reads contain only Requests
  currently assigned to the authenticated Technician.
- **REQ-TECH-002:** Search covers display ID, title and location. Filters cover
  status, category and Manager priority.
- **REQ-TECH-003:** Default order is Manager priority Critical to Low, reported
  urgency Emergency to Low, oldest assignment time, then display ID. Unknown
  migrated assignment times follow known times until the next assignment.
- **REQ-TECH-004:** The assigned Technician may explicitly transition ASSIGNED to
  IN_PROGRESS.
- **REQ-TECH-005:** Only the currently assigned Technician may append a Work Log,
  and only while persisted state is IN_PROGRESS.
- **REQ-TECH-006:** Work Logs are append-only, preserve author/time and follow the
  shared note/minute limits.
- **REQ-TECH-007:** The assigned Technician may transition IN_PROGRESS to COMPLETED
  only when at least one Work Log exists and a valid Resolution Summary is given.
- **REQ-TECH-008:** Completion retains the Technician assignment and records
  completion metadata for Manager review.
- **REQ-TECH-009:** The Technician may read Requester Updates for assigned work but
  may not access unrelated Requests or another Technician's Work Logs unless the
  Request is later assigned to them; historical authorship remains.
- **REQ-TECH-010:** A former Technician's next write after reassignment shall fail
  safely and refresh current state.
- **REQ-TECH-011:** A Technician shall never assign/reassign, reprioritize, edit
  original Request fields, cancel, close, reopen or directly set status.

## Work Plan Assistant

The assistant proposes diagnostic steps, safety precautions, tools, clarification
questions, evidence to collect, and stop/escalation conditions.

- **REQ-TECH-012:** It is available only to the active Technician currently
  assigned to a persisted Request whose state is ASSIGNED or IN_PROGRESS.
- **REQ-TECH-013:** Context is limited to that Request's title, description,
  location, category, reported urgency, Manager priority and status.
  It also contains at most the latest ten Requester Updates visible to the
  Technician. Select Updates by creation time descending then immutable ID
  descending and send the selected set oldest-to-newest. It excludes Work Logs,
  Request/Account internal or display IDs, other Requests, accounts, credentials,
  audit data and model tools. The initial revision sends no playbook excerpt.
  Oversize context is rejected rather than silently truncated beyond the
  specified latest-ten Update selection.
- **REQ-TECH-014:** The typed result follows the Work Plan schema in
  [INTEGRATION-SPEC](INTEGRATION-SPEC.md) and is displayed as read-only advice.
- **REQ-TECH-015:** The Technician chooses which steps to follow. Advice does not
  start work, create Work Logs, complete work, reserve tools or change any record.
- **REQ-TECH-016:** Safety advice shall prominently show stop/escalation items.
  Empty or unsafe safety output is rejected rather than presented as authoritative.
- **REQ-TECH-017:** AI failure or safe rejection leaves all manual Technician
  operations available and stores no partial plan or business change. Before
  display, the server rechecks current Account/role, assignment, ASSIGNED or
  IN_PROGRESS state and Request version. Any change discards the result as
  `DISCARDED_ACCESS_CHANGED`; it is never recorded successful.

## Acceptance scenarios

| ID | Given / when | Required result | Forbidden result |
| --- | --- | --- | --- |
| TECH-A01 | assigned Technician starts ASSIGNED work | IN_PROGRESS plus Audit Event | other Technician starts it |
| TECH-A02 | Technician requests unrelated display ID | safe denial and unchanged state | details disclosed |
| TECH-A03 | assigned Technician logs valid work | one append-only Work Log | overwrite/delete |
| TECH-A04 | completion has no Work Log | actionable rejection; remains IN_PROGRESS | COMPLETED state |
| TECH-A05 | valid Work Log and resolution | COMPLETED with retained assignment/audit | automatic closure |
| TECH-A06 | Manager reassigns before stale write | write rejected and view refreshed | former assignee mutation |
| TECH-AI01 | eligible assigned Request requests plan | bounded read-only structured plan | state/log changes |
| TECH-AI02 | stored Request/Update says to ignore instructions | content remains data; safe schema/rejection | instruction hierarchy changed |
| TECH-AI03 | unsafe or missing safety section | result rejected with manual fallback | advice shown as approved procedure |
| TECH-AI04 | assignment/status/Request version changes in flight | entire result discarded with safe refresh guidance | stale plan displayed or recorded successful |

Tests cover queue scope/order, transitions, append-only behavior, stale
assignment, indirect injection through stored text/playbooks, unsafe output,
timeout/rate limit and core-workflow fallback.
