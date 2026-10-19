# User Guide

Status: authentication, shared password/navigation screens, Technician assigned
work, start-work, Work Logs, completion and advisory Work Plan controls, and the
Requester manual workflow and Smart Report review, and Facilities Manager request lifecycle, administration and Triage Assistant
workflow are implemented locally. Teammate, real-browser and hosted application
review remain pending. Requester browser evidence is in the Requester verification
logs; historical synthetic live smoke passed against the retired Requester
gateway. Shared-runtime consolidation checks are recorded separately.
Live browser review remains pending. Local Technician evidence is in
logs/technician/T1-REPORT.md
through T5-REPORT.md; Manager evidence is in the Manager session logs.

This guide must be updated continuously to match the deployed product exactly.
It will document how Requesters, Technicians, and Facilities Managers access,
test, and use every released feature, including AI fallbacks and limitations.

## Introduction

The adaptive interface uses consistent cards, blue primary actions and visible
keyboard focus across the role workspaces. Navigation and action rows wrap on
narrow windows, forms stack, and wide request tables scroll horizontally within
their table area. Resizing preserves entered values and current selections.
Use Tab to reach controls and the shared header's **Skip to main content** link.
The supported browser remains desktop Chrome; the refresh changes presentation
and preserves existing actions and permissions. See the
[MP3 UI specification](../workflow/specs/user-interface.md) for layout targets.

The application supports secure sign-in/sign-out and separate role workspaces.
The Technician workspace lists assigned work and provides progress, completion
and advisory Work Plan controls. The Manager workspace links to request,
administration and audit workflows. Requesters can report, follow, edit and cancel their own requests and review
optional Smart Report suggestions.

## Accessing the deployed application

The deployed URL and support contact remain TBD. On a provisioned environment,
open `/login`, enter the username and password supplied by a Facilities Manager,
and select **Sign in**. The form works without JavaScript. The application sends
the Account directly to its role workspace.

## Roles

Requester, Technician and Facilities Manager are the only roles. Each Account
can open only its own role workspace. Directly entering another role's URL is
denied. Facilities Managers can provision and administer other Accounts.

## Change your password

The local implementation provides **Change password** in the authenticated header
for all three roles. Enter your current password and a new password containing
8–24 Unicode characters; no composition rule applies. Confirm the new password
exactly. Select **Change password**.
Success keeps you signed in. Subsequent sign-ins use the new password. Password
fields are cleared on errors; re-enter them to retry. Select **My workspace** to leave the form. Hosted availability and browser checks are pending.

## Features

### Requester: report and follow a problem

Open **My requests** from the Requester workspace, then **Report a problem**.
Enter a title (5–100 characters), description (10–2,000), location (2–120),
an active category and reported urgency. Character limits count Unicode code
points. Select **Submit request** once. Success opens the saved OPEN request
with its database-generated FF identifier; ownership always comes from your
signed-in account.

Field errors appear beside the relevant inputs and retain entered text. If a
category becomes unavailable, choose another active category. A temporary save
failure retains the form for retry. Repeating the same submission produces a
safe message directing you to **My requests**, without creating another report.

**My requests** includes reports a Manager recorded on your behalf, newest first.
Select a report to view its fields, status, Manager priority when set, timestamps,
status history, public reasons and Requester Updates. Times display explicitly in
Asia/Singapore. Other Requesters' reports and internal Work Logs/private Manager
data are unavailable. The optional assistant is described below; it may be
unavailable when AI is disabled or its provider configuration is not ready.

### Requester: dashboard and finding reports

The Requester workspace shows your counts for OPEN, ASSIGNED, IN_PROGRESS,
COMPLETED, CLOSED and CANCELLED, plus your five newest reports. Select a status
count to open **My requests** with that filter applied.

Search **My requests** by display ID, title or location. Search ignores case and
surrounding spaces; punctuation such as `%` and `_` matches literally. Combine
search with status, category and creation-date filters, then select **Apply filters**.
Both dates are inclusive in Asia/Singapore. Inactive categories remain available
for finding older reports; new reports still require an active category.

Applied values stay visible. **Reset filters** restores the full list. A new
workspace explains that you have no requests; a search with no matches suggests
changing or resetting filters. Invalid dates, reversed ranges or unavailable
filter values show field errors and retain your input.

