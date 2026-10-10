# User Guide

Status: pre-implementation placeholder.

This guide must be updated continuously to match the deployed product exactly.
It will document how Requesters, Technicians, and Facilities Managers access,
test, and use every released feature, including AI fallbacks and limitations.

## Introduction

Facilities-maintenance application is planned; no released features exist yet.

## Accessing the deployed application

URL, browsers, sign-in and support contact: TBD after deployment.

## Roles

Requester, Technician and Facilities Manager. Provisioning and released
permissions: TBD; approved role specs will govern behavior.

## Features

Verified released workflows, steps and examples: TBD.

## AI features

Three bounded SoC LLM assistants are planned:

- Requesters may ask the Smart Report Assistant for an editable structured
  draft, missing-information questions, and limited safety advice.
- Technicians may ask the Work Plan Assistant for an advisory diagnostic and
  evidence-collection plan for an assigned request.
- Facilities Managers may ask the Triage Assistant for an explained category,
  priority, clarification, and eligible-technician suggestion.

No assistant performs a business action. Users review suggestions and use the
ordinary workflow. Exact steps and screenshots will be added only after the
features are implemented and verified.

## Limitations

AI drafts/recommends only; users perform authorized actions. Confirmed
released resource limits and failure behavior: TBD.

## Troubleshooting

Verified recovery steps for authentication, request and AI failures: TBD.
