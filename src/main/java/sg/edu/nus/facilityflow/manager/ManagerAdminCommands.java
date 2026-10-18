package sg.edu.nus.facilityflow.manager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed inputs for REQ-MGR-013–019 Manager administration operations. */
public final class ManagerAdminCommands {
    private ManagerAdminCommands() { }

    public record CreateAccount(String username, String displayName, String role, String password) {
        public Map<String, String> errors() {
            var errors = new LinkedHashMap<String, String>();
            String normalized = username == null ? "" : username.strip();
            if (!normalized.matches("[A-Za-z0-9._-]{3,32}")) {
                errors.put("username", "Use 3–32 letters, numbers, dots, underscores or hyphens.");
            }
            text(errors, "displayName", displayName, 2, 80, "Enter a display name between 2 and 80 characters.");
            ManagerAdminCommands.role(errors, role);
            ManagerAdminCommands.password(errors, password);
            return Map.copyOf(errors);
        }
    }

    public record AccountState(String version, String active, String confirmed) {
        public Map<String, String> errors() {
            var errors = ManagerAdminCommands.version(version);
            if (!List.of("true", "false").contains(active)) errors.put("active", "Choose an account state.");
            if ("false".equals(active)) confirmation(errors, confirmed);
            return Map.copyOf(errors);
        }
    }

    public record AccountRole(String version, String role) {
        public Map<String, String> errors() {
            var errors = ManagerAdminCommands.version(version);
            ManagerAdminCommands.role(errors, role);
            return Map.copyOf(errors);
        }
    }

    public record PasswordReset(String version, String password) {
        public Map<String, String> errors() {
            var errors = ManagerAdminCommands.version(version);
            ManagerAdminCommands.password(errors, password);
            return Map.copyOf(errors);
        }
    }

    public record CatalogueName(String version, String name) {
        public Map<String, String> errors() {
            var errors = ManagerAdminCommands.version(version);
            text(errors, "name", name, 2, 50, "Enter a name between 2 and 50 characters.");
            return Map.copyOf(errors);
        }
    }

    public record CategoryRemoval(String version, String replacementId, String confirmed) {
        public Map<String, String> errors() {
            var errors = ManagerAdminCommands.version(version);
            uuid(errors, "replacementId", replacementId, "Choose a different active replacement category.");
            confirmation(errors, confirmed);
            return Map.copyOf(errors);
        }
    }

    public record TechnicianSkills(String version, List<String> skillIds) {
        public TechnicianSkills {
            skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
        }

        public Map<String, String> errors() {
            var errors = ManagerAdminCommands.version(version);
            for (String id : skillIds) uuid(errors, "skillIds", id, "Choose only active skills.");
            return Map.copyOf(errors);
        }
    }

    public record SkillDeactivation(String version, String confirmed) {
        public Map<String, String> errors() {
            var errors = ManagerAdminCommands.version(version);
            confirmation(errors, confirmed);
            return Map.copyOf(errors);
        }
    }

    static long parsedVersion(String raw) {
        var errors = version(raw);
        if (!errors.isEmpty()) throw new ManagerValidationException(errors);
        return Long.parseLong(raw);
    }

    private static LinkedHashMap<String, String> version(String raw) {
        var errors = new LinkedHashMap<String, String>();
        try {
            if (raw == null || Long.parseLong(raw) < 0) throw new NumberFormatException();
        } catch (RuntimeException exception) {
            errors.put("version", "This item changed after the page loaded. Refresh and try again.");
        }
        return errors;
    }

    private static void role(Map<String, String> errors, String role) {
        if (!List.of("REQUESTER", "TECHNICIAN", "FACILITIES_MANAGER").contains(role)) {
            errors.put("role", "Choose a valid role.");
        }
    }

    private static void password(Map<String, String> errors, String password) {
        text(errors, "password", password, 8, 24, "Use a password between 8 and 24 characters.");
    }

    private static void text(Map<String, String> errors, String field, String value,
            int minimum, int maximum, String message) {
        String normalized = value == null ? "" : value.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < minimum || length > maximum) errors.put(field, message);
    }

    private static void uuid(Map<String, String> errors, String field, String value, String message) {
        try { java.util.UUID.fromString(value == null ? "" : value); }
        catch (RuntimeException exception) { errors.put(field, message); }
    }

    private static void confirmation(Map<String, String> errors, String confirmed) {
        if (!"true".equals(confirmed)) {
            errors.put("confirmed", "Confirm this destructive action before continuing.");
        }
    }
}
