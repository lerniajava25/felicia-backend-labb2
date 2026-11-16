package se.backend.labb2.helpdesk.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * "This doesn't exist, or you may not see it." Always the same fixed message,
 * so the response never reveals which of the two it was.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("Not found");
    }
}
