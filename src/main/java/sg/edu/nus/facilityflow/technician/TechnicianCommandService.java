package sg.edu.nus.facilityflow.technician;

import java.util.Objects;
import org.springframework.stereotype.Service;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.identity.IdentityAuthorization;

/** Explicit manual commands; actors always originate from server-side identity. */
@Service
public class TechnicianCommandService {
    private final IdentityAuthorization authorization;
    private final TechnicianCommandRepository repository;

    public TechnicianCommandService(IdentityAuthorization authorization, TechnicianCommandRepository repository) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void startWork(String displayId, long expectedVersion) {
        CurrentAccountSnapshot actor = authorization.requireRoleSnapshot(AccountRole.TECHNICIAN);
        validateRequest(displayId, expectedVersion);
        repository.startWork(actor, displayId, expectedVersion);
    }

    public void appendWorkLog(String displayId, long expectedVersion, String note, int minutes) {
        CurrentAccountSnapshot actor = authorization.requireRoleSnapshot(AccountRole.TECHNICIAN);
        validateRequest(displayId, expectedVersion);
        String normalizedNote = normalizeNote(note, minutes);
        repository.appendWorkLog(actor, displayId, expectedVersion, normalizedNote, minutes);
    }

    // The persistence seam invokes these checks itself as well; it does not trust service callers.
    static void validateRequest(String displayId, long expectedVersion) {
        if (displayId == null || !displayId.matches("FF-[0-9]{6}") || displayId.equals("FF-000000")) {
            throw new TechnicianCommandException(TechnicianCommandException.Code.NOT_AVAILABLE);
        }
        if (expectedVersion < 0) {
            throw new TechnicianCommandException(TechnicianCommandException.Code.INVALID_INPUT);
        }
    }

    static String normalizeNote(String note, int minutes) {
        if (note == null || minutes < 1 || minutes > 1440) {
            throw new TechnicianCommandException(TechnicianCommandException.Code.INVALID_INPUT);
        }
        String normalized = note.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 1000) {
            throw new TechnicianCommandException(TechnicianCommandException.Code.INVALID_INPUT);
        }
        return normalized;
    }
}
