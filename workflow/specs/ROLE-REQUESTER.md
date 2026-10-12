# Requester Specification

Status: approved v0.1, 12 October 2026. Owner: yooplo. Required teammate review
has been confirmed. Shared fields, lifecycle, UI and quality rules are in
[PRODUCT-SPEC](PRODUCT-SPEC.md) and are not repeated here.

## Objective and screens

The Requester reports a facilities problem and follows its progress without
seeing another Requester's data or internal maintenance notes.

Required routes are dashboard, own-Request list, create form, own-Request detail,
eligible edit form, change password, and Report Assistant embedded in create/edit.

## Manual workflow requirements

- **REQ-RQ-001:** The dashboard shows status counts and recent Requests, and the
  dashboard/list contain only Requests owned by the authenticated Requester,
  including Requests recorded by a Manager on their behalf.
- **REQ-RQ-002:** The Requester may create a valid Request. Success creates exactly
  one audited OPEN Request owned by and recorded by that Requester.
- **REQ-RQ-003:** Own-Request search covers display ID, title and location.
  Filters cover status, category and inclusive creation-date range.
- **REQ-RQ-004:** The default list order is creation time descending, then display
  ID ascending for ties.
- **REQ-RQ-005:** Detail shows Request fields, status, reported urgency, Manager
  priority when present, created/updated times and Requester-visible history.
- **REQ-RQ-006:** The Requester may edit title, description, location, category
  and reported urgency only while its persisted status is OPEN.
- **REQ-RQ-007:** The Requester may cancel only an owned OPEN Request after
  confirmation and a valid reason.
- **REQ-RQ-008:** The Requester may add a Requester Update while the Request is not
  CLOSED or CANCELLED.
- **REQ-RQ-009:** Requester reads shall exclude Work Logs, private Manager data,
  structured audit details and other Requesters' Request existence/details at
  the server result, not merely in the page.
- **REQ-RQ-010:** The Requester shall never assign, reprioritize, start, complete,
  close, reopen or directly set status.
- **REQ-RQ-011:** On validation, stale-state, authorization or persistence failure,
  no partial write occurs; recoverable form input remains and safe corrective or
  retry guidance is shown.

## Smart Report Assistant

The assistant converts a rough problem description into a temporary structured
draft, suggests category and reported urgency, and asks for missing information.

- **REQ-RQ-012:** It is available only to an authenticated active Requester while
  creating a Request or editing an owned persisted OPEN Request.
- **REQ-RQ-013:** Its model context contains only the Requester's current rough
  text/form fields, active category values and the fixed field rules needed to
  construct the draft. It contains no account list, other Requests, Work Logs,
  Audit Events or Manager/Technician private data.
- **REQ-RQ-014:** Its typed result contains optional draft title, description and
  location; optional suggested category and urgency; zero to five missing-
  information questions; and a short rationale. Exact schema and limits are in
  [INTEGRATION-SPEC](INTEGRATION-SPEC.md).
- **REQ-RQ-015:** The result is displayed as an AI suggestion, separate from the
  persisted Request form. The Requester may accept individual suggestions, edit
  them or discard all of them. Nothing is saved automatically.
- **REQ-RQ-016:** Submission always uses the ordinary Request creation/update
  operation and repeats deterministic validation, ownership and OPEN-state
  checks. AI output is never authorization or trusted validation.
- **REQ-RQ-017:** Missing, malformed, unsafe, timed-out or rate-limited AI output
  produces a safe status message and leaves the manual form and its current input
  usable.

## Acceptance scenarios

| ID | Given / when | Required result | Forbidden result |
| --- | --- | --- | --- |
| RQ-A01 | Requester submits valid fields | one OPEN Request and Audit Event; visible in own list | duplicate or partial write |
| RQ-A02 | required/length rule fails | field-level error and retained input | Request or Audit Event created |
| RQ-A03 | A requests B's known display ID | safe denial, unchanged state | title, description or existence leaked |
| RQ-A04 | owner edits OPEN then attempts after ASSIGNED | OPEN edit audited; later edit rejected | stale page bypass |
| RQ-A05 | owner confirms OPEN cancellation with reason | CANCELLED and audited reason visible | later Requester writes |
| RQ-A06 | storage/audit insertion fails | complete rollback, retained input, retry possible | success message or orphan row |
| RQ-A07 | Manager recorded Request for owner | same own visibility and OPEN rights | Manager becomes owner |
| RQ-AI01 | valid rough text returns typed AI result | review panel only; accepted fields copied to form | automatic submission |
| RQ-AI02 | rough text contains prompt-injection instructions | treated as Request data; bounded schema or safe rejection | disclosure, tools or broader context |
| RQ-AI03 | AI is unavailable or malformed | manual form remains usable | draft loss or business mutation |

Tests cover normal, boundary, ownership, stale-state, rollback, duplicate-click,
direct/obfuscated injection, malformed output and AI failure cases.
