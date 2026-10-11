package dev.nexcraft.maegae.internal.json;

/**
 * The explicit JSON null value.
 */
public enum JsonNull implements JsonValue {
    INSTANCE;

    public static JsonNull instance() {
        return INSTANCE;
    }
}
