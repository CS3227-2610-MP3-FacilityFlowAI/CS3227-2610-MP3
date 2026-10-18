# Triage result contract increment

- Increment: `fix(manager-ai): enforce the Triage result contract`
- Requirements: REQ-MGR-022/023, AI-SEC-010/011/016.
- Change: the provider parser now explicitly enforces Low/Medium/High/Critical
  priority values, unique required and optional skill lists, and disjoint
  required/optional tags. The typed result remains the ranker's trust boundary,
  so duplicate tags cannot inflate optional-match counts.
- Focused verification: `./gradlew.bat test --tests
  sg.edu.nus.facilityflow.ai.provider.SocLlmTriageAssistantTest --tests
  sg.edu.nus.facilityflow.manager.TriageCandidateRankerTest --no-daemon` —
  BUILD SUCCESSFUL.
- Review/approval: provider/ranker behavior is locally verified; Manager
  orchestration and v0.2 role-owner/teammate review remain pending.
- Corrections/risks: no assignment or Request mutation is introduced.
