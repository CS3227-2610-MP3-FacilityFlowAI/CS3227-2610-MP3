# Specification Index

This directory is the source of truth for FacilityFlow AI behavior. The initial
specification gate requires the following approved artifacts before feature
implementation begins:

- `PRODUCT-SPEC.md`
- `ROLE-REQUESTER.md`
- `ROLE-TECHNICIAN.md`
- `ROLE-MANAGER.md`
- `AI-SECURITY-SPEC.md`
- `TECHNICAL-SPEC.md`
- `INTEGRATION-SPEC.md`

Do not use unresolved placeholders for material behavior. Record assumptions
and obtain team review before implementation.

## Stable requirement IDs

| Prefix | Category | Example format (not an allocated requirement) |
| --- | --- | --- |
| REQ-RQ | Requester | REQ-RQ-001 |
| REQ-TECH | Technician | REQ-TECH-001 |
| REQ-MGR | Facilities Manager | REQ-MGR-001 |
| REQ-AI | Shared AI | REQ-AI-001 |
| AI-SEC | AI security | AI-SEC-001 |
| NFR | Non-functional | NFR-001 |
| NFR-REL | Reliability subcategory already used in the plan | NFR-REL-001 |

Allocate sequential unique numbers per prefix only for real drafted requirements.
Keep IDs across revisions; never reuse retired IDs. Document any new subcategory
here before use. The role AI contracts allocate `REQ-RQ-001`, `REQ-TECH-001`,
and `REQ-MGR-001`; shared AI and security requirements allocate `REQ-AI-001`
through `REQ-AI-002` and `AI-SEC-001` through `AI-SEC-005`. These remain drafts
until teammate approval.

## Requirement and approval workflow

Use [the requirement template](../templates/requirement-template.md). Each entry
needs a source, owner, status, observable acceptance criteria and failure cases.
Material TBDs block implementation. Record the approved revision and review by
the role owner and one teammate before coding. Link [traceability](../TRACEABILITY.md)
in the same change as specs/code/tests. Preserve superseded IDs and history.

Product intent belongs in product/role specs, architecture/contracts in
technical/integration specs, and shared AI threats in the security spec. Link
shared constraints instead of maintaining conflicting copies.
