# MP2 Reuse Ledger

Status: current provenance and measurement, 22 October 2026. Human acceptance is
resolved by the stakeholder; see [Acceptance](../docs/Acceptance.md). Historical
increment logs remain evidence for their original changes, not pending release
measurements or current approval status.

## Baseline and method

- MP2 repository: `CS3227-2610-MP2-FacilityFlow/CS3227-2610-MP2`.
- Fixed source: `9819b857d880134c1fc1ffef8a8bcd5d6e536f43` (MP2 v1.0.1).
- MP3 source revision at measurement: `9dae03f80e4175f0e6ffa57f01de59e5b2700e01`
  plus the current acceptance/documentation/test working-tree changes.
- Counts are physical UTF-8 text lines, including comments and blank lines.
  Binary files are counted separately. CRLF and LF are equivalent for comparison.
- Read-only reproduction, from the MP3 repository root:

```text
python -B workflow/tools/measure-reuse.py --mp2 ../path-to-MP2 --output build/verification/reuse.json
```

Supply the actual MP2 checkout path. The script reads the fixed Git blobs without
checking out or modifying MP2. It includes tracked and non-ignored current MP3
files in the listed categories. Historical logs, generated build output, the
measurement record itself and docs/Acceptance.md are excluded from totals.

## Measured inventory

| Category | Files | Physical text lines | Binary files |
| --- | ---: | ---: | ---: |
| Application source and resources (`src/main/`) | 187 | 16,571 | 0 |
| Tests and fixtures (`src/test/`) | 77 | 15,629 | 0 |
| Build bootstrap (four Gradle wrapper files) | 4 | 349 | 1 |
| Project Markdown documentation, excluding historical logs and acceptance/reuse records | 57 | 7,319 | 0 |

Only `gradlew` (248 lines), `gradlew.bat` (93 lines) and the wrapper JAR match
complete MP2 baseline files after newline normalisation. The eight-line wrapper
properties file retains all eight key/value settings but reorders the checksum
entry, so it is adapted rather than byte-identical. Earlier descriptions of
all four wrapper files as unchanged are superseded by this comparison.

No complete application, test or documentation file matches an MP2 baseline
file. This exact-file result corroborates the implementation provenance below;
it is not proof that no partial text or domain concept was reused. We do not
present new MP3 line totals as copied MP2 lines or infer a copying percentage
from coincidental matching boilerplate.

## Reused material and acknowledgement

| MP2 source at the fixed baseline | MP3 destination / amount | Classification and treatment |
| --- | --- | --- |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` | Same paths: 341 text lines and one binary JAR | **Unchanged**, newline-normalised. Upstream Gradle-generated bootstrap retained from MP2; no business behaviour. |
| `gradle/wrapper/gradle-wrapper.properties` | Same path: eight retained settings / eight lines | **Adapted** ordering; Gradle 9.1.0 URL, checksum, timeout and validation settings retained. |
| `specs/product-requirements.md`, `specs/components/request-lifecycle.md` | `workflow/specs/PRODUCT-SPEC.md`, role services/repositories and lifecycle tests | **Concept only**: lifecycle states, role permissions, retained history and audit invariants. Zero MP2 application-code lines copied according to the implementation records. Spring/JDBC transactions and PostgreSQL persistence are new MP3 implementations. |
| `specs/roles/requester.md` | `workflow/specs/ROLE-REQUESTER.md`, `requester/`, role web templates/controllers and tests | **Concept only**: own-request reporting, editing, cancellation, updates and discovery. Zero copied MP2 implementation lines; web implementation and Smart Report integration are new. |
| `specs/roles/technician.md` | `workflow/specs/ROLE-TECHNICIAN.md`, `technician/`, role web templates/controllers and tests | **Concept only**: assigned queue, start/log/completion and retained evidence. Zero copied MP2 implementation lines; Work Plan, web UI and PostgreSQL implementation are new. |
| `specs/roles/facilities-manager.md` | `workflow/specs/ROLE-MANAGER.md`, `manager/`, role web templates/controllers and tests | **Concept only**: triage/assignment, lifecycle review, account/category administration and audit viewing. Zero copied MP2 implementation lines; skill matching, advisory Triage and shared observability are new. |
| `specs/components/authentication-authorization.md` | MP3 identity/web guards, shared password/navigation and role tests | **Concept only**: account, role, ownership, assignment, generic login-failure and password scenarios. Zero copied MP2 implementation lines; Spring Security/JDBC-session implementation is new. |
| `specs/components/persistence-observability.md`, `specs/acceptance-test-matrix.md` | PostgreSQL migrations/permissions, audit, integration fixtures and acceptance cases | **Concept only**: persistence/audit invariants and selected scenarios. Zero copied SQLite implementation or test lines; PostgreSQL/Flyway, isolated fixtures and operations automation are new. |
| Supplied MP2 `user-interface.md`, Draft v0.2 (12 September), corresponding to `specs/components/user-interface.md` | `workflow/specs/user-interface.md`: one adapted specification; UIX identifiers and the role/navigation, form/feedback, list/layout and accessibility sections retain provenance | **Adapted documentation**. JavaFX dialogs, account menu and split-pane presentation are superseded by explicit MP3 web requirements. CSS, templates and browser tests are new MP3 implementation, not copied JavaFX source. The adapted file's whole size is not claimed as verbatim copied lines. |
| MP2 application/tutorial implementation | None | JavaFX views, SQLite storage and desktop startup are not copied into MP3. Shared domain knowledge is acknowledged above rather than counted as source copying. |

## New MP3 work and other upstream sources

SoCLaaS transport/readiness, typed role contracts, disclosure/safety rules,
rolling quotas/leases, telemetry, Manager AI monitoring, web sessions, adaptive
web assets, production isolation, backup/restore/monitoring and GitHub Actions
are new MP3 implementation or adaptations of earlier MP3 code. Reusing an MP3
service in another MP3 role is not additional MP2 reuse.

The V1 session-table DDL adapts Spring Session JDBC 4.1.1's
`schema-postgresql.sql` (Apache-2.0), not MP2; see
[Database migrations](../docs/DatabaseMigrations.md). The public Supabase CA is
provider material, not MP2 application code. Normal library dependencies and
Gradle templates remain attributed to their upstream projects.

## Provenance evidence

- [Reuse decision](decisions/ADR-001-reuse-mp2-foundation.md).
- [Authentication implementation](../logs/2026-10-13-authentication-foundation.md).
- [Requester development](../logs/2026-10-14-requester-development-plan.md)
  and dated step 1–7 implementation records.
- [Technician T1](../logs/technician/T1-REPORT.md),
  [T3](../logs/technician/T3-REPORT.md), [T4](../logs/technician/T4-REPORT.md)
  and [T5](../logs/technician/T5-REPORT.md) include scoped line changes.
- [Manager implementation](../logs/2026-10-18-manager-lifecycle-administration.md).
- [Adaptive UI provenance](../logs/2026-10-19-responsive-ui.md).

These source records distinguish conceptual reuse, adapted documentation,
generated bootstrap and new implementation. Approximate conceptual reuse is
reported by named sections/features, not an unsupported percentage of Java code.
