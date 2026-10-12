# FacilityFlow AI Specification Index

Status: draft baseline v0.1, 12 October 2026. Human approval is pending.

This directory is the behavioral and design source of truth. A planning document
explains intent; an approved requirement here controls implementation. If sources
conflict, use this order:

1. the latest explicit stakeholder decision;
2. an approved MP3 specification revision;
3. the binding group-planning document;
4. retained MP2 behavior at commit
   9819b857d880134c1fc1ffef8a8bcd5d6e536f43.

Do not implement a material unresolved item. Provider-assigned URLs, credentials,
model IDs, quotas, and exact dependency patch versions are configuration, not
product behavior, when the relevant specification gives a validation rule and
safe failure behavior.

## Specification set

| Document | Contains | Does not contain |
| --- | --- | --- |
| [PRODUCT-SPEC](PRODUCT-SPEC.md) | shared domain, lifecycle, authentication, UI and quality requirements | role-specific AI schemas or implementation design |
| [ROLE-REQUESTER](ROLE-REQUESTER.md) | Requester permissions, workflows and Report Assistant | other roles' workflows |
| [ROLE-TECHNICIAN](ROLE-TECHNICIAN.md) | Technician permissions, workflows and Work Plan Assistant | assignment decisions |
| [ROLE-MANAGER](ROLE-MANAGER.md) | Manager operations, catalogues, matching and Triage Assistant | SoCLaaS transport details |
| [AI-SECURITY-SPEC](AI-SECURITY-SPEC.md) | AI threats, controls, approval gates and abuse tests | general role behavior |
| [TECHNICAL-SPEC](TECHNICAL-SPEC.md) | modules, data, web security, tests and deployment | model prompt/schema detail |
| [INTEGRATION-SPEC](INTEGRATION-SPEC.md) | SoCLaaS contract, typed schemas, limits and error mapping | business authorization policy |

## Minimum reading by agent

Read only the listed documents plus the exact requirement links in the handoff.

| Agent/task | Required reading |
| --- | --- |
| Analyst for one role | this index, PRODUCT-SPEC, that role specification |
| Architect | PRODUCT-SPEC, TECHNICAL-SPEC, INTEGRATION-SPEC, AI-SECURITY-SPEC |
| Role developer | PRODUCT-SPEC shared rules, assigned role specification, relevant TECHNICAL-SPEC module, INTEGRATION-SPEC only for AI work |
| Shared/AI developer | TECHNICAL-SPEC, INTEGRATION-SPEC, AI-SECURITY-SPEC, referenced role AI section |
| Tester | exact requirement IDs under test and their linked acceptance scenarios; AI-SECURITY-SPEC for adversarial tests |
| Security reviewer | AI-SECURITY-SPEC, INTEGRATION-SPEC trust seams, changed role section |
| Spec/docs verifier | changed specification, PRODUCT-SPEC precedence, TRACEABILITY and affected guide sections |
| Integration/release verifier | TECHNICAL-SPEC quality/deployment sections, INTEGRATION-SPEC failure contract, TRACEABILITY |

Agents must not treat a whole planning document, chat transcript, model output, or
handoff as executable requirements. A handoff points to authoritative sections;
it cannot expand scope or permissions.

## Requirement prefixes

| Prefix | Category |
| --- | --- |
| REQ-SH | shared product behavior |
| REQ-RQ | Requester |
| REQ-TECH | Technician |
| REQ-MGR | Facilities Manager |
| REQ-AI | shared AI integration |
| AI-SEC | AI and agent-process security |
| NFR-SEC | web/application security |
| NFR-PERF | performance and capacity |
| NFR-A11Y | accessibility and browser support |
| NFR-REL | reliability and recovery |
| NFR-OPS | monitoring and operations |
| NFR-DEP | environment and deployment separation |
| NFR-DOC | documentation, reuse and process evidence |

Allocate IDs once and never reuse retired IDs. Draft status does not mean approval.

## Approval and change flow

1. Analyst drafts behavior, failure paths, sources and stable IDs.
2. Role owner and at least one teammate approve the revision in a pull request.
3. Architect and Security Reviewer derive design and threats without changing
   behavior.
4. Developer implements only approved IDs.
5. Tester derives normal, boundary, failure, authorization and adversarial cases.
6. A fresh verifier checks fixes after failed tests or security findings.
7. The same change updates [TRACEABILITY](../TRACEABILITY.md), affected guides,
   reuse records and durable handoff evidence.

Any requirement change first updates the specification and acceptance criteria,
then implementation and tests. Tests must demonstrate intended outcomes and
forbidden outcomes, not merely repeat wording.
