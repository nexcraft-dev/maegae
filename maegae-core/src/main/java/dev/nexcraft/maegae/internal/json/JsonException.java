package dev.nexcraft.maegae.internal.json;

/**
 * Base class for failures while parsing or serializing an internal JSON value.
 */
public abstract class JsonException extends IllegalArgumentException {

    protected JsonException(String message) {
        super(message);
    }

    protected JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
