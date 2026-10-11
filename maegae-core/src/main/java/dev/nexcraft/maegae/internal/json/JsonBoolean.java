package dev.nexcraft.maegae.internal.json;

/**
 * The two immutable JSON boolean values.
 */
public enum JsonBoolean implements JsonValue {
    TRUE(true),
    FALSE(false);

    private final boolean value;

    JsonBoolean(boolean value) {
        this.value = value;
    }

    public boolean value() {
        return value;
    }

    public static JsonBoolean of(boolean value) {
        return value ? TRUE : FALSE;
    }
}
