# Local startup connection diagnosis handoff

- Source agent: Codex diagnostic session; destination: next collaborator/session.
- Task/goal: diagnose user bootRun exit1 while opening Requester live AI.
- Approved requirement IDs: TECHNICAL-SPEC schema readiness, NFR-SEC-002;
  preserve REQ-RQ-012–017 behavior and existing live evidence.
- Authorized scope: reproduce startup, read-only connectivity and safe network
  checks. No schema edits, password rotation, cloud settings or push authorized.
- Provenance: logs/2026-10-19-local-startup-connection-timeout.md; current
  uncommitted Requester non-reasoning fix remains preserved.
- Findings: configured CA exists; DNS resolves; TCP times out. Read-only JDBC
  probes08001/SocketTimeoutException. Actual local-profile JAR exits1 with
  Database schema is not ready and the same nested connection timeout.
- Unresolved: project active/paused/restoring status, exact dashboard endpoint
  currency, network/VPN/firewall and any configured IP restrictions.
- Files changed: evidence only; no app/config/schema changes. Ignored helpers
  report safe categories; do not print local secret file or raw diagnostics.
- Verification: startup/read-only probes executed; no SQL queries succeeded,
  no shared data writes; no ready diagnostic service left running.
- Suggestions accepted/rejected: investigate external connectivity; reject
  bypassing schema readiness/TLS or treating timeout as bad-password evidence.
- Human input: Supabase project status question pending. No teammate/release
  approval inferred. Next: use user's status/network information, then retry
  only after a relevant external change; preserve existing credentials.
- Suggested skills: Supabase, Postgres best practices, Ponytail,
  facilityflow-handoff.
- Status: application startup blocked by external database reachability;
  narrower AI tests previously passed against disposable local PostgreSQL.

User status reply: Active/healthy. Configured public endpoint confirmed as
aws-0-ap-northeast-2.pooler.supabase.com:5432. Await a user-side TCP/network check;
do not infer healthy dashboard means database port is reachable or widen ACLs.

Resolved connectivity continuation: user hotspot TCP test passed; root then
verified strict-TLS app-role JDBC connectivity read-only and successful schema
versions1-6. The external reachability blocker is cleared on hotspot. Original
network policy/device cause remains unconfirmed. No config/schema/data changes;
next ordinary app startup by user on the working connection. Earlier blocked
status is historical, not current on hotspot.

Latest pasted startup failure is UnknownHostException for pooler. Windows DNS
queries timed out for pooler/SoCLaaS; fresh Java later resolved both and app-role
strict-TLS read-only JDBC retry again confirmed V1-V6 success. No settings/code
or DNS configuration changes performed. Suspect intermittent network DNS, not
missing migrations. Next fresh-JVM local startup on hotspot (optionally clearing
Windows DNS cache); browser/provider access still needs user startup verification.
