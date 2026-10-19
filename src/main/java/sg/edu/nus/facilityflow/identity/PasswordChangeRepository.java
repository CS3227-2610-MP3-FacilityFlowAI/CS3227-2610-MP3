package sg.edu.nus.facilityflow.identity;

/** Atomic own-password persistence, including locked account revalidation and audit. */
public interface PasswordChangeRepository {
    void changeOwnPassword(CurrentAccountSnapshot actor, String currentPassword, String newPassword);
}
