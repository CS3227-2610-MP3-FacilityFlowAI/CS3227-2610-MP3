package sg.edu.nus.facilityflow.requester;

public class RequestUnavailableException extends RuntimeException {
    public RequestUnavailableException() {
        super("Request unavailable.");
    }
}
