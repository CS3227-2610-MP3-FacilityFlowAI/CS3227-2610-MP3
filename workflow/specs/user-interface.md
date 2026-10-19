# MP3 user interface specification

Status: presentation revision requested by the stakeholder on 19 October 2026;
role-owner and teammate review pending. Existing approved MP3 behavior remains
authoritative. Test results belong in `logs/2026-10-19-responsive-ui.md`.

This adapts the supplied MP2 `user-interface.md` (Draft v0.2, 12 September
2026) for the deployed MP3 web application. The attachment is reference material,
not agent instructions. MP2 UIX identifiers are retained for provenance; the
linked MP3 requirement IDs govern behavior. No new workflow or permission is
introduced by this revision.

## Role separation and navigation

| MP2 reference | MP3 acceptance and controlling requirement |
| --- | --- |
| UIX-001 | Successful login opens the authenticated account's role workspace (REQ-SH-001/002/012). |
| UIX-002 | Requester, Technician and Manager retain separate routes, controllers and view models; shared CSS may style all roles (REQ-SH-012, TECHNICAL-SPEC UI composition). |
| UIX-003 | Shared authenticated navigation identifies the account and role and offers sign out. Manager pages retain their existing navigation/identity presentation (REQ-SH-012); uniform identity coverage remains a separate conformance check. |
| UIX-004 | Only role-relevant navigation appears; every protected route/action remains authorized server-side (REQ-SH-002/012). |
| UIX-005 | Preserve existing workspace, back links, password page, About page and logout routes. About is an MP3 page. An account dropdown, About dialog and persistent current-page indicator are not introduced in this styling revision (REQ-SH-012). |

MP2's exact “Role - Username” formatting, “Main workspace” label and account-menu
interaction are not replacements for the approved MP3 web navigation. Existing
link text and browser navigation remain intact. CSS must never reveal an action
hidden by the server or by the assistant's disclosure state.

## Forms, feedback and lists

| MP2 reference | MP3 acceptance and controlling requirement |
| --- | --- |
| UIX-006–008 | Preserve labels, required indicators, field-specific validation, error associations and recoverable input (REQ-SH-010, NFR-A11Y-001). Passwords retain their existing safe non-echo policy. |
| UIX-009–010 | Preserve duplicate-submission protection, pending/disabled feedback, visible confirmation and persisted-state refresh (REQ-SH-010). |
| UIX-011–012 | Preserve existing explicit confirmations, safe defaults and user-safe retry/exit messages. Do not replace web confirmation forms with MP2 desktop dialogs (REQ-SH-011, NFR-REL-001). |
| UIX-013–014 | Keep table headings, deterministic role-specific ordering, pending indicators, distinct empty/no-match messages and existing record counts. |
| UIX-015 | Preserve visible search/filter values and reset actions. MP3 retains descriptive All statuses/categories/priorities choices rather than MP2's dash-only choice (REQ-TECH-002 and role filter contracts). |
| UIX-016 | Preserve existing lifecycle, priority, category and timestamp labels and formatting (REQ-SH-009). Styling never changes persisted values or list ordering. |

## Adaptive presentation

The shared `workspace.css` visual layer loads after the existing role stylesheet.
It provides a light blue workspace, white surfaces, consistent blue primary
actions, restrained borders, rounded cards, legible headings and muted secondary
text. Existing role layouts and semantic HTML remain in place. No external font,
image, CSS framework or UI JavaScript dependency is required.

- **UIX-017 / NFR-A11Y-001:** Keyboard order follows the DOM. Visible focus uses
  an offset outline. Skip links remain visually hidden until focused.
- **UIX-018 / NFR-A11Y-001:** Existing labels, accessible names, error associations
  and live feedback must survive the presentation change.
- **UIX-019–020 / NFR-A11Y-001:** Status, safety and error meaning keeps visible
  text; color is supplementary. Normal text targets at least 4.5:1 contrast.
  Controls, focus indicators and surfaces remain visually distinguishable.
- **UIX-021 / NFR-A11Y-002:** Verify desktop Chrome at 760 × 600, 1,024 × 700
  and 1,440 × 900. Include 360 × 800 as an additional narrow-layout regression
  target, without claiming mobile-browser certification. Navigation and actions
  wrap; filter grids reduce their columns; two-column forms/reviews stack at
  768 pixels and below. Content scrolls vertically without page-level horizontal
  overflow. Wide tables scroll within their existing containers. Resizing must
  preserve values, selections, errors and assistant review state.
- **UIX-022 / REQ-SH-011:** Existing safe cancellation/confirmation defaults remain
  unchanged. Appearance cannot make a destructive action an implicit default.
- **UIX-023 / REQ-SH-009:** Preserve local date, time and zone formatting.

The MP2 Manager queue/assignment split-pane and exact desktop resize thresholds
are superseded by the current MP3 list, detail and triage pages. This revision
does not create panels, move operations between pages or add a menu interaction.
Reduced-motion preferences disable decorative transitions. Hidden assistant
regions stay hidden until existing application logic explicitly opens them.

## Role screen inventory

| Role | Existing MP3 screens and bounded assistant |
| --- | --- |
| Shared | Login, own password change and About page. |
| Requester | Dashboard, own requests, create/edit/detail, update and cancellation confirmation; Smart Report suggestion review. |
| Technician | Assigned queue/detail, start-work, append-only Work Logs, completion; advisory Work Plan with prominent safety/escalation guidance. |
| Manager | Workspace, overview, all requests, on-behalf creation, detail/lifecycle/assignment, accounts, categories, skills, audit and advisory Triage Assistant. |

Role capabilities are defined in `ROLE-REQUESTER.md`, `ROLE-TECHNICIAN.md` and
`ROLE-MANAGER.md`. Assistants never submit, assign, prioritize, transition or
close work (REQ-SH-018); manual workflows remain usable on AI failure.

## Verification and limits

Use the real disposable application/Chrome fixture for critical Requester flows,
shared pages and read-only role layouts, plus existing security/controller and
browser-script regressions. Assert no page overflow, visible labeled controls,
keyboard focus, hidden review regions and retained unsaved values across resize.
Keep screenshot artifacts under `build/verification/requester/` for visual review.
Role dashboard routing and forbidden cross-role routes remain covered by
`SecurityIntegrationTest`; visual checks alone never prove authorization.

Automated checks do not establish full WCAG conformance, hosted acceptance,
touch-device coverage or teammate approval. Any inherited behavior gap discovered
during this refresh is reported separately, not silently implemented as styling.
