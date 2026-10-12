# User Guide

Status: authentication foundation implemented 13 October 2026; teammate review
pending. Maintenance and AI workflows are not yet released.

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
