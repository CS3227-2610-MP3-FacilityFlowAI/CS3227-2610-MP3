# Source Code

The initial specification gate passed on 12 October 2026. Application and test
code may now be added as scoped implementation work under the approved baseline.
The planned implementation is a Spring Boot modular monolith with
Thymeleaf/HTMX, PostgreSQL, Spring Security, and a bounded SoC LLM gateway.

The first walking skeleton now provides PostgreSQL/Flyway account and Spring
Session tables, Spring Security form login, role workspaces, persisted-account
revalidation, service-level owner/assignment guards and an authorization-first
AI access seam. Security integration and unit tests live under `src/test`.

Account administration, demo/production bootstrap, maintenance workflows and
the three AI assistants remain separate follow-up slices. Do not bypass the
identity authorization or AI access guard when those slices are added.
