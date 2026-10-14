# Complete database preparation handoff

- Source/destination: Codex implementation → next hosted setup/authentication part.
- Goal: Shared database schema and explicit first initialization supplied and tested.
- Authorization: User authorized continuing database completion in small parts on
  feat/database-migration. Never commit MP3 instructions. No new commit, cloud
  credential access, teammate messages, merge or production deployment authorized.
- Sources/IDs: Approved v0.1 specs and REQ-SH-007/009/014–017, role storage,
  REQ-AI-007–010, AI-SEC-008/017/021. ADR-006 explains concrete choices.
- Artifacts: V2/V3, DatabaseInitializer, database/initialization tests, completion
  guide, REUSE/TRACEABILITY and logs/2026-10-15-complete-database-storage.md.
- Evidence: Initial 19-test run failed exact SQLSTATE assertion only; fixed to
  generated-column428C9 and independently verified19/19. Final full build26/26
  passed in2m17s. V1 unchanged; no hosted query/write performed.
- Scope/claims: All14 technical-model tables and atomic opt-in demo/bootstrap
  source exist. Shared JDBC privileges/RLS are not human role authorization.
  Actual gateway rate/lease algorithms and schema guard before runtime endpoints
  remain outstanding; initialize Accounts is not login implementation.
- Security: PBKDF2 v5.8 delegated hashes support24 Unicode code points. Bootstrap
  secrets are never logged; demo credentials are development-only and explicit.
  Initialization refuses existing teammate business/catalogue changes; existing
  Accounts skip without overwrite. Migration identity remains separate from app.
- Risks/unresolved: Hosted upgrade/seed verification pending user steps. No backup/
  production release/session cookie evidence. Services must enforce eligibility,
  ownership/state/audit atomicity and safe historical skill snapshots.
- Worktree: Existing user .gitignore changed independently; preserve it and
  downloaded .agents skills/skills-lock. This part is not staged/committed.
- Human review: Teammate merge approval pending; no approval fabricated.
- Next authorized action: Guide hosted V2/V3 migration and explicit development
  initialization via docs/DatabaseCompletion.md, verify safe metadata/counts,
  then next approved authentication/service slice after checking current state.
- Suggested skills: Supabase/Postgres best practices, Context7, Ponytail,
  FacilityFlow handoff; fresh verification after future failures/findings.
- Status: Local database preparation verified; hosted application pending.
