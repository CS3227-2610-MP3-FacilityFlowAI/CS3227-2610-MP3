# Source Code

The approved specification baseline defines a Spring Boot modular monolith.
The integrated foundation provides private PostgreSQL storage through Flyway
V1-V3, explicit demo/Manager initialization, Spring Security login and role
workspaces, JDBC sessions, persisted-account revalidation, owner/assignment
service guards and an authorization-first AI access seam.

Applied migrations remain unchanged. Authentication queries the existing private
schema and supports seeded PBKDF2 hashes alongside new Argon2 hashes. See
`docs/DeveloperGuide.md`, `docs/DatabaseCompletion.md` and `workflow/TRACEABILITY.md`.
Business workflows, account administration and AI assistants remain pending.
