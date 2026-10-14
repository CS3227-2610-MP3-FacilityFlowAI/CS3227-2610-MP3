# Source Code

The initial specification gate passed on 12 October 2026. Application and test
code may now be added as scoped implementation work under the approved baseline.
The planned implementation is a Spring Boot modular monolith with
Thymeleaf/HTMX, PostgreSQL, Spring Security, and a bounded SoC LLM gateway.

Part 1 adds the application entry point, source configuration and a startup
test. Part 2a adds PostgreSQL JDBC configuration and a read-only connection checker;
user-reported Supabase runtime connectivity succeeded. This scaffold has no HTTP
server, authentication, business operations or AI features. Part 2b supplies
versioned account/audit/session storage and isolated migration tests; follow
docs/DatabaseMigrations.md to apply it to the development database.
See the Developer Guide for commands and ADR-003 for pinned versions.

Part 3 now supplies V2/V3 for all remaining shared storage and an explicit
database-initialize command for hashed demo/bootstrap Accounts. See
docs/DatabaseCompletion.md and ADR-006. Hosted V2/V3 application, login, role
services, AI calls and release operations are not yet verified/implemented.
