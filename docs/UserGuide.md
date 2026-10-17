# User Guide

Status: authentication foundation and the first Facilities Manager read/create
slice are implemented locally; teammate, browser and hosted review remain pending.

This guide must be updated continuously to match the deployed product exactly.
It will document how Requesters, Technicians, and Facilities Managers access,
test, and use every released feature, including AI fallbacks and limitations.

## Introduction

The current walking skeleton supports secure sign-in, sign-out and separate
placeholder workspaces for the three FacilityFlow roles.

## Accessing the deployed application

The deployed URL and support contact remain TBD. On a provisioned environment,
open `/login`, enter the username and password supplied by a Facilities Manager,
and select **Sign in**. The form works without JavaScript. The application sends
the Account directly to its role workspace.

## Roles

Requester, Technician and Facilities Manager are the only roles. Each Account
can open only its own role workspace. Directly entering another role's URL is
denied. Account provisioning and password-change screens are not yet available.

## Features

The authenticated header shows display name, username and role. Select
**Sign out** to end the server-side session. A deactivated Account, a Manager
password reset represented by a session-version change, or a persisted role
change takes effect on the next request.

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

Manager assignment, reassignment, cancellation, completed-work review,
correction, account/catalogue/skill administration and Triage Assistant remain
pending. The first Manager slice has local automated evidence only; no hosted or
real-browser release claim is made yet.

## Troubleshooting

`Invalid username or password` intentionally covers an unknown username, wrong
password and inactive Account. Retry the supplied credentials or contact a
Facilities Manager; the message does not reveal whether an Account exists. If
the session is no longer valid, sign in again. Deployment/support details remain
TBD.
