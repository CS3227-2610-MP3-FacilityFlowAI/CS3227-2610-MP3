# Pin the Authentication Foundation

- Decision ID: ADR-003
- Date: 2026-10-13
- Status: implemented; teammate approval pending
- Related requirements: REQ-SH-001–005, REQ-SH-012, REQ-SH-018,
  NFR-SEC-001, AI-SEC-001

## Context

The approved technical specification requires the latest non-preview Spring
Boot release supporting Java 25, Spring Security form login, Spring Session
JDBC, PostgreSQL/Flyway, maintained adaptive password hashing and authorization
at both HTTP and use-case seams. No application or database implementation
existed on the approved master revision.

Maven Central metadata checked on 13 October 2026 showed Spring Boot 4.1.1 as
the latest non-preview release. The existing MP2 wrapper already pins Gradle
9.1.0 with its distribution checksum and supports the Java 25 toolchain.

## Decision

- Pin Java 25, Gradle 9.1.0 and Spring Boot 4.1.1.
- Use PostgreSQL in deployed environments and Flyway as the only schema owner.
- Use Spring Session JDBC with an explicit Secure, HttpOnly, SameSite=Lax,
  browser-scoped `SESSION` cookie.
- Use Argon2id with a 16-byte salt, 32-byte hash, parallelism 1, 64 MiB memory
  and three iterations. Parameters are configuration-pinned and may be raised
  through a reviewed decision and migration plan.
- Re-read persisted active state, role and session version for every
  authenticated request. Use service-level owner/assignment checks in addition
  to route rules.
- Require future AI endpoints to invoke `AiAccessGuard` before building
  protected context or calling the SoC LLM gateway.

## Alternatives considered

- In-memory sessions were rejected because stateless web executions must share
  authenticated sessions and deactivation/reset must take effect consistently.
- Route-only authorization was rejected because direct use-case calls and
  object-level access would remain vulnerable.
- BCrypt was considered, but Argon2id avoids BCrypt's input-byte limit and
  provides memory-hard hashing for the approved Unicode password range.
- Demo accounts in the first migration were rejected because production must
  never receive hard-coded credentials and the approved demo seed is atomic
  with representative Requests, which are not part of this slice.

## Consequences

The authentication contract is available for all role and AI slices. A fresh
database still needs the separately specified demo/production initializer or
Manager account-administration slice before interactive use. H2 PostgreSQL-mode
tests provide fast security coverage, but an actual PostgreSQL integration run
and browser verification remain release requirements.

## Approval evidence

The implementation follows approved specification revision `0778d6e`. Local
test evidence is recorded in the authentication session log. A teammate must
review and approve this decision and implementation before merge.
