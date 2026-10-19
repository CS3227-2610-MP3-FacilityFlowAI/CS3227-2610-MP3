# Local startup: Supabase connectivity timeout

- Date: 19 October 2026, Asia/Singapore.
- User reported bootRun exiting1 after54s while opening the app for live AI use.
- Requirements: TECHNICAL-SPEC web schema readiness; NFR-SEC-002 safe diagnostics;
  preserve existing REQ-RQ-012–017 live AI fix and evidence.
- No source code, local credentials, limits or schema settings changed.
- Two strict-TLS configured-datasource read-only probes failed before executing
  queries: SQLSTATE08001, PSQLException caused by SocketTimeoutException. The
  configured certificate file exists; current credentials/TLS verification could
  not be assessed because network establishment failed.
- Separate credential-free network probe: hostname resolves to three addresses;
  TCP connection to the configured endpoint timed out. This supports a network,
  endpoint or project-availability issue; it does not prove a particular cause.
- Bounded JAR startup with local profile and a temporary loopback port reproduced
  exit1, Application run failed and Database schema is not ready, with nested
  CannotGetJdbcConnectionException/PSQLException/SocketTimeoutException. An initial
  probe stopped after an early server-start message; the follow-up waited for
  readiness completion and confirmed the actual failure. Diagnostic processes
  exited or were stopped; no ready application was left running.
- No password-authentication failure28P01, port-in-use, YAML or unresolved-setting
  error was identified. Do not bypass readiness, TLS or change passwords based
  on this timeout alone.
- Root cause still needs external state: user was asked for Supabase project
  status. If healthy, compare Connect/session-pooler endpoint and check current
  network/VPN/firewall and any configured Supabase IP restrictions. Do not widen
  access indiscriminately. Hosted status, IP restrictions and user's network
  policy have not been inspected.
- Prior live AI checks used disposable local PostgreSQL and remain valid for
  that scope; they did not establish shared Supabase connectivity.
- Ignored evidence: build/local-startup-diagnostic.log and sanitized JDBC/network
  helper output. Raw local config and secrets were not displayed or copied.
- No migration, data change, commit or push. Earlier uncommitted work preserved.

User reports project Active/healthy. Private config parsing confirms the public
endpoint aws-0-ap-northeast-2.pooler.supabase.com:5432. Paused status is not the
user-reported cause; exact dashboard endpoint currency, network restrictions
and local network/VPN reachability remain unverified. Next: user-side TCP test
and comparison from another permitted network before another authenticated retry.

## Connectivity restored on hotspot

User supplied a successful TCP test after switching to a phone hotspot. The
subsequent agent JDBC probe also succeeded with configured app credentials and
strict certificate/hostname verification, in a read-only transaction. The app
role sees schema_version/request_submission and successful versions1-6. This
confirms connectivity restoration and points to the original network path; the
specific blocking policy/device is not established. No credentials, application
settings, schema or business data changed. Next: ordinary app startup on hotspot.

## Follow-up pasted startup log: DNS resolution

User supplied a later bootRun log showing UnknownHostException for the configured
pooler, alongside shared AI READINESS/NETWORK_FAILURE. The subsequent schema
readiness error is a consequence of failed connection, not evidence of missing
migrations. Windows Resolve-DnsName checks timed out for database and AI hosts;
a fresh Java process later resolved both. No inherited JVM network-option
variables were found by the limited presence/flag check; project build files
contained no inspected IPv4/IPv6 overrides. This points to intermittent DNS
availability, not a proven persistent JVM setting or password error.

The later strict-TLS app-role JDBC retry succeeded read-only with successful
versions1-6. No system DNS, IPv4/IPv6, credentials or app configuration changed.
An initial automatic approval review for the Java DNS probe timed out before
execution; one permitted retry succeeded. No raw secret values logged. Next:
remain on working hotspot and launch a fresh app JVM; clear local DNS cache if
needed, then retry. Current full browser startup and provider reachability after
this network change are not independently verified by this continuation.