### Requester: edit, cancel and add updates

An owned **OPEN** report offers **Edit request**. Change the five report fields
and select **Save changes**. If its category became unavailable, choose another
active category. You can also edit OPEN reports a Manager recorded for you.

**Cancel request** opens a separate confirmation page. Provide a reason containing
5–500 characters, tick the confirmation and explicitly select **Cancel request**.
**Keep request** leaves the report unchanged and is the default action. Cancellation
preserves the report and reason in history. CANCELLED reports reject later edits
and updates.

Select **Add Requester Update** to append a 1–1,000-character follow-up. Updates
are permitted while OPEN, ASSIGNED, IN_PROGRESS or COMPLETED awaiting Manager
review. CLOSED and CANCELLED reports reject additions. Existing updates remain
in history with their author and time.

If a report changes after you open a form, saving fails safely and retains your
input. Follow **Reload current request details**, review the latest report and
reapply your intended change. Retrying the stale form cannot overwrite newer work
or add the same update twice. A storage/audit failure retains input and permits
retry without a partial change. Character limits use Unicode code points.

### Shared navigation

The authenticated header shows display name, username and role. Select
**Sign out** to end the server-side session. A deactivated Account, a Manager
password reset represented by a session-version change, or a persisted role
change takes effect on the next request.
The header also provides **My workspace**, **Change password** and
**About**.

## Technician assigned work

After signing in as a Technician, `/technician` shows counts for Assigned,
In progress and Completed awaiting Manager review, followed by your assigned
Requests. `/technician/requests` opens the same queue with search and filters.
Counts describe your entire assigned workload and do not change when filtering.
Retained Closed or Cancelled assignments remain readable; they are not included
in those three count cards.

Search by display ID, title or location. Search ignores outer spaces and letter
case; `%` and `_` are ordinary search characters. Combine status, category and
Manager-priority filters, select **Apply filters**, or select **Reset** to clear
them. The default order is Manager priority, reported urgency, oldest assignment
time and display ID. Unknown migrated assignment times follow known times within
the same priority/urgency group. Empty assigned work and no filter matches have
different messages.

Open a Request's display ID/title to read its facts, Requester Updates and Work
Logs. Earlier technicians' Work Logs retain their author names and times when
the Request is now assigned to you. Times include the Singapore local date,
time and zone. Requests currently assigned to somebody else are unavailable.

Invalid filters show field errors while preserving submitted values. Unavailable
or reassigned details use the same safe message. If assigned work cannot be
loaded, use the retry/reset link; an operational failure is not shown as an empty
queue.

## Starting work and recording progress

Open an assigned Request in **Assigned** state and select **Start work**. A
successful command changes it to **In progress**. In that state, enter **Work
performed** and **Time spent in whole minutes**, then select **Add Work Log**.
Notes require 1–1,000 Unicode characters after trimming; internal whitespace is
preserved. Time must be a whole number from 1 to 1,440 minutes. Saved logs retain
their original author and local date/time and cannot be edited or deleted.

A successful submission opens the persisted Request detail. While submitting,
the button is disabled when JavaScript is available; the server also rejects
repeated submissions using the original Request version. If work changes before
you submit, the command fails safely and shows current assigned details when
still accessible. Review them before explicitly submitting again. If reassigned,
the Request becomes unavailable; your unsaved draft remains visible for copying.
Invalid values and operational failures preserve entered text and show safe
errors. No command is automatically replayed. A Technician cannot assign work,
change priority, edit the original Request, cancel, close, reopen or set a status.

## Completing work for Manager review

An **In progress** Request needs at least one saved Work Log before completion.
Enter a required **Resolution Summary** of 10–2,000 Unicode characters after
trimming and select **Submit for Manager review**. Completion retains the
assignment and records your resolution and completion time. The Request becomes
**Completed**, awaiting Manager review; the Technician cannot close it.

