package sg.edu.nus.facilityflow.identity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/** REQ-SH-002/004: the server captures the actor and preserves their current session. */
@Service
public class PasswordChangeService {
    private final CurrentAccountProvider currentAccounts;
    private final PasswordChangeRepository repository;

    public PasswordChangeService(CurrentAccountProvider currentAccounts, PasswordChangeRepository repository) {
        this.currentAccounts = Objects.requireNonNull(currentAccounts, "currentAccounts");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void changeOwnPassword(String currentPassword, String newPassword, String confirmPassword) {
        try {
            CurrentAccountSnapshot actor = currentAccounts.requireCurrentAccountSnapshot();
            requireSnapshot(actor);
            Map<String, String> errors = inputErrors(currentPassword, newPassword);
            if (confirmPassword == null || confirmPassword.isEmpty() || !confirmPassword.equals(newPassword)) {
                errors.put("confirmPassword", "Confirm the new password exactly.");
            }
            requireValid(errors);
            repository.changeOwnPassword(actor, currentPassword, newPassword);
        } catch (PasswordChangeException failure) {
            throw failure;
        } catch (AuthenticationException | AccessDeniedException failure) {
            throw new PasswordChangeException(PasswordChangeException.Code.ACCESS_CHANGED);
        } catch (RuntimeException failure) {
            throw new PasswordChangeException(PasswordChangeException.Code.UNAVAILABLE);
        }
    }

    static void requireSnapshot(CurrentAccountSnapshot actor) {
        if (actor == null || actor.sessionVersion() < 0) {
            throw new PasswordChangeException(PasswordChangeException.Code.ACCESS_CHANGED);
        }
    }

    // Shared by the persistence seam so direct callers cannot bypass pre-hash validation.
    static void validatePasswords(String currentPassword, String newPassword) {
        requireValid(inputErrors(currentPassword, newPassword));
    }

    private static Map<String, String> inputErrors(String currentPassword, String newPassword) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!hasLength(currentPassword, 1, 24)) {
            errors.put("currentPassword", "Enter your current password using 1 to 24 Unicode code points.");
        }
        if (!hasLength(newPassword, 8, 24)) {
            errors.put("newPassword", "Enter a new password using 8 to 24 Unicode code points.");
        }
        return errors;
    }

    private static boolean hasLength(String value, int minimum, int maximum) {
        if (value == null) {
            return false;
        }
        int length = value.codePointCount(0, value.length());
        return length >= minimum && length <= maximum;
    }

    private static void requireValid(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new PasswordChangeException(PasswordChangeException.Code.INVALID_INPUT, errors);
        }
    }
}
