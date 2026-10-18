# User Guide

Status: authentication foundation and Technician assigned-work reads implemented
in source; teammate and hosted-release verification remain pending. Technician
start-work, Work Logs, completion and Work Plan browser controls are implemented
in source; local T4 verification is recorded in logs/technician/T4-REPORT.md.

This guide must be updated continuously to match the deployed product exactly.
It will document how Requesters, Technicians, and Facilities Managers access,
test, and use every released feature, including AI fallbacks and limitations.

## Introduction

The application supports secure sign-in/sign-out and separate role workspaces.
The Technician workspace lists assigned work; Requester and Manager manual
workspaces remain placeholders.

## Accessing the deployed application

The deployed URL and support contact remain TBD. On a provisioned environment,
open `/login`, enter the username and password supplied by a Facilities Manager,
and select **Sign in**. The form works without JavaScript. The application sends
the Account directly to its role workspace.

## Roles

Requester, Technician and Facilities Manager are the only roles. Each Account
can open only its own role workspace. Directly entering another role's URL is
denied. Own-password change is available through the authenticated header.
Account provisioning screens remain outside this Technician increment.

## Features

The authenticated header shows display name, username and role. Select
**Sign out** to end the server-side session. A deactivated Account, a Manager
password reset represented by a session-version change, or a persisted role
change takes effect on the next request.

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