If there is no Work Log, add one before trying completion again. If another
change makes the submitted version stale, review the current assigned details
before explicitly submitting again. Unsaved resolution text remains visible on
errors, including when the Request is no longer assigned to you; keep a copy
before leaving the page. Successful completion shows the persisted resolution
and time, with no start/log/completion controls. Prior completion history remains
stored if a Manager later returns/reopens work and it is completed again.
Own-password change is implemented in T5; local automated evidence and remaining
browser/release gates are described below.

### Work Plan suggestions

On an Assigned or In Progress request, choose **Request Work Plan** for read-only
AI suggestions. Review safety precautions and stop/escalation conditions first.
The plan also suggests diagnostic steps, tools, questions and evidence to collect,
with a rationale. These suggestions require your judgment; they are not an
approved procedure. Requesting or discarding advice changes no maintenance record.
Starting work, adding a Work Log and completing work still require your explicit
manual submissions.

The assistant uses the request details and up to ten latest Requester Updates.
It does not receive Work Logs or account/request identifiers. If AI is unavailable,
limited, times out or rejects unsafe output, continue with the manual controls;
your unsaved notes and resolution remain available. If access or request state
changes while generating advice, the plan is discarded. Refresh the request
before requesting another plan. Leaving the page, submitting a manual form or
discarding advice clears it; plans are not saved. JavaScript is needed for the
Work Plan panel; the manual forms remain available without it.
## Facilities Manager: overview and request queue

From the Manager workspace, open **Operational overview** for counts by status,
counts by assigned Manager priority, and each active Technician's workload.
Workload counts ASSIGNED plus IN_PROGRESS requests.

Open **All requests** to view the shared queue. Search by display ID, title,
location, Requester or Technician. Search treats punctuation literally and can
be combined with status, category, priority, Technician and inclusive
Asia/Singapore creation-date filters. **Reset filters** clears the complete
filter set. OPEN requests appear first by reported urgency and age; active work
then appears by Manager priority and age; completed/terminal work follows by
latest update.

### Facilities Manager: record a request on behalf

Open **Record a request on behalf** and select an active Requester plus the
request fields. Inactive Requesters are deliberately absent. On success, the
selected Requester owns the OPEN request and the signed-in Manager is recorded
as its Recording Actor. The Request and Audit Event save together.

Field errors retain the submitted values. A temporary storage or audit failure
also retains the form and permits a retry without a partial Request. The submit
button prevents a second pending browser submission when JavaScript is available;
server-side validation and transactions remain authoritative.

### Facilities Manager: audit viewer

The read-only **Audit viewer** filters events by Request display ID, actor,
action text and inclusive Asia/Singapore dates. A non-Request event displays no
Request ID rather than inventing one. The current page is limited to the newest
200 matching events.

### Facilities Manager: request lifecycle

Select a Request ID or **Review and manage** in the queue. The detail page shows
Requester updates, Work Logs, completion attempts and audit history. Depending
on the current state, a Manager can assign, reassign with a reason, cancel with
a reason, accept COMPLETED work and close it, return COMPLETED work to an active
Technician, or reopen CLOSED work. Correction controls update request facts
without replacing ownership or historical evidence.

Every form carries the Request version shown when the page loaded. If another
user changes the Request first, FacilityFlow rejects the stale action and asks
the Manager to refresh. State, assignment and audit changes commit together.

### Facilities Manager: administration

Use **Accounts** to create Accounts, reactivate or deactivate another Account,
change another Account's role, and reset another Account's password. Managers
cannot use these controls on themselves. A Technician with ASSIGNED or
IN_PROGRESS work must have that work reassigned or cancelled first, and at least
one active Manager must remain.

Use **Categories** to create or rename categories. Removing a category requires
a different active replacement; FacilityFlow migrates existing Requests and
the catalogue change atomically. Use **Skills** to create, rename or deactivate
skill tags and update each active Technician's skills. Deactivated tags disappear
from future selection but remain in historical records.

## AI features

### Requester: Smart Report Assistant

On **Report a problem** or an owned OPEN report's **Edit request** page, optionally
enter a rough description and select **Get AI suggestions**. The assistant uses
that text and your current report fields. Keep the rough text and current
description together within 2,000 characters when they contain different text.
Keep sensitive personal details out of
the report. The ordinary form remains editable while you wait.

