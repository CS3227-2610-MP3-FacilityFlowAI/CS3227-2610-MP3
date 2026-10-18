package sg.edu.nus.facilityflow.manager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/** Opaque freshness token for REQ-MGR-025 catalogue, skill and ranking inputs. */
final class TriageSnapshotFingerprint {
    private TriageSnapshotFingerprint() { }

    static String create(List<ManagerTriageRepository.CatalogueSnapshot> categories,
            List<ManagerTriageRepository.CatalogueSnapshot> skills,
            List<TechnicianCandidate> technicians) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            categories.stream().sorted(Comparator.comparing(value -> value.id().toString()))
                    .forEach(value -> add(digest, "category", value.id().toString(), value.name(),
                            Long.toString(value.version())));
            skills.stream().sorted(Comparator.comparing(value -> value.id().toString()))
                    .forEach(value -> add(digest, "skill", value.id().toString(), value.name(),
                            Long.toString(value.version())));
            technicians.stream().sorted(Comparator.comparing(value -> value.accountId().toString()))
                    .forEach(value -> {
                        add(digest, "technician", value.accountId().toString(), value.username(),
                                Boolean.toString(value.active()), Integer.toString(value.workload()));
                        value.skillTags().stream().sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo))
                                .forEach(skill -> add(digest, "technician-skill", skill));
                    });
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void add(MessageDigest digest, String... values) {
        for (String value : values) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            digest.update((byte) (bytes.length >>> 24));
            digest.update((byte) (bytes.length >>> 16));
            digest.update((byte) (bytes.length >>> 8));
            digest.update((byte) bytes.length);
            digest.update(bytes);
        }
    }
}
