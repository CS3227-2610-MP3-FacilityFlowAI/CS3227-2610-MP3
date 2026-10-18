package sg.edu.nus.facilityflow.manager;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public final class ManagerRequestNotFoundException extends RuntimeException {
    public ManagerRequestNotFoundException() {
        super("The requested maintenance request was not found");
    }
}