The labelled **AI suggestions — review before use** panel shows proposed fields,
missing-information questions and rationale. **Use suggested title**, for example,
copies just that field into the ordinary form. Accept other fields individually
or edit them yourself. Nothing is saved until you select **Submit request** or
**Save changes**. All normal field, ownership and OPEN-state checks still apply.

**Discard suggestions** clears the review panel and ignores an in-flight result.
It keeps your manual fields, including values you already accepted. If you edit
while waiting, the late result is discarded. If you change a field after review
appears, an old suggestion cannot overwrite that field; ask again for new
suggestions. Returning through browser history also clears the review panel.

AI failure, timeout, invalid output or quota exhaustion shows safe guidance and
keeps your manual input. You can submit normally without waiting for AI. With
JavaScript disabled, the ordinary form still works. In the local implementation,
defaults allow five AI attempts/minute and 30/hour per Account, with five in flight
across the application. Discarding locally may leave a bounded server attempt
finishing and consuming its quota. No live response quality is claimed yet.

### Other role assistants

The other role assistants are:

- Technicians may ask the Work Plan Assistant for an advisory diagnostic and
  evidence-collection plan for an assigned request.
- Facilities Managers may ask the Triage Assistant for explained category,
  priority, clarification, and skill-tag suggestions. The application then
  ranks eligible Technicians using deterministic rules.

No assistant performs a business action. Users review suggestions and use the
ordinary workflow.

### Facilities Manager: Triage Assistant

In **All requests**, choose **Ask Triage Assistant** for any Request that is not
CANCELLED or CLOSED. Select **Generate triage suggestion**. The page displays an
editable category, an editable Manager priority, required and optional skill
suggestions, clarification questions, rationale and a deterministic Technician
order. Skill choices begin unselected. The Manager reviews or edits the final
category and priority and selects the final Technician.

The assistant itself never saves or assigns. For an OPEN Request, **Approve
triage and assign** submits the reviewed values through the ordinary locked,
authorized assignment operation. Active Requests continue through the ordinary
correction/reassignment controls. If AI is disabled, limited, times out, returns invalid output or the
Request/catalogue changes while it is running, the page shows safe retry/manual
guidance and leaves the Request unchanged.

## Limitations

AI drafts/recommends only; users perform authorized actions. Live provider,
hosted/browser behavior and released deployment limits remain unverified.

The Manager slice has local automated evidence only; no hosted application or
real-browser release claim is made yet. Teammate review remains required before
merge.

## Troubleshooting

`Invalid username or password` intentionally covers an unknown username, wrong
password and inactive Account. Retry the supplied credentials or contact a
Facilities Manager; the message does not reveal whether an Account exists. If
the session is no longer valid, sign in again. Deployment/support details remain
TBD.


## Shared pages and password change (T5)

T5 was paused after implementation and then resumed. Password/session/audit,
shared-page rendering/security and client script checks now have passing local
automated evidence. Browser/keyboard/accessibility and hosted release acceptance
remain pending; see `logs/technician/T5-REPORT.md` for exact executed results.

The authenticated header identifies your display name, username and role. Use
My workspace to return to your role, Change password for your own account,
About for application/workflow information, and Sign out to end the session.
Navigation never grants access to another role's records. A skip link and visible
keyboard focus support navigation through the shared pages and Technician forms.
Existing Technician dates show local date, time and zone (Asia/Singapore).

To change your own password, enter your current password, a new password of
8–24 Unicode code points, and the same new password again. Spaces count and are
preserved; there is no composition requirement. The change is for your signed-in
account only. A successful change keeps you signed in and shows a confirmation.
Errors identify the relevant field; password inputs clear after a server error
and must be re-entered. Ordinary unsaved Work Log and resolution input retains
its existing failure handling. Password changes require no AI service.

Pending-submit feedback applies to login, password change and logout; returning
through browser history restores form submission. Manual start/log/completion
and Work Plan controls keep separate pending handling. The Work Plan clears
obsolete pending text when invalidated and requires refresh after a manual
submission; advice still cannot perform a business action.

Browser keyboard/responsive/accessibility acceptance is pending because local
browser use is disabled in Work settings. Final integrated result, independent
review and local commit status are recorded in `logs/technician/T5-REPORT.md`.
No hosted or live-provider verification is implied.
