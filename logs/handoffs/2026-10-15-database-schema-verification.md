# Fresh V2/V3 database verifier handoff

- Source: Codex implementation session; destination: fresh verifier agent.
- Authorization: AGENTS.md rule 5 requires fresh verification after failed tests.
  Read-only code review and isolated test execution; no code edits, hosted writes,
  commits, staging, secret inspection or changes to unrelated files.
- Task: Verify V2/V3 migrations and the corrected generated-identity negative
  test. Check correctness/security of grants, RLS, IDs, history and AI metadata.
- Approved sources: workflow/specs PRODUCT-SPEC, TECHNICAL-SPEC, relevant role
  and AI security/integration requirements. REQ-SH-007/009/014; REQ-RQ-002/008;
  REQ-TECH-006/007; REQ-MGR-016–019; REQ-AI-007–010; AI-SEC-008/017/021.
- Artifacts: New V2__maintenance_records.sql, V3__skills_ai_storage.sql and
  DatabaseMigrationTest; V1 is already applied and must remain unchanged.
- Initial verification: test bootJar ran 19 tests, 18 passed/1 failed. Negative
  identity-write test expected permission SQLSTATE 42501; PostgreSQL emitted
  generated-column SQLSTATE 428C9 first. Only the expected exact error code was
  corrected; mutation remains rejected. Fresh run/result not yet recorded.
- Assumptions: These are shared storage prerequisites; use-case authorization,
  eligible assignee checks, named transitions and gateway limits remain services.
  App uses trusted JDBC role; human roles are not mapped to Supabase Auth.
- Unresolved: Hosted application pending. User modified .gitignore and installed
  skill files; preserve them. Never commit MP3 instructions. No runtime endpoints.
- Suggested skills: Supabase/Postgres best practices, Ponytail review, Context7
  for Flyway/driver APIs. Tests use disposable loopback PostgreSQL, no hosted data.
- Human review: Teammate merge review pending; agent verification is not approval.
- Next action/status: Fresh verifier reruns .\gradlew.bat --offline --no-daemon
  test bootJar and reports findings/command/results. Verification pending.

Completed verification: database_verifier independently ran the command successfully
in 54 seconds: 19 tests, zero failures/errors/skips. No material V2/V3 findings;
generated-column rejection confirmed, V1 unchanged. No source/cloud edits.
The later initializer/full-suite verification is separately recorded in the Part 3 log.
