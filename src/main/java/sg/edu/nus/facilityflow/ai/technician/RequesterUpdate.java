package sg.edu.nus.facilityflow.ai.technician;

import java.time.Instant;
import java.text.Normalizer;
import java.util.Objects;
import java.util.UUID;

/** Server-loaded Requester Update used only for deterministic context selection. */
public record RequesterUpdate(UUID id, Instant createdAt, String note) {
    public RequesterUpdate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(note, "note");
        note = Normalizer.normalize(note.replace("\r\n", "\n").replace('\r', '\n'),
                Normalizer.Form.NFC).trim();
        if (note.isEmpty() || note.codePointCount(0, note.length()) > 1000
                || note.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                        && codePoint != '\n' && codePoint != '\t')) {
            throw new IllegalArgumentException("note is invalid");
        }
    }
}
