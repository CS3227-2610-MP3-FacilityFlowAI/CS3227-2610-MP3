# Manager navigation correction

Requirements: REQ-SH-001, REQ-SH-012, NFR-DOC-001.

The authenticated `/manager` landing route rendered the retired generic
dashboard, while the complete Facilities Manager navigation and operational
overview were available only at `/manager/overview`. This made Accounts,
Categories, Skills, Audit and AI monitoring difficult to discover from the
normal role landing page.

The landing route now redirects to `/manager/overview`. The overview marks its
own navigation item as the current page and labels it `Overview`. Security
integration coverage verifies the redirect, and the existing Chrome verification
now checks that the resulting Manager navigation exposes Accounts, Categories
and Skills.

Verification on the clean `fix/manager-navigation` worktree:

- `SecurityIntegrationTest`: 45 tests passed.
- Five JavaScript test suites passed.
- `./gradlew clean check bootJar --no-daemon`: passed (9 tasks).
- `git diff --check`: passed.
- Impeccable UI static detector: no findings.

The browser verification source was updated but its opt-in live Chrome/PostgreSQL
run was not claimed in this session. A teammate review remains required before
merge. No database, secret, deployment or production change was made.
