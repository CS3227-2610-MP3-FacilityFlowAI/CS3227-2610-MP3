# MP2 Reuse Ledger

## Baseline

- Source repository: `CS3227-2610-MP2-FacilityFlow/CS3227-2610-MP2`
- Verified source commit: `9819b857d880134c1fc1ffef8a8bcd5d6e536f43`
  (29 September 2026, MP2 v1.0.1 merge on `master`)
- Target repository: `CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3`

The source commit was verified against the MP2 `master` branch on 12 October
2026. Every reused item must be recorded before its pull request is merged.

## Classifications

- **Unchanged:** copied without behavior changes.
- **Adapted:** derived from MP2 with material changes for the web architecture.
- **Concept only:** domain knowledge or scenarios reused, but implementation is
  new.
- **New:** no MP2 source was used.

## Ledger

| MP2 source | MP3 destination | Classification | Reused lines | Changed/new lines | Reason and acknowledgement | Pull request |
| --- | --- | --- | ---: | ---: | --- | --- |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` at verified MP2 commit | same wrapper paths | Unchanged | 349 text lines plus one binary wrapper JAR | 0 | Reuse the checksum-pinned Gradle 9.1.0 bootstrap already used by the team; no application behavior reused | `feat/authentication-foundation` (review pending) |
| `specs/components/authentication-authorization.md` at verified MP2 commit | MP3 identity/web/AI guard behavior and tests under `src/` | Concept only | 0 copied code lines | 1,149 new source/resource/test lines | Adapt account, generic-login-failure, role, ownership, assignment and deactivation scenarios to Spring Security/JDBC/PostgreSQL; JavaFX session implementation not copied | `feat/authentication-foundation` (review pending) |

## Planned treatment

| MP2 asset | MP3 treatment |
| --- | --- |
| Lifecycle states and transition rules | Reuse behavior; adapt tested Java domain logic |
| Role permissions and audit semantics | Reuse requirements and selected service checks |
| Domain models and validation | Adapt selected Java classes for Spring and PostgreSQL |
| Tests and fixtures | Reuse scenarios; adapt code to Spring integration tests |
| JavaFX interface | Do not reuse |
| SQLite storage | Reuse schema concepts only; PostgreSQL/Flyway implementation is new |
| Documentation | Reuse factual domain descriptions with acknowledgement; rewrite for the released web product |

## Current evidence and measurement detail

No MP2 application source/tests are present at specification time. Treatment
rows above are plans, not completed code reuse. MP2 specification concepts were
consulted for the MP3 draft; exact source sections and actual reused amounts
must be recorded before each implementing pull request is merged.

Each ledger entry must record component/section, original commit/location,
target, classification (unchanged, adapted, concept-only or new), actual MP3
changes, approximate amount with units, justification, acknowledgement and
comparison evidence. Separate code, test and documentation totals; do not submit
planned estimates as actual reuse percentages.

| Component / section | Source / location | Target | Classification | Changes for MP3 | Approximate amount / units | Justification | Acknowledgement / evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Initial MP3 scaffolding from the 6 October 2026 setup session | Current user request and existing MP3 guidance; no MP2 files consulted | New scaffolds and documentation additions listed in logs/2026-10-06-repository-scaffolding.md | New | Initial process/placeholders | File inventory in session report; no MP2 copied lines identified | Establish requested workflow | AI-assisted with Codex; human review pending |
| Authentication/authorization behavior | MP2 `specs/components/authentication-authorization.md`, verified source commit above | `src/main/java/sg/edu/nus/facilityflow/identity`, `src/main/java/sg/edu/nus/facilityflow/web`, `src/main/java/sg/edu/nus/facilityflow/ai/AiAccessGuard.java`, and related tests | Concept only | 0 copied code lines; 1,149 new source/resource/test lines | Replace JavaFX/in-memory sessions with Spring Security, JDBC sessions, PostgreSQL/Flyway contracts and authorization-before-AI; reuse named scenarios and invariants only | AI-assisted implementation; `git diff`/line count and test log dated 13 October 2026; teammate review pending |
| Gradle wrapper bootstrap | MP2 `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties`, verified source commit above | Same MP3 paths | Unchanged | 349 text lines and one binary JAR unchanged | Preserve checksum-pinned Gradle 9.1.0 wrapper; dependency/build definitions in `build.gradle` and `settings.gradle` are new | File hashes/diff available on review branch; teammate review pending |

Detailed provenance of pre-existing documents and future concept-only reuse:
TBD after checking MP2. Preserve existing content until comparison supports
accurate classification.
