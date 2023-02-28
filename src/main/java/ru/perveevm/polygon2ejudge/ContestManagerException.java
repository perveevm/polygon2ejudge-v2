package ru.perveevm.polygon2ejudge;

public class ContestManagerException extends Exception {
    public ContestManagerException(final String message) {
        super("Fatal error: " + message);
    }

    public ContestManagerException(final String message, final Throwable cause) {
        super("Fatal error: " + message, cause);
    }
}
