package dev.nexcraft.maegae.protocol;

/** Indicates that a parsed JSON document has an invalid JSON-RPC message structure. */
final class JsonRpcValidationException extends IllegalArgumentException {
    JsonRpcValidationException(String message) {
        super(message);
    }

    JsonRpcValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
