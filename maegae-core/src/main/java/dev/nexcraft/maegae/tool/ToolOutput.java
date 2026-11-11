package dev.nexcraft.maegae.tool;

import java.util.Objects;
import java.util.Optional;

/** The result of one Tool invocation: text, or no output. */
public final class ToolOutput {
    private static final ToolOutput EMPTY = new ToolOutput(null);

    private final String text;

    private ToolOutput(final String text) {
        this.text = text;
    }

    /**
     * Creates an output that carries text. An empty string is valid text.
     *
     * @param text the text
     * @return the output
     */
    public static ToolOutput text(final String text) {
        return new ToolOutput(Objects.requireNonNull(text, "text"));
    }

    /** Returns the output that carries no text. */
    public static ToolOutput empty() {
        return EMPTY;
    }

    /** Returns the text, or an empty optional for {@link #empty()}. */
    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof ToolOutput output && Objects.equals(text, output.text);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(text);
    }

    @Override
    public String toString() {
        return text == null ? "ToolOutput[empty]" : "ToolOutput[text=" + text + "]";
    }
}
