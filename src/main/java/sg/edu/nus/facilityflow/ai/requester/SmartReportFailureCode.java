package sg.edu.nus.facilityflow.ai.requester;

/** Safe categories exposed by the Requester AI seam. */
public enum SmartReportFailureCode {
    AI_UNAVAILABLE,
    AI_LIMITED,
    AI_INVALID_REQUEST,
    AI_CONFIGURATION_ERROR,
    AI_TIMEOUT,
    AI_INVALID_OUTPUT,
    DISCARDED_ACCESS_CHANGED
}
