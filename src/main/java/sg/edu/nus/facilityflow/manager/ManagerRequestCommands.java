package sg.edu.nus.facilityflow.manager;

import java.util.LinkedHashMap;
import java.util.Map;

/** Typed inputs for REQ-MGR-004–012 named Manager lifecycle operations. */
public final class ManagerRequestCommands {
    private ManagerRequestCommands() { }

    public record Assignment(String version, String technicianId, String managerPriority) {
        public Map<String, String> errors() {
            var errors = base(version);
            uuid(errors, "technicianId", technicianId, "Choose an active Technician.");
            oneOf(errors, "managerPriority", managerPriority,
                    new String[]{"LOW", "MEDIUM", "HIGH", "CRITICAL"}, "Choose a Manager priority.");
            return Map.copyOf(errors);
        }
    }

    public record Reassignment(String version, String technicianId, String reason) {
        public Map<String, String> errors() {
            var errors = base(version);
            uuid(errors, "technicianId", technicianId, "Choose a different active Technician.");
            text(errors, "reason", reason, 5, 500, "Enter a reason between 5 and 500 characters.");
            return Map.copyOf(errors);
        }
    }

    public record Cancellation(String version, String reason, String confirmed) {
        public Map<String, String> errors() {
            var errors = base(version);
            text(errors, "reason", reason, 5, 500, "Enter a reason between 5 and 500 characters.");
            confirmation(errors, confirmed);
            return Map.copyOf(errors);
        }
    }

    public record ReturnCompleted(String version, String technicianId, String reason) {
        public Map<String, String> errors() {
            var errors = base(version);
            uuid(errors, "technicianId", technicianId, "Choose an active Technician.");
            text(errors, "reason", reason, 5, 500, "Enter a reason between 5 and 500 characters.");
            return Map.copyOf(errors);
        }
    }

    public record Reopen(String version, String technicianId, String reason) {
        public Map<String, String> errors() {
            var errors = base(version);
            uuid(errors, "technicianId", technicianId, "Choose an active Technician.");
            text(errors, "reason", reason, 5, 500, "Enter a reason between 5 and 500 characters.");
            return Map.copyOf(errors);
        }
    }

    public record Correction(String version, String title, String description, String location,
            String categoryId, String reportedUrgency, String managerPriority, String reason) {
        public Map<String, String> errors() {
            var errors = base(version);
            text(errors, "title", title, 5, 100, "Enter a title between 5 and 100 characters.");
            text(errors, "description", description, 10, 2000,
                    "Enter a description between 10 and 2,000 characters.");
            text(errors, "location", location, 2, 120, "Enter a location between 2 and 120 characters.");
            uuid(errors, "categoryId", categoryId, "Choose an active category.");
            oneOf(errors, "reportedUrgency", reportedUrgency,
                    new String[]{"LOW", "NORMAL", "HIGH", "EMERGENCY"}, "Choose a reported urgency.");
            if (managerPriority != null && !managerPriority.isBlank()) {
                oneOf(errors, "managerPriority", managerPriority,
                        new String[]{"LOW", "MEDIUM", "HIGH", "CRITICAL"}, "Choose a valid Manager priority.");
            }
            text(errors, "reason", reason, 5, 500, "Enter a correction reason between 5 and 500 characters.");
            return Map.copyOf(errors);
        }
    }

    public record TriageDecision(String version, String snapshotFingerprint, String categoryId,
            String managerPriority, String technicianId, java.util.List<String> acceptedRequiredSkills,
            java.util.List<String> acceptedOptionalSkills) {
        public TriageDecision {
            acceptedRequiredSkills = acceptedRequiredSkills == null ? java.util.List.of() : java.util.List.copyOf(acceptedRequiredSkills);
            acceptedOptionalSkills = acceptedOptionalSkills == null ? java.util.List.of() : java.util.List.copyOf(acceptedOptionalSkills);
        }

        public Map<String, String> errors() {
            var errors = base(version);
            if (snapshotFingerprint == null || !snapshotFingerprint.matches("[A-Za-z0-9_-]{43}")) {
                errors.put("version", "This recommendation is stale. Generate a fresh suggestion.");
            }
            uuid(errors, "categoryId", categoryId, "Choose an active category.");
            uuid(errors, "technicianId", technicianId, "Choose an active Technician.");
            oneOf(errors, "managerPriority", managerPriority,
                    new String[]{"LOW", "MEDIUM", "HIGH", "CRITICAL"}, "Choose a Manager priority.");
            acceptedRequiredSkills.forEach(value -> text(errors, "acceptedRequiredSkills", value,
                    2, 50, "Choose only active required skills."));
            acceptedOptionalSkills.forEach(value -> text(errors, "acceptedOptionalSkills", value,
                    2, 50, "Choose only active optional skills."));
            return Map.copyOf(errors);
        }
    }

    static long version(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value < 0) throw new NumberFormatException();
            return value;
        } catch (RuntimeException exception) {
            throw new ManagerValidationException(Map.of("version",
                    "This request changed after the page was loaded. Refresh and try again."));
        }
    }

    private static LinkedHashMap<String, String> base(String version) {
        var errors = new LinkedHashMap<String, String>();
        try {
            if (version == null || Long.parseLong(version) < 0) throw new NumberFormatException();
        } catch (RuntimeException exception) {
            errors.put("version", "This request changed after the page was loaded. Refresh and try again.");
        }
        return errors;
    }

    private static void uuid(Map<String, String> errors, String field, String value, String message) {
        try {
            java.util.UUID.fromString(value == null ? "" : value);
        } catch (RuntimeException exception) {
            errors.put(field, message);
        }
    }

    private static void oneOf(Map<String, String> errors, String field, String value,
            String[] accepted, String message) {
        if (value == null || java.util.Arrays.stream(accepted).noneMatch(value::equals)) {
            errors.put(field, message);
        }
    }

    private static void text(Map<String, String> errors, String field, String value,
            int minimum, int maximum, String message) {
        String normalized = value == null ? "" : value.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < minimum || length > maximum) errors.put(field, message);
    }

    private static void confirmation(Map<String, String> errors, String confirmed) {
        if (!"true".equals(confirmed)) {
            errors.put("confirmed", "Confirm this destructive action before continuing.");
        }
    }
}
