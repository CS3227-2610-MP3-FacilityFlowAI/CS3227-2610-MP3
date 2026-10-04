# MP2 Reuse Ledger

## Baseline

- Source repository: `CS3227-2610-MP2-FacilityFlow/CS3227-2610-MP2`
- Planned source commit: `cbfeb8f` (29 September 2026)
- Target repository: `CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3`

The source commit must be verified before reuse begins. Every reused item must
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
