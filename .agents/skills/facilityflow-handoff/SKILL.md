---
name: facilityflow-handoff
description: Create a secure FacilityFlow project handoff when work is ready to pass to another specialized agent or session, or when unfinished work must pause for a fresh agent to continue.
---

# FacilityFlow Handoff

Follow the repository handoff protocol in `workflow/agents/README.md` and use
`workflow/templates/agent-handoff-template.md` for the durable record.

1. Identify the completed scope, the receiving agent or session, and the next
   authorized task. Preserve unresolved choices instead of deciding them.
2. Write a compact temporary handoff under the operating system's temporary
   directory. Include suggested skills for the receiver.
3. Write the durable project handoff under `logs/handoffs/` using the repository
   template. Reference specifications, decisions, commits, diffs, tests, and
   other existing artifacts by path or URL instead of copying their contents.
4. Record the approved revision or requirement IDs, changed artifacts, verified
   results, unverified claims, assumptions, unresolved items, risks, and next
   authorized action. State tests as executed only when evidence exists.
5. Remove secrets, credentials, unnecessary personal data, and raw sensitive
   model input or output from both handoffs.
6. Confirm that the handoff does not grant the receiver broader permissions than
   the repository rules or the user's authorization.

The handoff is complete when a fresh agent can identify the authoritative
artifacts, verified state, unresolved work, and permission boundary without
re-reading the full conversation.
