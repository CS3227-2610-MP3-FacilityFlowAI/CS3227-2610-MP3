package sg.edu.nus.facilityflow.requester;

public class StaleRequestException extends RuntimeException {
    public StaleRequestException() {
        super("This request changed or no longer allows this action. Reload its current details before trying again. Your input is below.");
    }
}
