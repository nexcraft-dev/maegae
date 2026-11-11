package dev.nexcraft.maegae.internal.tool;

import dev.nexcraft.maegae.internal.json.JsonBoolean;
import dev.nexcraft.maegae.internal.json.JsonNull;
import dev.nexcraft.maegae.internal.json.JsonNumber;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonString;
import dev.nexcraft.maegae.internal.json.JsonValue;
import dev.nexcraft.maegae.tool.ToolArgumentException;
import dev.nexcraft.maegae.tool.ToolArguments;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * {@link ToolArguments} backed by an internal JSON object.
 *
 * <p>Numeric rules:</p>
 * <ul>
 *   <li>Only JSON numbers convert to numeric types. Strings and booleans are rejected.</li>
 *   <li>Integer types accept integral values, such as {@code 1.0} and {@code 1e2}, and reject
 *       fractional and out-of-range values.</li>
 *   <li>Before an exact conversion, the scale and precision are checked. An integer value
 *       has at most {@link #MAX_INTEGER_DIGITS} digits, so a value such as {@code 1e1000000000}
 *       is rejected in constant time and memory.</li>
 *   <li>{@code float} and {@code double} may lose precision. A positive or negative infinity result is
 *       rejected. A finite value that is too small to represent becomes zero (with the sign of the value).</li>
 *   <li>{@link BigDecimal} values pass without rounding.</li>
 * </ul>
 */
public final class JsonToolArguments implements ToolArguments {
    /**
     * The maximum number of decimal digits that an integer conversion accepts, both for the integer part
     * and for the significant digits. It equals the numeric token limit of the JSON codec,
     * so every integer token that the codec accepts converts.
     */
    public static final int MAX_INTEGER_DIGITS = 1000;

    /** A decimal exponent above this value always overflows {@code double}; one below it always underflows. */
    private static final int FLOATING_EXPONENT_LIMIT = 400;

    private final JsonObject arguments;

    private JsonToolArguments(final JsonObject arguments) {
        this.arguments = arguments;
    }

    /**
     * Creates arguments from a JSON object.
     *
     * @param arguments the call arguments
     * @return the Tool arguments
     * @throws NullPointerException if arguments is null
     */
    public static ToolArguments of(final JsonObject arguments) {
        return new JsonToolArguments(Objects.requireNonNull(arguments, "arguments"));
    }

    @Override
    public boolean has(final String name) {
        return arguments.values().containsKey(name);
    }

    @Override
    public boolean isNull(final String name) {
        return arguments.values().get(name) instanceof JsonNull;
    }

    @Override
    public String getString(final String name) {
        if (value(name, "string") instanceof JsonString string) {
            return string.value();
        }
        throw invalid(name, "string");
    }

    @Override
    public boolean getBoolean(final String name) {
        if (value(name, "boolean") instanceof JsonBoolean bool) {
            return bool.value();
        }
        throw invalid(name, "boolean");
    }

    @Override
    public byte getByte(final String name) {
        return (byte) integral(name, "byte", Byte.SIZE - 1).intValue();
    }

    @Override
    public short getShort(final String name) {
        return (short) integral(name, "short", Short.SIZE - 1).intValue();
    }

    @Override
    public int getInt(final String name) {
        return integral(name, "int", Integer.SIZE - 1).intValue();
    }

    @Override
    public long getLong(final String name) {
        return integral(name, "long", Long.SIZE - 1).longValue();
    }

    @Override
    public BigInteger getBigInteger(final String name) {
        return integral(name, "BigInteger", Integer.MAX_VALUE);
    }

    @Override
    public float getFloat(final String name) {
        final BigDecimal number = number(name, "float");
        final float result = switch (magnitude(number)) {
            case OVERFLOW -> Float.POSITIVE_INFINITY;
            case UNDERFLOW -> number.signum() < 0 ? -0.0f : 0.0f;
            case NORMAL -> number.floatValue();
        };
        if (Float.isInfinite(result)) {
            throw invalid(name, "float");
        }
        return result;
    }

    @Override
    public double getDouble(final String name) {
        final BigDecimal number = number(name, "double");
        final double result = switch (magnitude(number)) {
            case OVERFLOW -> Double.POSITIVE_INFINITY;
            case UNDERFLOW -> number.signum() < 0 ? -0.0 : 0.0;
            case NORMAL -> number.doubleValue();
        };
        if (Double.isInfinite(result)) {
            throw invalid(name, "double");
        }
        return result;
    }

    @Override
    public BigDecimal getBigDecimal(final String name) {
        return number(name, "BigDecimal");
    }

    private JsonValue value(final String name, final String expectedType) {
        final JsonValue value = arguments.values().get(name);
        if (value == null || value instanceof JsonNull) {
            throw invalid(name, expectedType);
        }
        return value;
    }

    private BigDecimal number(final String name, final String expectedType) {
        if (value(name, expectedType) instanceof JsonNumber number) {
            return number.value();
        }
        throw invalid(name, expectedType);
    }

    /** Converts an integral number whose magnitude needs at most {@code maxBits} bits (sign excluded). */
    private BigInteger integral(final String name, final String expectedType, final int maxBits) {
        final BigDecimal number = number(name, expectedType);
        if (number.signum() == 0) {
            return BigInteger.ZERO;
        }
        final long precision = number.precision();
        final long integerDigits = precision - number.scale();
        // A non-zero value with no integer digit is fractional. The checks run before any exact conversion.
        if (integerDigits <= 0 || integerDigits > MAX_INTEGER_DIGITS || precision > MAX_INTEGER_DIGITS) {
            throw invalid(name, expectedType);
        }
        final BigInteger integer;
        try {
            integer = number.toBigIntegerExact();
        } catch (ArithmeticException exception) {
            throw invalid(name, expectedType);
        }
        if (integer.bitLength() > maxBits) {
            throw invalid(name, expectedType);
        }
        return integer;
    }

    private static Magnitude magnitude(final BigDecimal number) {
        if (number.signum() == 0) {
            return Magnitude.NORMAL;
        }
        final long exponent = (long) number.precision() - number.scale() - 1;
        if (exponent > FLOATING_EXPONENT_LIMIT) {
            return Magnitude.OVERFLOW;
        }
        return exponent < -FLOATING_EXPONENT_LIMIT ? Magnitude.UNDERFLOW : Magnitude.NORMAL;
    }

    private static ToolArgumentException invalid(final String name, final String expectedType) {
        return new ToolArgumentException(name, expectedType);
    }

    private enum Magnitude { OVERFLOW, UNDERFLOW, NORMAL }
}
