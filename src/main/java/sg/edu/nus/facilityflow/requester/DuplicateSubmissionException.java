package sg.edu.nus.facilityflow.requester;

public class DuplicateSubmissionException extends RuntimeException {
    public DuplicateSubmissionException() {
        super("This form has already been submitted. Check My requests for the saved request.");
    }
}
