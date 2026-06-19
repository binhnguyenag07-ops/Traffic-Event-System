package team.traffic.exception;

import java.io.IOException;

public class InvalidMapException extends IOException {
    private static final long serialVersionUID = 1L;

    public InvalidMapException(String message) {
        super(message);
    }

    public InvalidMapException(String message, Throwable cause) {
        super(message, cause);
    }
}
