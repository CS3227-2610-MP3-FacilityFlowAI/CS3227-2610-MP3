# Technician Specification

Status: approved v0.1, 12 October 2026. Owner: ngkhengyang. Required teammate
review has been confirmed. Shared rules are in [PRODUCT-SPEC](PRODUCT-SPEC.md).

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
- **REQ-TECH-013:** Context is limited to that Request's report fields, category,
  urgencies/priorities, status, Requester Updates visible to the Technician, and
  approved static safety/playbook excerpts selected by the application. It
  excludes other Requests, accounts, credentials, audit data and model tools.
  Playbooks are source-controlled, versioned, human-reviewed read-only resources;
  if no applicable approved excerpt exists, the model receives none.
- **REQ-TECH-014:** The typed result follows the Work Plan schema in
  [INTEGRATION-SPEC](INTEGRATION-SPEC.md) and is displayed as read-only advice.
- **REQ-TECH-015:** The Technician chooses which steps to follow. Advice does not
  start work, create Work Logs, complete work, reserve tools or change any record.
- **REQ-TECH-016:** Safety advice shall prominently show stop/escalation items.
  Empty or unsafe safety output is rejected rather than presented as authoritative.
- **REQ-TECH-017:** AI failure or safe rejection leaves all manual Technician
  operations available and stores no partial plan or business change.

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

Tests cover queue scope/order, transitions, append-only behavior, stale
assignment, indirect injection through stored text/playbooks, unsafe output,
timeout/rate limit and core-workflow fallback.
