# T5 browser acceptance

Status: **browser execution blocked/pending**. On resumed verification, browser
discovery returned “Local browser use is disabled in Work settings”. No browser
was opened. Automated MVC/DOM tests do not satisfy these browser items; the user
was notified.

Requirement scope: REQ-SH-004/009/010/012/014/018, NFR-SEC-001/002,
NFR-A11Y-001/002, NFR-REL-001. Verify on the latest stable desktop Google
Chrome at execution time and record the exact version and date before results.

| Viewport | Result | Evidence | Status |
| --- | --- | --- | --- |
| 760 × 600 | Pending | — | Not executed |
| 1,024 × 700 | Pending | — | Not executed |
| 1,440 × 900 | Pending | — | Not executed |

## Keyboard and manual workflows

- [ ] Load login with JavaScript disabled; labels, validation feedback, CSRF
  submission and login remain usable.
- [ ] Sign in as a synthetic Technician; use keyboard only to move through the
  shared header, skip link, queue, request detail, work log and Work Plan
  controls. Confirm visible focus and sensible tab order.
- [ ] Complete the Technician lifecycle from queue through start, work-log
  entry and completion. Confirm the completed Request remains assigned to the
  Technician and requesting or discarding Work Plan advice never mutates
  assignment, priority, status or work-log history.
- [ ] Attempt direct access to an unassigned Request, then repeat after its
  assignment is moved away. Confirm both requests are rejected and no Request
  details are disclosed; confirm a stale Technician command is rejected without
  changing business state.
- [ ] Attempt forged Technician requests to assign work, reprioritize a Request
  and close it, then submit a state-changing request without its CSRF token.
  Confirm each forbidden or missing-CSRF action is rejected and leaves the
  Request, assignment, priority, status and audit state unchanged.
- [ ] Complete the manual work-log flow without requesting AI. Submit a manual
  log while an AI request is pending; verify the AI request disables until the
  refreshed page, pending advice is cleared and the manual draft is submitted.
- [ ] Request Work Plan advice, discard it, request again, then navigate away
  and use browser back/forward. Confirm stale or aborted advice never returns,
  controls recover after pageshow, and the manual workflow remains usable after
  network, timeout, malformed-response and service failures.
- [ ] Change the current user's own password using wrong current password,
  mismatched confirmation, valid 8-code-point, valid 24-code-point, and
  supplementary-character examples. Confirm password fields clear on failure,
  errors are associated with fields, success is shown after the fixed redirect,
  and the current session continues to work.
- [ ] Check that no view, browser history entry, or visible form value exposes a
  stored hash or a submitted password after success or failure.
- [ ] Navigate to About and logout using keyboard controls.

## Layout and accessibility

- [ ] At each viewport, confirm content wraps without horizontal page scrolling
  and all controls remain reachable.
- [ ] Confirm required fields have programmatic labels, errors are announced or
  associated, focus is visible, errors and pending states do not rely on color,
  and normal text meets 4.5:1 contrast.
- [ ] Confirm header identity, role and workspace navigation match the signed-in
  role; role-irrelevant links and controls are absent.
- [ ] Confirm displayed dates retain explicit local date, time and zone.

## Record after authorized execution

Chrome version/date: pending
Test account/data: synthetic only
Observed failures and reproduction: pending
Screenshots/evidence paths: pending
Overall result: pending
