package ru.perveevm.polygon2ejudge.exceptions;

/**
 * @author Mike Perveev (perveev_m@mail.ru)
 */
public class ContestManagerException extends Exception {
    public ContestManagerException(final String message) {
        super("Fatal error: " + message);
    }

    public ContestManagerException(final String message, final Throwable cause) {
        super("Fatal error: " + message, cause);
    }
}
