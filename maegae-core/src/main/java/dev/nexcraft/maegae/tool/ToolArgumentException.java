package dev.nexcraft.maegae.tool;

import java.util.Objects;

/**
 * Reports an argument that is missing, null, of the wrong type, or out of range.
 * The message contains the argument name and the expected type. It never contains the supplied value.
 */
public final class ToolArgumentException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String argumentName;

    /**
     * Creates an exception for one argument.
     *
     * @param argumentName the argument name
     * @param expectedType a short description of the expected type, for example {@code int}
     */
    public ToolArgumentException(final String argumentName, final String expectedType) {
        super("Invalid argument '" + Objects.requireNonNull(argumentName, "argumentName")
                + "': expected non-null " + Objects.requireNonNull(expectedType, "expectedType") + ".");
        this.argumentName = argumentName;
    }

    /** Returns the name of the rejected argument. */
    public String argumentName() {
        return argumentName;
    }
}
