package sg.edu.nus.facilityflow.requestcore;

import java.util.Map;

public class RequestValidationException extends RuntimeException {
    private final Map<String, String> errors;

    public RequestValidationException(Map<String, String> errors) {
        super("Check the marked fields and try again.");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> errors() {
        return errors;
    }
}
