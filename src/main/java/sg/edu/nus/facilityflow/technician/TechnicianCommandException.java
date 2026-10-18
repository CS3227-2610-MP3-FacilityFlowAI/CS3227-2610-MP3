package sg.edu.nus.facilityflow.technician;

import java.util.Objects;

/** Safe command failures: no stored text, SQL, connection details or nested causes. */
public final class TechnicianCommandException extends RuntimeException {
    public enum Code { INVALID_INPUT, WORK_LOG_REQUIRED, NOT_AVAILABLE, CONFLICT, UNAVAILABLE }

    private final Code code;

    public TechnicianCommandException(Code code) {
        super(switch (Objects.requireNonNull(code, "code")) {
            case INVALID_INPUT -> "Check the submitted values and try again.";
            case WORK_LOG_REQUIRED -> "Add a Work Log before completion.";
            case NOT_AVAILABLE -> "The requested work is not available.";
            case CONFLICT -> "The work has changed. Refresh the page before trying again.";
            case UNAVAILABLE -> "Work changes are temporarily unavailable. Please try again.";
        });
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
