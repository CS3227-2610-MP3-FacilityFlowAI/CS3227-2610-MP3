# SoCLaaS gateway Iteration 2K — Requester Smart Report slice

## Scope

Implemented the first role-specific feature vertical slice on top of the shared
gateway contracts. No Request persistence or automatic submission path was
added.

## Delivered

- Added the provider-owned `SocLlmSmartReportAssistant` implementation.
- Builds a versioned two-message prompt from rough title/description/location,
  selected values, active category labels and fixed field rules only.
- Enforces the 12,000-code-point serialized prompt limit before provider use.
- Strictly parses the Smart Report schema with duplicate-key/trailing-content,
  unknown-field, bounds, active-category, urgency, markup and identifier checks.
- Maps provider failures to the Smart Report safe failure categories and returns
  no partial result on malformed, unsafe or unavailable output.
- Wires the implementation into available provider runtimes while preserving
  inert behavior for disabled/unavailable configuration.

## Verification

- `gradlew.bat test --tests 'sg.edu.nus.facilityflow.ai.provider.SocLlmSmartReportAssistantTest' --no-daemon` — passed.
- `gradlew.bat test --tests 'sg.edu.nus.facilityflow.ai.provider.*' --tests 'sg.edu.nus.facilityflow.ai.AiFeatureContractTest' --tests 'sg.edu.nus.facilityflow.technician.TechnicianWorkPlanServiceTest' --no-daemon` — passed.

The ordinary Requester form/persistence endpoint remains the next integration
step; this increment only exposes the typed advisory provider seam.
