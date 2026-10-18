package sg.edu.nus.facilityflow.technician;

import java.util.Objects;

/** Safe failure vocabulary; messages never include persistence or inaccessible data. */
public final class TechnicianQueryException extends RuntimeException {
    public enum Code { INVALID_FILTER, NOT_AVAILABLE, UNAVAILABLE }

    private final Code code;

    public TechnicianQueryException(Code code) {
        super(switch (Objects.requireNonNull(code, "code")) {
            case INVALID_FILTER -> "Check the selected filters and try again.";
            case NOT_AVAILABLE -> "The requested work is not available.";
            case UNAVAILABLE -> "Assigned work is temporarily unavailable. Please try again.";
        });
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
