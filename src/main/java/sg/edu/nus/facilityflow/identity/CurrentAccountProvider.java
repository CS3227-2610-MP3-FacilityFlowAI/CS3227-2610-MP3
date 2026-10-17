package sg.edu.nus.facilityflow.identity;

public interface CurrentAccountProvider {

    CurrentAccount requireCurrentAccount();

    /** Captures the authenticated principal identity for asynchronous revalidation. */
    default CurrentAccountSnapshot requireCurrentAccountSnapshot() {
        CurrentAccount account = requireCurrentAccount();
        return new CurrentAccountSnapshot(account.id(), account.role(), -1);
    }
}
