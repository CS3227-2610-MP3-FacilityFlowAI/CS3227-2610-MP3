# User Guide

Status: authentication, Requester manual workflow and Smart Report review, and
Facilities Manager lifecycle, administration and Triage Assistant review are
implemented locally. Teammate and hosted application review remain pending;
Requester browser evidence is recorded in the verification logs.
Live SoCLaaS smoke verification was deferred by the user.

This guide must be updated continuously to match the deployed product exactly.
It will document how Requesters, Technicians, and Facilities Managers access,
test, and use every released feature, including AI fallbacks and limitations.

## Introduction

The current walking skeleton supports secure sign-in, sign-out and separate
workspaces for the three FacilityFlow roles. Requesters can now create and view
their own maintenance reports in the local implementation.

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
8–24 Unicode characters; no composition rule applies. Select **Change password**.
Success keeps you signed in. Subsequent sign-ins use the new password. Password
fields are cleared on errors; re-enter them to retry. Select **Back to main
workspace** to leave the form. Hosted availability and browser checks are pending.

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
The header also provides **Main workspace**, **Change password** and
**About FacilityFlow**.

### Facilities Manager: overview and request queue

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
that text and your current report fields. Keep sensitive personal details out of
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
