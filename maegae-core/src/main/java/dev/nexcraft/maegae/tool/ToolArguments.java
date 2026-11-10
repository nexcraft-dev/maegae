package dev.nexcraft.maegae.tool;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Read-only access to the arguments of one Tool call.
 *
 * <p>Every getter throws {@link ToolArgumentException} when the argument is missing or JSON null,
 * has the wrong JSON type, or is out of range for the requested Java type. No getter coerces values:
 * numeric getters accept JSON numbers only, and the string getter accepts JSON strings only.</p>
 */
public interface ToolArguments {
    /**
     * Tests whether an argument key exists. A key with a JSON null value exists.
     *
     * @param name the argument name
     * @return true if the key exists
     */
    boolean has(String name);

    /**
     * Tests whether an argument key exists and has a JSON null value.
     *
     * @param name the argument name
     * @return true if the key exists and its value is JSON null
     */
    boolean isNull(String name);

    /** Returns a JSON string argument. */
    String getString(String name);

    /** Returns a JSON boolean argument. */
    boolean getBoolean(String name);

    /** Returns an integral JSON number that fits a {@code byte}. */
    byte getByte(String name);

    /** Returns an integral JSON number that fits a {@code short}. */
    short getShort(String name);

    /** Returns an integral JSON number that fits an {@code int}. */
    int getInt(String name);

    /** Returns an integral JSON number that fits a {@code long}. */
    long getLong(String name);

    /**
     * Returns a JSON number as a {@code float}. Precision loss is allowed.
     * A finite value that is too small to represent becomes zero. An infinite result is rejected.
     */
    float getFloat(String name);

    /**
     * Returns a JSON number as a {@code double}. Precision loss is allowed.
     * A finite value that is too small to represent becomes zero. An infinite result is rejected.
     */
    double getDouble(String name);

    /** Returns an integral JSON number as a {@link BigInteger}. The digit count is limited. */
    BigInteger getBigInteger(String name);

    /** Returns a JSON number exactly, without rounding. */
    BigDecimal getBigDecimal(String name);
}
