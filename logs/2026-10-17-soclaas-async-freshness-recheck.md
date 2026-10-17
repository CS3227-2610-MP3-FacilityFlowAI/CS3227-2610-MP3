# SoCLaaS asynchronous freshness recheck

- Source: live Work Plan response reported as `DISCARDED_ACCESS_CHANGED`, followed
  by local code inspection and a deterministic regression test.
- Root cause: the successful provider future completes on a non-request thread,
  but the post-call path attempted to read Spring Security's thread-local current
  principal. Missing thread-local authentication was incorrectly classified as
  changed access.
- Fix: rely on the persisted Work Plan eligibility recheck, which verifies the
  captured Technician assignment, active Account role, Request status and Request
  version after provider completion. The authorization-first check before context
  loading and provider use remains unchanged.
- Secrets and protected Request data were not recorded.
- Verification: the new asynchronous-completion regression failed before the
  production change and passed afterward. The focused Work Plan service tests and
  the full `gradlew.bat test` suite passed locally.
