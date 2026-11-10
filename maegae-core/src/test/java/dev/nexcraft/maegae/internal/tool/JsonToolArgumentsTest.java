package dev.nexcraft.maegae.internal.tool;

import dev.nexcraft.maegae.internal.json.JacksonJsonCodec;
import dev.nexcraft.maegae.internal.json.JsonNumber;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.tool.ToolArgumentException;
import dev.nexcraft.maegae.tool.ToolArguments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonToolArgumentsTest {
    private static ToolArguments args(final String json) {
        return JsonToolArguments.of((JsonObject) new JacksonJsonCodec().parse(json));
    }

    private static ToolArguments number(final String literal) {
        return args("{\"n\":" + literal + "}");
    }

    private static ToolArguments number(final BigDecimal value) {
        return JsonToolArguments.of(JsonObject.of("n", JsonNumber.of(value)));
    }

    private static void rejects(final Function<ToolArguments, ?> getter, final ToolArguments arguments) {
        final var exception = assertThrows(ToolArgumentException.class, () -> getter.apply(arguments));
        assertEquals("n", exception.argumentName());
    }

    @Test
    void readsStringAndBooleanWithoutCoercion() {
        final var arguments = args("{\"s\":\"text\",\"b\":true,\"n\":1}");
        assertEquals("text", arguments.getString("s"));
        assertTrue(arguments.getBoolean("b"));
        assertThrows(ToolArgumentException.class, () -> arguments.getString("n"));
        assertThrows(ToolArgumentException.class, () -> arguments.getBoolean("s"));
        assertThrows(ToolArgumentException.class, () -> arguments.getInt("s"));
        assertThrows(ToolArgumentException.class, () -> arguments.getDouble("b"));
        assertThrows(ToolArgumentException.class, () -> args("{\"n\":\"1\"}").getInt("n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1]", "{\"a\":1}"})
    void rejectsContainersForEveryType(final String literal) {
        final var arguments = number(literal);
        rejects(a -> a.getString("n"), arguments);
        rejects(a -> a.getBoolean("n"), arguments);
        rejects(a -> a.getInt("n"), arguments);
        rejects(a -> a.getDouble("n"), arguments);
        rejects(a -> a.getBigDecimal("n"), arguments);
    }

    @Test
    void distinguishesMissingFromExplicitNull() {
        final var arguments = args("{\"nil\":null,\"value\":1}");
        assertTrue(arguments.has("nil"));
        assertTrue(arguments.isNull("nil"));
        assertTrue(arguments.has("value"));
        assertFalse(arguments.isNull("value"));
        assertFalse(arguments.has("missing"));
        assertFalse(arguments.isNull("missing"));
        for (final String name : new String[]{"nil", "missing"}) {
            assertThrows(ToolArgumentException.class, () -> arguments.getString(name));
            assertThrows(ToolArgumentException.class, () -> arguments.getBoolean(name));
            assertThrows(ToolArgumentException.class, () -> arguments.getInt(name));
            assertThrows(ToolArgumentException.class, () -> arguments.getBigInteger(name));
            assertThrows(ToolArgumentException.class, () -> arguments.getBigDecimal(name));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1.0", "1e0", "0.1e1", "10e-1", "-0", "0.0", "0e-1000000000"})
    void acceptsIntegralValuesForAllIntegerTypes(final String literal) {
        final var arguments = number(literal);
        final long expected = new BigDecimal(literal).signum() == 0 ? 0 : 1;
        assertEquals(expected, arguments.getByte("n"));
        assertEquals(expected, arguments.getShort("n"));
        assertEquals(expected, arguments.getInt("n"));
        assertEquals(expected, arguments.getLong("n"));
        assertEquals(BigInteger.valueOf(expected), arguments.getBigInteger("n"));
    }

    @Test
    void acceptsIntegralScientificNotation() {
        assertEquals(100, number("1e2").getInt("n"));
        assertEquals(100, number("1.0E+2").getByte("n"));
        assertEquals(-120, number("-1.2e2").getByte("n"));
        assertEquals(new BigInteger("1" + "0".repeat(50)), number("1e50").getBigInteger("n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "0.5", "-0.5", "1e-1", "10.50", "123456789.000000001", "1e-1000000000"})
    void rejectsFractionalValuesForAllIntegerTypes(final String literal) {
        final var arguments = number(literal);
        rejects(a -> a.getByte("n"), arguments);
        rejects(a -> a.getShort("n"), arguments);
        rejects(a -> a.getInt("n"), arguments);
        rejects(a -> a.getLong("n"), arguments);
        rejects(a -> a.getBigInteger("n"), arguments);
    }

    @ParameterizedTest
    @CsvSource({
            "127,-128,128,-129",
            "32767,-32768,32768,-32769",
            "2147483647,-2147483648,2147483648,-2147483649",
            "9223372036854775807,-9223372036854775808,9223372036854775808,-9223372036854775809"})
    void enforcesRangeAtTheBoundaries(final String max, final String min, final String above, final String below) {
        final int index = switch (max) {
            case "127" -> 0;
            case "32767" -> 1;
            case "2147483647" -> 2;
            default -> 3;
        };
        final Function<ToolArguments, Object> getter = switch (index) {
            case 0 -> a -> a.getByte("n");
            case 1 -> a -> a.getShort("n");
            case 2 -> a -> a.getInt("n");
            default -> a -> a.getLong("n");
        };
        assertEquals(max, getter.apply(number(max)).toString());
        assertEquals(min, getter.apply(number(min)).toString());
        rejects(getter, number(above));
        rejects(getter, number(below));
        rejects(getter, number(above + ".0"));
        rejects(getter, number("1e30"));
    }

    @Test
    void limitsBigIntegerDigitCount() {
        final String atLimit = "9".repeat(JsonToolArguments.MAX_INTEGER_DIGITS);
        assertEquals(new BigInteger(atLimit), number(atLimit).getBigInteger("n"));
        assertEquals(new BigInteger("-" + atLimit), number(new BigDecimal("-" + atLimit)).getBigInteger("n"));
        assertEquals(BigInteger.TEN.pow(JsonToolArguments.MAX_INTEGER_DIGITS - 1),
                number("1e" + (JsonToolArguments.MAX_INTEGER_DIGITS - 1)).getBigInteger("n"));
        rejects(a -> a.getBigInteger("n"), number("1e" + JsonToolArguments.MAX_INTEGER_DIGITS));
        rejects(a -> a.getBigInteger("n"),
                number(new BigDecimal("1" + "0".repeat(JsonToolArguments.MAX_INTEGER_DIGITS))));
    }

    @Test
    void rejectsIntegralValuesWithMoreSignificantDigitsThanTheLimit() {
        // One integer digit, but 1,501 significant digits. The policy rejects such values for integer types.
        final var arguments = number(new BigDecimal("1." + "0".repeat(1500)));
        assertTrue(new BigDecimal("1." + "0".repeat(1500)).precision() > JsonToolArguments.MAX_INTEGER_DIGITS);
        rejects(a -> a.getByte("n"), arguments);
        rejects(a -> a.getShort("n"), arguments);
        rejects(a -> a.getInt("n"), arguments);
        rejects(a -> a.getLong("n"), arguments);
        rejects(a -> a.getBigInteger("n"), arguments);
        // The exact value remains available as BigDecimal.
        assertEquals(new BigDecimal("1." + "0".repeat(1500)), arguments.getBigDecimal("n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e1000000000", "-1e1000000000", "1e-1000000000", "1e2147483647", "1e-2147483647",
            "9e999999999", "1.5e-999999999", "1e100000000"})
    void boundsWorkForExtremeExponents(final String literal) {
        final var arguments = number(literal);
        // Without the digit-limit check, 1e100000000 needs about 24 seconds in BigDecimal.toBigIntegerExact.
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            rejects(a -> a.getByte("n"), arguments);
            rejects(a -> a.getShort("n"), arguments);
            rejects(a -> a.getInt("n"), arguments);
            rejects(a -> a.getLong("n"), arguments);
            rejects(a -> a.getBigInteger("n"), arguments);
            if (literal.contains("e-") || literal.contains("E-")) {
                assertEquals(0.0, Math.abs(arguments.getDouble("n")));
                assertEquals(0.0f, Math.abs(arguments.getFloat("n")));
            } else {
                rejects(a -> a.getDouble("n"), arguments);
                rejects(a -> a.getFloat("n"), arguments);
            }
            assertEquals(new BigDecimal(literal), arguments.getBigDecimal("n"));
        });
    }

    @Test
    void floatingPointAllowsPrecisionLossButRejectsInfinity() {
        assertEquals(0.1, number("0.1").getDouble("n"));
        assertEquals(0.1f, number("0.1").getFloat("n"));
        assertEquals(16777216.0f, number("16777217").getFloat("n"));
        assertEquals(9007199254740992.0, number("9007199254740993").getDouble("n"));
        assertEquals(Double.MAX_VALUE, number(Double.toString(Double.MAX_VALUE)).getDouble("n"));
        assertEquals(Float.MAX_VALUE, number(Float.toString(Float.MAX_VALUE)).getFloat("n"));
        assertEquals(1e39, number("1e39").getDouble("n"));
        rejects(a -> a.getFloat("n"), number("1e39"));
        rejects(a -> a.getFloat("n"), number("-1e39"));
        rejects(a -> a.getDouble("n"), number("1e309"));
        rejects(a -> a.getDouble("n"), number("-1e309"));
        // Above Double.MAX_VALUE but below the rounding midpoint to infinity, the value rounds to MAX_VALUE.
        assertEquals(Double.MAX_VALUE, number("1.7976931348623158e308").getDouble("n"));
    }

    @Test
    void floatingPointUnderflowBecomesZeroWithSign() {
        assertEquals(0.0, number("1e-400").getDouble("n"));
        assertEquals(0.0, number("1e-330").getDouble("n"));
        assertEquals(-0.0, number("-1e-400").getDouble("n"));
        assertEquals(0.0f, number("1e-50").getFloat("n"));
        assertEquals(0.0f, number("1e-500").getFloat("n"));
        assertEquals(Double.MIN_VALUE, number("4.9e-324").getDouble("n"));
    }

    @Test
    void passesBigDecimalWithoutRounding() {
        final String literal = "123456789012345678901234567890.123456789012345678901234567890";
        assertEquals(new BigDecimal(literal), number(literal).getBigDecimal("n"));
        assertEquals(new BigDecimal("1.2300"), number("1.2300").getBigDecimal("n"));
        assertEquals(new BigDecimal("1E+2"), number("1e2").getBigDecimal("n"));
    }

    private static void assertMessage(final ToolArgumentException exception, final String name,
                                      final String type, final String... forbidden) {
        assertEquals(name, exception.argumentName());
        assertTrue(exception.getMessage().contains("'" + name + "'"), exception.getMessage());
        assertTrue(exception.getMessage().contains(type), exception.getMessage());
        for (final String text : forbidden) {
            assertFalse(exception.getMessage().contains(text), exception.getMessage());
        }
    }

    @Test
    void errorMessagesNameTheArgumentAndTypeButNeverTheValue() {
        final var arguments = args("{\"text\":\"SECRET-VALUE\",\"big\":987654321098765432109876543210,"
                + "\"frac\":3.14159265}");
        assertMessage(assertThrows(ToolArgumentException.class, () -> arguments.getInt("text")),
                "text", "int", "SECRET");
        assertMessage(assertThrows(ToolArgumentException.class, () -> arguments.getInt("big")),
                "big", "int", "987654321");
        assertMessage(assertThrows(ToolArgumentException.class, () -> arguments.getLong("frac")),
                "frac", "long", "3.14");
        assertMessage(assertThrows(ToolArgumentException.class, () -> arguments.getBoolean("absent")),
                "absent", "boolean");
    }

    @Test
    void infinityRejectionNamesTheArgumentAndTypeButNotTheValue() {
        final var arguments = args("{\"d\":7.25e999,\"f\":-7.25e39}");
        assertMessage(assertThrows(ToolArgumentException.class, () -> arguments.getDouble("d")),
                "d", "double", "7.25", "e999", "E+999");
        assertMessage(assertThrows(ToolArgumentException.class, () -> arguments.getFloat("f")),
                "f", "float", "7.25", "e39", "E+39");
    }

    @Test
    void digitLimitRejectionNamesTheArgumentAndTypeButNotTheValue() {
        final var arguments = number(new BigDecimal("7".repeat(JsonToolArguments.MAX_INTEGER_DIGITS + 1)));
        assertMessage(assertThrows(ToolArgumentException.class, () -> arguments.getBigInteger("n")),
                "n", "BigInteger", "7777");
    }
}
