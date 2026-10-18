package sg.edu.nus.facilityflow.ai.manager;

/** Safe categories exposed by the Manager AI seam. */
public enum TriageFailureCode {
    AI_UNAVAILABLE,
    AI_LIMITED,
    AI_INVALID_REQUEST,
    AI_CONFIGURATION_ERROR,
    AI_TIMEOUT,
    AI_INVALID_OUTPUT,
    DISCARDED_ACCESS_CHANGED
}
