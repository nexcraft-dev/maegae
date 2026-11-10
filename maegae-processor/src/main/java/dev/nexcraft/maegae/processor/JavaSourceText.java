package dev.nexcraft.maegae.processor;

/** Renders text as Java source code. */
final class JavaSourceText {
    private JavaSourceText() {
    }

    /** Renders a string literal that uses only printable ASCII characters. */
    static String string(final String value) {
        final StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character == '"' || character == '\\') {
                result.append('\\').append(character);
            } else if (character < 0x20 || character == 0x7f) {
                result.append(String.format("\\%03o", (int) character));
            } else if (character > 0x7e) {
                result.append(String.format("\\u%04x", (int) character));
            } else {
                result.append(character);
            }
        }
        return result.append('"').toString();
    }
}
