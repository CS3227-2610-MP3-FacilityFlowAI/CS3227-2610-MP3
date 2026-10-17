# Deadline delegate correction

- Increment: `fix(ai): preserve absolute deadlines through feature delegates`
- Requirements: AI-SEC-008/009 and REQ-AI-005.
- Finding corrected: the Spring-facing `Delegating` feature seams inherited a
  default overload that called the one-argument method, which could reset the
  provider deadline. All three delegates now forward the absolute deadline.
- Focused verification: `./gradlew.bat test --tests
  sg.edu.nus.facilityflow.ai.AiFeatureContractTest --tests
  sg.edu.nus.facilityflow.technician.TechnicianWorkPlanServiceTest --no-daemon`
  — BUILD SUCCESSFUL.
- Review/approval: this correction responds to fresh specification review;
  Security Reviewer, teammate and fresh-verifier approval remain pending.
