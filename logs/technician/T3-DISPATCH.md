# Technician T3 dispatch

- User approval: next iteration after T3preview; completion REQ-TECH007/008/010/011 andsharedrules, baseline9ba7009 cleanbranch technician-workflow.
- [Contract](T3-CONTRACT.md), [backend](T3-backend-task.md), [UI](T3-ui-task.md), [tester](T3-tests-task.md) fixedbeforeimplementation.
- Concurrentfreshauthors: backendgpt-6.1-sol/medium, UIgpt-6-luna/high, customtestergpt-6-luna/high. Freshreviewergpt-6.1-sol/high onlyafter allauthoring/preflightpass; maximum3workers+coordinator. No leafdelegation/Git/builds.
- Coordinator solecompile/test runner; owns existingSecurityIntegrationTest, docs/traceability/reuse, finalcandidatepatch/hashes, exactGitcommit/report. Metricsconversationonly; handoffs/evidence omitusage summaries.
- Exit: validcompletionwithlog+resolution, retainassignment/metadata/history, safeauditatomicity, missinglog/invalid/stale/concurrent/rollback checks, onebatchedindependentreview, fullcheck/bootJar, scopedlocalcommit. NoT4,password,Managerworkflow,migration/dependency,push/merge/deploy.
