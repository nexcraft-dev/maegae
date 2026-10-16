package dev.nexcraft.maegae.internal.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonJsonCodecTest {

    private final JacksonJsonCodec codec = new JacksonJsonCodec();

    @Test
    void parsesNestedValuesAndExplicitJsonNull() {
        JsonValue value = codec.parse("""
                {"name":"Maegae","active":true,"items":[1,false,null,{"empty":[]}]}
                """);

        assertEquals(JsonObject.of(
                "name", JsonValue.of("Maegae"),
                "active", JsonValue.of(true),
                "items", JsonArray.of(
                        JsonValue.of(1),
                        JsonValue.of(false),
                        JsonNull.instance(),
                        JsonObject.of("empty", JsonArray.of()))), value);
        assertSame(JsonNull.instance(), codec.parse("null"));
    }

    @Test
    void preservesObjectOrderWhenNamesAreUnique() {
        JsonObject object = (JsonObject) codec.parse("{\"first\":1,\"second\":2,\"third\":3}");

        assertEquals(List.of("first", "second", "third"), new ArrayList<>(object.values().keySet()));
        assertEquals(JsonValue.of(3), object.values().get("third"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":1,\"name\":2}",
            "{\"outer\":{\"name\":1,\"name\":2}}",
            "[{\"name\":1,\"name\":2}]",
            "{\"name\":1,\"n\\u0061me\":2}"
    })
    void rejectsDuplicatePropertyNamesWithinEachObject(String json) {
        JsonParseException exception = assertThrows(JsonParseException.class, () -> codec.parse(json));

        assertNotNull(exception.getCause());
        assertFalse(exception.getMessage().contains(json));
    }

    @Test
    void allowsTheSamePropertyNameInSeparateObjects() {
        assertEquals(JsonArray.of(JsonObject.of("name", JsonValue.of(1)),
                        JsonObject.of("name", JsonValue.of(2))),
                codec.parse("[{\"name\":1},{\"name\":2}]"));
    }

    @Test
    void parsesNumbersAsExactBigDecimalsIncludingExponentForms() {
        JsonArray numbers = (JsonArray) codec.parse(
                "[123456789012345678901234567890.12345678901234567890,1.2300e+12,-4.5E-7]");

        assertEquals(new BigDecimal("123456789012345678901234567890.12345678901234567890"),
                ((JsonNumber) numbers.values().get(0)).value());
        assertEquals(new BigDecimal("1.2300e+12"), ((JsonNumber) numbers.values().get(1)).value());
        assertEquals(new BigDecimal("-4.5E-7"), ((JsonNumber) numbers.values().get(2)).value());
    }

    @Test
    void acceptsJsonWhitespaceAndRequiresExactlyOneDocument() {
        assertEquals(JsonArray.of(JsonValue.of(1), JsonValue.of(2)), codec.parse(" \n [1, 2]\t\r"));

        assertThrows(JsonParseException.class, () -> codec.parse("1 2"));
        assertThrows(JsonParseException.class, () -> codec.parse("{} true"));
        assertThrows(JsonParseException.class, () -> codec.parse("true trailing"));
    }

    @Test
    void rejectsEmptyMalformedAndNonStandardJson() {
        assertThrows(JsonParseException.class, () -> codec.parse(""));
        assertThrows(JsonParseException.class, () -> codec.parse("  \n\t"));
        assertThrows(JsonParseException.class, () -> codec.parse("{\"value\":}"));
        assertThrows(JsonParseException.class, () -> codec.parse("[1,"));
        assertThrows(JsonParseException.class, () -> codec.parse("// comment\n1"));
        assertThrows(JsonParseException.class, () -> codec.parse("{'single':'quotes'}"));
        assertThrows(JsonParseException.class, () -> codec.parse("{unquoted:1}"));
        assertThrows(JsonParseException.class, () -> codec.parse("[1,]"));
    }

    @Test
    void wrapsJacksonFailuresWithoutIncludingInputInOuterMessage() {
        String input = "{\"secret\":\"sensitive-value\",}";

        JsonParseException exception = assertThrows(JsonParseException.class, () -> codec.parse(input));

        assertTrue(exception.getMessage().contains("Malformed JSON input"));
        assertFalse(exception.getMessage().contains("sensitive-value"));
        assertNotNull(exception.getCause());
    }

    @Test
    void rejectsNullInput() {
        assertThrows(NullPointerException.class, () -> codec.parse(null));
    }

    @Test
    void enforcesUtf8ByteLimitForAsciiAndMultibyteInput() {
        JacksonJsonCodec asciiLimited = new JacksonJsonCodec(4, 64);
        assertEquals(JsonValue.of(1), asciiLimited.parse("1"));
        assertEquals(JsonNull.instance(), asciiLimited.parse("null"));
        assertThrows(JsonParseException.class, () -> asciiLimited.parse("true "));

        JacksonJsonCodec multibyteLimited = new JacksonJsonCodec(5, 64);
        assertEquals(JsonValue.of("a"), multibyteLimited.parse("\"a\""));
        assertEquals(JsonValue.of("☃"), multibyteLimited.parse("\"☃\""));
        assertThrows(JsonParseException.class, () -> multibyteLimited.parse("\"☃ \""));
    }

    @Test
    void enforcesDefaultByteLimitAcrossEncoderBuffers() {
        String atLimit = "\"" + "a".repeat(1_048_574) + "\"";
        String aboveLimit = "\"" + "a".repeat(1_048_575) + "\"";

        JsonString parsed = (JsonString) codec.parse(atLimit);
        assertEquals(1_048_574, parsed.value().length());
        assertThrows(JsonParseException.class, () -> codec.parse(aboveLimit));
    }

    @Test
    void rejectsMalformedUtf16BeforeParsing() {
        String unpairedHighSurrogate = String.valueOf((char) 0xD800);
        String unpairedLowSurrogate = String.valueOf((char) 0xDC00);

        JsonParseException highSurrogate = assertThrows(JsonParseException.class,
                () -> codec.parse(unpairedHighSurrogate));
        JsonParseException lowSurrogate = assertThrows(JsonParseException.class,
                () -> codec.parse(unpairedLowSurrogate));

        assertTrue(highSurrogate.getMessage().contains("malformed UTF-16"));
        assertInstanceOf(java.nio.charset.CharacterCodingException.class, highSurrogate.getCause());
        assertInstanceOf(java.nio.charset.CharacterCodingException.class, lowSurrogate.getCause());
    }

    @Test
    void validatesConfiguredInputAndNestingLimits() {
        assertThrows(IllegalArgumentException.class, () -> new JacksonJsonCodec(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new JacksonJsonCodec(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new JacksonJsonCodec(1, -1));
    }

    @Test
    void enforcesZeroNestingDepthForParsingAndSerialization() {
        JacksonJsonCodec depthZero = new JacksonJsonCodec(100, 0);

        assertEquals(JsonValue.of(true), depthZero.parse("true"));
        assertEquals("1", depthZero.serialize(JsonValue.of(1)));

        assertThrows(JsonParseException.class, () -> depthZero.parse("{}"));
        assertThrows(JsonParseException.class, () -> depthZero.parse("[]"));
        assertThrows(JsonSerializationException.class, () -> depthZero.serialize(JsonObject.of()));
        assertThrows(JsonSerializationException.class, () -> depthZero.serialize(JsonArray.of()));
    }

    @Test
    void enforcesContainerDepthForObjectsArraysAndMixedNesting() {
        JacksonJsonCodec depthTwo = new JacksonJsonCodec(256, 2);

        assertEquals(JsonArray.of(), depthTwo.parse("[]"));
        assertEquals(JsonObject.of("items", JsonArray.of()), depthTwo.parse("{\"items\":[]}"));
        assertEquals(JsonArray.of(JsonArray.of(JsonValue.of(0))), depthTwo.parse("[[0]]"));
        assertThrows(JsonParseException.class, () -> depthTwo.parse("{\"outer\":{\"inner\":[]}}"));
        assertThrows(JsonParseException.class, () -> depthTwo.parse("[[[]]]"));
        assertThrows(JsonParseException.class, () -> depthTwo.parse("{\"items\":[{}]}"));

        assertEquals("[]", depthTwo.serialize(JsonArray.of()));
        assertEquals("{\"items\":[]}", depthTwo.serialize(JsonObject.of("items", JsonArray.of())));
        assertEquals("[[0]]", depthTwo.serialize(JsonArray.of(JsonArray.of(JsonValue.of(0)))));
        assertThrows(JsonSerializationException.class,
                () -> depthTwo.serialize(JsonObject.of("outer", JsonObject.of("inner", JsonArray.of()))));
        assertThrows(JsonSerializationException.class,
                () -> depthTwo.serialize(JsonArray.of(JsonArray.of(JsonArray.of()))));
        assertThrows(JsonSerializationException.class,
                () -> depthTwo.serialize(JsonObject.of("items", JsonArray.of(JsonObject.of()))));
    }

    @Test
    void enforcesDefaultNestingDepthBoundary() {
        JsonValue atLimit = nestedArray(64);
        String atLimitJson = nestedArrayJson(64);

        assertJsonEquivalent(atLimit, codec.parse(atLimitJson));
        assertEquals(atLimitJson, codec.serialize(atLimit));
        assertThrows(JsonParseException.class, () -> codec.parse(nestedArrayJson(65)));
        assertThrows(JsonSerializationException.class, () -> codec.serialize(nestedArray(65)));
    }

    @Test
    void enforcesParsedNumberLengthAndWrapsJacksonFailures() {
        String maximumLengthNumber = "9".repeat(1_000);
        String overLimitNumber = "9".repeat(1_001);
        List<String> otherMaximumLengthNumbers = List.of(
                "-" + "9".repeat(999),
                "0." + "1".repeat(998),
                "1".repeat(996) + "e+10");
        List<String> otherOverLimitNumbers = List.of(
                "-" + "9".repeat(1_000),
                "0." + "1".repeat(999),
                "1".repeat(997) + "e+10");

        JsonNumber parsed = (JsonNumber) codec.parse(maximumLengthNumber);
        assertEquals(new BigDecimal(maximumLengthNumber), parsed.value());
        assertEquals(overLimitNumber, codec.serialize(JsonNumber.of(new BigDecimal(overLimitNumber))));
        for (String number : otherMaximumLengthNumbers) {
            assertEquals(new BigDecimal(number), ((JsonNumber) codec.parse(number)).value());
        }

        JsonParseException tooLong = assertThrows(JsonParseException.class, () -> codec.parse(overLimitNumber));
        assertInstanceOf(JsonException.class, tooLong);
        assertNotNull(tooLong.getCause());
        for (String number : otherOverLimitNumbers) {
            assertThrows(JsonParseException.class, () -> codec.parse(number));
        }
        assertThrows(JsonParseException.class, () -> codec.parse("01"));
        assertThrows(JsonParseException.class, () -> codec.parse("1e"));
    }

    @Test
    void wrapsSerializationConstraintFailuresInJsonException() {
        JacksonJsonCodec shallowCodec = new JacksonJsonCodec(100, 1);

        JsonSerializationException exception = assertThrows(JsonSerializationException.class,
                () -> shallowCodec.serialize(JsonObject.of("nested", JsonArray.of())));

        assertInstanceOf(JsonException.class, exception);
        assertNotNull(exception.getCause());
        assertFalse(exception.getMessage().contains("nested"));
    }

    @Test
    void serializesPrimitiveValuesAndEmptyContainers() {
        assertEquals("\"Maegae\"", codec.serialize(JsonValue.of("Maegae")));
        assertEquals("123", codec.serialize(JsonValue.of(123)));
        assertEquals("true", codec.serialize(JsonValue.of(true)));
        assertEquals("false", codec.serialize(JsonValue.of(false)));
        assertEquals("null", codec.serialize(JsonNull.instance()));
        assertEquals("{}", codec.serialize(JsonObject.of()));
        assertEquals("[]", codec.serialize(JsonArray.of()));
    }

    @Test
    void serializesNestedValuesPreservingObjectAndArrayOrder() {
        JsonValue value = JsonObject.of(
                "jsonrpc", JsonValue.of("2.0"),
                "method", JsonValue.of("tools/list"),
                "params", JsonArray.of(
                        JsonValue.of(1),
                        JsonObject.of("enabled", JsonValue.of(true), "result", JsonNull.instance())));

        String expected = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"params\":[1,{\"enabled\":true,\"result\":null}]}";

        assertEquals(expected, codec.serialize(value));
    }

    @Test
    void escapesJsonStringsAndPreservesUnicode() {
        String original = "quote \" slash \\ newline\n tab\t control\u0001 snowman ☃ emoji 😀";

        JsonString value = new JsonString(original);
        JsonObject object = JsonObject.of(original, value);

        assertEquals(value, codec.parse(codec.serialize(value)));
        assertEquals(object, codec.parse(codec.serialize(object)));
    }

    @Test
    void serializesBigDecimalsWithoutLosingPrecisionOrScale() {
        List<BigDecimal> numbers = List.of(
                new BigDecimal("123456789012345678901234567890"),
                new BigDecimal("-123"),
                new BigDecimal("123.456789012345678901234567890"),
                new BigDecimal("1E+10"),
                new BigDecimal("0.00000000000000000001"),
                new BigDecimal("1.2300"));
        JsonArray original = new JsonArray(numbers.stream().<JsonValue>map(JsonNumber::of).toList());

        JsonArray restored = (JsonArray) codec.parse(codec.serialize(original));

        for (int index = 0; index < numbers.size(); index++) {
            BigDecimal expected = numbers.get(index);
            BigDecimal actual = ((JsonNumber) restored.values().get(index)).value();
            assertEquals(0, expected.compareTo(actual));
        }
        assertEquals(numbers.get(5).scale(), ((JsonNumber) restored.values().get(5)).value().scale());
    }

    @Test
    void roundTripsMixedNestedValuesParsedAndConstructed() {
        JsonValue parsed = codec.parse("""
                {
                  "escaped":"quote \\\" slash \\\\ newline\\n tab\\t snowman ☃ emoji 😀",
                  "emptyObject":{},
                  "emptyArray":[],
                  "flags":[true,false,null],
                  "numbers":[123456789012345678901234567890.12345678901234567890,-12345678901234567890,1e+10,-4.5E-7,1.2300,0.00000000000000000001],
                  "nested":[{"values":[null,{"text":"Maegae ☃ 😀"}]}]
                }
                """);
        JsonValue constructed = JsonObject.of(
                "escaped", JsonValue.of("quote \" slash \\ newline\n tab\t snowman ☃ emoji 😀"),
                "emptyObject", JsonObject.of(),
                "emptyArray", JsonArray.of(),
                "flags", JsonArray.of(JsonValue.of(true), JsonValue.of(false), JsonNull.instance()),
                "numbers", JsonArray.of(
                        JsonValue.of(new BigDecimal("123456789012345678901234567890.12345678901234567890")),
                        JsonValue.of(new BigDecimal("-12345678901234567890")),
                        JsonValue.of(new BigDecimal("1E+10")),
                        JsonValue.of(new BigDecimal("-4.5E-7")),
                        JsonValue.of(new BigDecimal("1.2300")),
                        JsonValue.of(new BigDecimal("0.00000000000000000001"))),
                "nested", JsonArray.of(JsonObject.of(
                        "values", JsonArray.of(JsonNull.instance(), JsonObject.of("text", JsonValue.of("Maegae ☃ 😀"))))));

        assertJsonEquivalent(parsed, codec.parse(codec.serialize(parsed)));
        assertJsonEquivalent(constructed, codec.parse(codec.serialize(constructed)));
    }

    @Test
    void safelySharesCodecAcrossVirtualThreadTasks() throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            int taskNumber = index;
            tasks.add(() -> {
                String json = "{\"id\":" + taskNumber
                        + ",\"payload\":{\"label\":\"task-" + taskNumber + " ☃ 😀\","
                        + "\"flags\":[true,false,null,[]]},"
                        + "\"amount\":12345678901234567890.1234500}";
                JsonValue expected = JsonObject.of(
                        "id", JsonValue.of(taskNumber),
                        "payload", JsonObject.of(
                                "label", JsonValue.of("task-" + taskNumber + " ☃ 😀"),
                                "flags", JsonArray.of(JsonValue.of(true), JsonValue.of(false), JsonNull.instance(),
                                        JsonArray.of())),
                        "amount", JsonValue.of(new BigDecimal("12345678901234567890.1234500")));

                JsonValue parsed = codec.parse(json);
                assertJsonEquivalent(expected, parsed);
                JsonValue restored = codec.parse(codec.serialize(parsed));
                assertJsonEquivalent(expected, restored);
                return taskNumber;
            });
        }

        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<Integer>> results = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
            for (int index = 0; index < results.size(); index++) {
                Future<Integer> result = results.get(index);
                assertFalse(result.isCancelled(), "virtual-thread task exceeded 10 seconds");
                assertEquals(index, result.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsNullSerializationInput() {
        assertThrows(NullPointerException.class, () -> codec.serialize(null));
    }

    private static JsonValue nestedArray(int depth) {
        JsonValue value = JsonValue.of(0);
        for (int level = 0; level < depth; level++) {
            value = JsonArray.of(value);
        }
        return value;
    }

    private static String nestedArrayJson(int depth) {
        return "[".repeat(depth) + "0" + "]".repeat(depth);
    }

    private static void assertJsonEquivalent(JsonValue expected, JsonValue actual) {
        switch (expected) {
            case JsonObject expectedObject -> {
                JsonObject actualObject = assertInstanceOf(JsonObject.class, actual);
                assertEquals(new ArrayList<>(expectedObject.values().keySet()),
                        new ArrayList<>(actualObject.values().keySet()));
                expectedObject.values().forEach((key, value) ->
                        assertJsonEquivalent(value, actualObject.values().get(key)));
            }
            case JsonArray expectedArray -> {
                JsonArray actualArray = assertInstanceOf(JsonArray.class, actual);
                assertEquals(expectedArray.values().size(), actualArray.values().size());
                for (int index = 0; index < expectedArray.values().size(); index++) {
                    assertJsonEquivalent(expectedArray.values().get(index), actualArray.values().get(index));
                }
            }
            case JsonString expectedString ->
                    assertEquals(expectedString.value(), assertInstanceOf(JsonString.class, actual).value());
            case JsonNumber expectedNumber ->
                    assertEquals(0, expectedNumber.value()
                            .compareTo(assertInstanceOf(JsonNumber.class, actual).value()));
            case JsonBoolean expectedBoolean ->
                    assertEquals(expectedBoolean.value(), assertInstanceOf(JsonBoolean.class, actual).value());
            case JsonNull ignored -> assertSame(JsonNull.instance(), assertInstanceOf(JsonNull.class, actual));
        }
    }
}
