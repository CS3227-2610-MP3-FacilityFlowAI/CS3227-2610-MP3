package sg.edu.nus.facilityflow.identity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Safe failures for own-password changes; submitted values and operational causes are discarded. */
public final class PasswordChangeException extends RuntimeException {
    public enum Code { INVALID_INPUT, INCORRECT_CURRENT_PASSWORD, ACCESS_CHANGED, UNAVAILABLE }

    private final Code code;
    private final Map<String, String> fieldErrors;

    public PasswordChangeException(Code code) {
        this(code, Map.of());
    }

    public PasswordChangeException(Code code, Map<String, String> fieldErrors) {
        super(switch (Objects.requireNonNull(code, "code")) {
            case INVALID_INPUT -> "Check the password fields and try again.";
            case INCORRECT_CURRENT_PASSWORD -> "The current password is incorrect.";
            case ACCESS_CHANGED -> "Your account access has changed. Sign in again.";
            case UNAVAILABLE -> "Password changes are temporarily unavailable. Please try again.";
        }, null);
        this.code = code;
        // Treat map values as untrusted: only known keys and constant messages can escape.
        Map<String, String> safeErrors = new LinkedHashMap<>();
        if (fieldErrors != null && code == Code.INVALID_INPUT) {
            for (String field : new String[] { "currentPassword", "newPassword", "confirmPassword" }) {
                if (fieldErrors.containsKey(field)) {
                    safeErrors.put(field, fieldMessage(field));
                }
            }
        } else if (fieldErrors != null && code == Code.INCORRECT_CURRENT_PASSWORD
                && fieldErrors.containsKey("currentPassword")) {
            safeErrors.put("currentPassword", "The current password is incorrect.");
        }
        this.fieldErrors = Map.copyOf(safeErrors);
    }

    public Code code() {
        return code;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }

    private static String fieldMessage(String field) {
        return switch (field) {
            case "currentPassword" -> "Enter your current password using 1 to 24 Unicode code points.";
            case "newPassword" -> "Enter a new password using 8 to 24 Unicode code points.";
            case "confirmPassword" -> "Confirm the new password exactly.";
            default -> throw new IllegalArgumentException("Unknown password field");
        };
    }
}
