package sg.edu.nus.facilityflow.ai.technician;

/** Stable safe categories returned by the Technician Work Plan Assistant. */
public enum WorkPlanFailureCode {
    AI_UNAVAILABLE,
    AI_LIMITED,
    AI_INVALID_REQUEST,
    AI_CONFIGURATION_ERROR,
    AI_TIMEOUT,
    AI_INVALID_OUTPUT,
    DISCARDED_ACCESS_CHANGED
}
