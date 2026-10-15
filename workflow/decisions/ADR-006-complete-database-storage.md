# ADR-006: Complete shared storage and explicit initialization

- Date: 15 October 2026, Asia/Singapore.
- Status: Implemented against approved requirements; teammate review pending.
- IDs: REQ-SH-007/009/014–017; Requester/Technician/Manager storage requirements;
  REQ-AI-007–010; AI-SEC-008/017/021; TECHNICAL-SPEC.
- Decision: Add V2 maintenance/category/history storage and V3 skills/AI metadata/
  rolling-limit storage. Preserve applied V1. Use UUIDs consistently with the
  existing schema, a bounded generated identity for Request display numbers,
  explicit column privileges and private-schema RLS for the trusted server role.
- Initialization: Explicit database-initialize profile only. Demo flag defaults
  false; production bootstrap requires three deployment secrets. Advisory lock
  serializes first setup, transactions preserve atomic audit/business writes,
  existing Accounts skip initialization and are never overwritten.
- Password choice: Boot-managed Spring Security crypto 7.1.1 PBKDF2 v5.8 defaults
  with {pbkdf2@SpringSecurity_v5_8} format. Documented 310,000 iterations, 16-byte
  random salt, SHA-256, 256-bit output. Unlike bcrypt's 72-byte limit, this handles
  the approved 24-code-point Unicode password boundary without truncation. Login
  must use a delegating verifier supporting this stored format.
- AI storage: No raw content fields. Rolling timestamp arrays and five expiring
  slot leases support atomic limit operations, not a fixed-window approximation.
  Actual gateway enforcement remains assigned to the AI owner.
- Alternatives: Manual hosted table/password-hash insertion or unconditional
  startup/demo seeding were rejected. No autonomous workflow or new UI added.
- Limits: Eligible actor/assignee/catalogue checks, named transitions and history
  visibility remain service responsibilities. No full role feature verified.
  Runtime schema validation is still required before business endpoints. No
  Supabase Auth, hosted writes, production backup or deployment added.
- Verification: Exact commands/results and fresh failed-test verification in
  logs/2026-10-15-complete-database-storage.md and the verification handoff.
- Approval: User requested complete database preparation; approved specifications
  govern behavior. Teammate merge approval is pending; no new commit requested.
