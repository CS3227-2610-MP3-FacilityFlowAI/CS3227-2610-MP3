# MP2 Reuse Ledger

## Baseline

- Source repository: `CS3227-2610-MP2-FacilityFlow/CS3227-2610-MP2`
- Verified source commit: `cbfeb8fec339d2708c15eae1884fbc9b69063908`
  (29 September 2026, `Prepare clean reproducible v1.0.1 release`)
- Target repository: `CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3`

The team reported on 10 October 2026 that the lecturer confirmed MP2 reuse is
allowed when the result is a deployed web application with a production
database and the team documents its reuse. See
[`ADR-001`](decisions/ADR-001-reuse-mp2-foundation.md). Every reused item must
be recorded before its pull request is merged.

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

No MP2 application source or tests are present in this repository yet.
Treatment rows above are plans, not completed reuse. The source commit was
verified locally on 10 October 2026. Existing planning documents reference MP2
concepts; their exact source sections and reused amount still need team
verification.

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
