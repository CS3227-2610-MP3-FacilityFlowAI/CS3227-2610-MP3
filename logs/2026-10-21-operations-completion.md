# Submission operations completion

Requirements: NFR-REL-002, NFR-OPS-001/002, NFR-SEC-001, NFR-DEP-001.

User approved GitHub issue alerts and confirmed the independent encryption
passphrase saved in the production-backup GitHub environment. The backup role
uses its own password; setup instructions provided without collecting secrets.

Implemented external health monitoring with third-failure issue alerts/recovery,
synthetic alert exercise, encrypted 12-hour backup and disposable PostgreSQL
restore verification. Scheduled workflows require reviewed merge to master.
GitHub scheduling is best-effort; no guaranteed five-minute wall-clock SLA.

Validation: full Gradle check/bootJar passed, latest focused database isolation
and backup permission regression passed; nine Python tests passed; actionlint
v1.7.12 passed. Earlier concurrent Gradle invocation failed from a Windows test
report lock; rerunning sequentially passed. Fresh verifier reviewed fixes for
alert creation/recovery API partial failures and unexpected backup privileges.

Actual backup/restore, notification delivery, fresh Preview and protected rollback
rehearsal remain pending. No live database restore or production change was
performed by these local checks. Teammate review is required before merge.
