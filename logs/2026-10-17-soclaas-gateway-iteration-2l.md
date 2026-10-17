# SoCLaaS gateway Iteration 2L — Manager Triage slice

## Scope

Implemented the Manager Triage provider vertical slice on top of the shared
gateway contracts. No assignment, state transition or catalogue mutation path
was added.

## Delivered

- Added the provider-owned `SocLlmTriageAssistant` implementation.
- Builds a versioned two-message prompt from the selected Request fields, up to
  ten caller-selected Requester Updates, active category labels, active skill
  labels and fixed output rules only.
- Enforces the aggregate 12,000-code-point prompt limit before provider use.
- Strictly parses category, priority, clarification-question and skill-tag
  output, rejecting unknown fields, duplicate/trailing content, inactive labels,
  duplicate/overlapping tags, markup and identifiers.
- Maps provider failures to safe Triage failure categories and returns no
  partial recommendation.
- Wires Triage into available provider runtimes while preserving the inert
  disabled/unavailable lifecycle behavior.

## Verification

- `gradlew.bat test --tests 'sg.edu.nus.facilityflow.ai.provider.SocLlmTriageAssistantTest' --no-daemon` — passed.
- `gradlew.bat test --tests 'sg.edu.nus.facilityflow.ai.provider.*' --tests 'sg.edu.nus.facilityflow.ai.AiFeatureContractTest' --tests 'sg.edu.nus.facilityflow.technician.TechnicianWorkPlanServiceTest' --no-daemon` — passed.

The deterministic Manager ranking and ordinary assignment operation remain
outside the AI provider seam and are the next integration concern.
