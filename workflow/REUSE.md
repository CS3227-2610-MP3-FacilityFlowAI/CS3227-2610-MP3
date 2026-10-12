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
| _Add entries before merging reused work_ | | | | | | |

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

Detailed provenance of pre-existing documents and future concept-only reuse:
TBD after checking MP2. Preserve existing content until comparison supports
accurate classification.
