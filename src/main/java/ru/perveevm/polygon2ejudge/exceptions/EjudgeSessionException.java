package ru.perveevm.polygon2ejudge.exceptions;

public class EjudgeSessionException extends Exception {
    public EjudgeSessionException(final String message) {
        super("Error happened while interacting with ejudge: " + message);
    }

    public EjudgeSessionException(final String message, final Throwable cause) {
        super("Error happened while interacting with ejudge: " + message, cause);
    }
}
