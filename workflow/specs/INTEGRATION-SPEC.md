# Integration Specification

Status: draft scaffold; not approved for implementation. Owner/reviewer: TBD.

Planning context: [PLAN.md](../PLAN.md). Existing planning decisions are preserved;
behavior, acceptance criteria and detailed design still need approval.
Material TBDs block implementation. Approval revision, approvers and evidence: TBD.

## Interfaces and owners

Role services, persistence, audit and gateway contracts: TBD across owners.
Ownership follows AGENTS.md.

## SoC LLM contract

Application AI must use the SoC LLM. Endpoint, model, authentication, supported
schema and documented quotas: TBD. Credentials load from configuration/environment
and must never be committed or logged.

## Typed inputs and outputs

Schemas, enums, lengths, unknown-field rejection and invalid-output responses:
TBD. Model output is untrusted.

## Authorization and context scope

Authorize before collecting minimal role-scoped context. Exact fields/service
checks: TBD. The model never receives mutation capabilities.

## Limits and failures

Per-user/global budgets, timeouts, concurrency, bounded retries and responses
to 429, partial output and unavailability: TBD. Preserve core workflow use.

## Persistence and audit

Transactions, version checks, event contracts and safe audit metadata: TBD.

## Environment configuration

Separate development/production data, secrets and configuration. Approved
variable names, credentials/quotas separation and provisioning: TBD.

## Integration acceptance and tests

Contract, authorization, schema, failure, quota and isolation cases/outcomes:
TBD. External calls require an approved integration design.
