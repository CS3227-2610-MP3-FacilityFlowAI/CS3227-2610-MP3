# User Guide

Status: authentication and Requester manual workflow, dashboard and filters implemented locally;
teammate review and hosted verification pending. AI workflows are not yet released.

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
denied. Account provisioning screens are not yet available.

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
data are unavailable. The assistant review interface is still pending. Its
service is tested against a local fake; ordinary runtime currently returns
unavailable and the manual workflow remains usable.

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

## AI features

Three bounded SoC LLM assistants are planned:

- Requesters may ask the Smart Report Assistant for an editable structured
  draft, category and urgency suggestions, and missing-information questions.
- Technicians may ask the Work Plan Assistant for an advisory diagnostic and
  evidence-collection plan for an assigned request.
- Facilities Managers may ask the Triage Assistant for explained category,
  priority, clarification, and skill-tag suggestions. The application then
  ranks eligible Technicians using deterministic rules.

No assistant performs a business action. Users review suggestions and use the
ordinary workflow. Exact steps and screenshots will be added only after the
features are implemented and verified.

## Limitations

AI drafts/recommends only; users perform authorized actions. Confirmed
released resource limits and failure behavior: TBD.

## Troubleshooting

`Invalid username or password` intentionally covers an unknown username, wrong
password and inactive Account. Retry the supplied credentials or contact a
Facilities Manager; the message does not reveal whether an Account exists. If
the session is no longer valid, sign in again. Deployment/support details remain
TBD.
