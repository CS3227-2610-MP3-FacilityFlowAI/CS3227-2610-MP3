package sg.edu.nus.facilityflow.manager;

import java.util.Map;

public class ManagerValidationException extends RuntimeException {
    private final Map<String, String> errors;

    public ManagerValidationException(Map<String, String> errors) {
        super("Review the highlighted fields.");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> errors() {
        return errors;
    }
}
