package dev.nexcraft.maegae.protocol;

import dev.nexcraft.maegae.internal.json.JacksonJsonCodec;
import dev.nexcraft.maegae.internal.json.JsonArray;
import dev.nexcraft.maegae.internal.json.JsonNull;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonParseException;
import dev.nexcraft.maegae.internal.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class JsonRpcMessageParserTest {
    private final JsonRpcMessageParser parser = new JsonRpcMessageParser(new JacksonJsonCodec());

    @ParameterizedTest
    @MethodSource("requestsAndNotifications")
    void parsesRequestsAndNotificationsWithAbsentOrStructuredParams(String json, JsonRpcMessage expected) {
        assertEquals(expected, parser.parse(json));
    }

    static Stream<Arguments> requestsAndNotifications() {
        return Stream.of(
                arguments("""
                        {"jsonrpc":"2.0","id":"request-1","method":"  vendor/custom  "}
                        """, new JsonRpcRequest(JsonRpcId.of("request-1"), "  vendor/custom  ")),
                arguments("""
                        {"jsonrpc":"2.0","id":1,"method":"example","params":{"items":[1,true,null,{"value":"nested"}]}}
                        """, new JsonRpcRequest(JsonRpcId.of(1), "example", Optional.of(
                        JsonObject.of("items", JsonArray.of(JsonValue.of(1), JsonValue.of(true), JsonNull.INSTANCE,
                                JsonObject.of("value", JsonValue.of("nested"))))))),
                arguments("""
                        {"jsonrpc":"2.0","id":1,"method":"example","params":[]}
                        """, new JsonRpcRequest(JsonRpcId.of(1), "example", Optional.of(JsonArray.of()))),
                arguments("""
                        {"jsonrpc":"2.0","method":"  vendor/notify  "}
                        """, new JsonRpcNotification("  vendor/notify  ")),
                arguments("""
                        {"jsonrpc":"2.0","method":"example","params":{}}
                        """, new JsonRpcNotification("example", Optional.of(JsonObject.of()))),
                arguments("""
                        {"jsonrpc":"2.0","method":"example","params":[{"value":[false,null]}]}
                        """, new JsonRpcNotification("example", Optional.of(JsonArray.of(
                        JsonObject.of("value", JsonArray.of(JsonValue.of(false), JsonNull.INSTANCE)))))));
    }

    @ParameterizedTest
    @MethodSource("ids")
    void preservesStringAndNumericIdsAcrossRequestsAndResponses(String jsonId, JsonRpcId expected) {
        assertEquals(new JsonRpcRequest(expected, "example"), parser.parse(
                "{\"jsonrpc\":\"2.0\",\"id\":" + jsonId + ",\"method\":\"example\"}"));
        assertEquals(JsonRpcResponse.success(expected, JsonNull.INSTANCE), parser.parse(
                "{\"jsonrpc\":\"2.0\",\"id\":" + jsonId + ",\"result\":null}"));
        assertEquals(JsonRpcResponse.failure(expected, new JsonRpcError(-1, "custom")), parser.parse(
                "{\"jsonrpc\":\"2.0\",\"id\":" + jsonId + ",\"error\":{\"code\":-1,\"message\":\"custom\"}}"));
    }

    static Stream<Arguments> ids() {
        return Stream.concat(
                Stream.of(arguments("\"\"", JsonRpcId.of("")),
                        arguments("\"  request-1  \"", JsonRpcId.of("  request-1  ")),
                        arguments("\"42\"", JsonRpcId.of("42"))),
                Stream.of("0", "-42", "123.5", "1.234567890123456789",
                                "123456789012345678901234567890.1234567890", "1.2300e+12", "-4.5E-7")
                        .map(number -> arguments(number, JsonRpcId.of(new BigDecimal(number)))));
    }

    @ParameterizedTest
    @MethodSource("jsonValues")
    void preservesEveryJsonKindInResultsAndErrorData(String jsonValue, JsonValue expected) {
        assertEquals(JsonRpcResponse.success(JsonRpcId.of("id"), expected), parser.parse(
                "{\"jsonrpc\":\"2.0\",\"id\":\"id\",\"result\":" + jsonValue + "}"));
        assertEquals(JsonRpcResponse.failure(JsonRpcId.of("id"),
                new JsonRpcError(-32601, "  custom message  ", Optional.of(expected))), parser.parse(
                "{\"jsonrpc\":\"2.0\",\"id\":\"id\",\"error\":{\"code\":-32601,"
                        + "\"message\":\"  custom message  \",\"data\":" + jsonValue + "}}"));
    }

    static Stream<Arguments> jsonValues() {
        return Stream.of(
                arguments("null", JsonNull.INSTANCE),
                arguments("true", JsonValue.of(true)),
                arguments("\"detail\"", JsonValue.of("detail")),
                arguments("42.500", JsonValue.of(new BigDecimal("42.500"))),
                arguments("{}", JsonObject.of()),
                arguments("[]", JsonArray.of()),
                arguments("{\"items\":[null,{\"value\":1.25}]}", JsonObject.of("items", JsonArray.of(
                        JsonNull.INSTANCE, JsonObject.of("value", JsonValue.of(new BigDecimal("1.25")))))));
    }

    @ParameterizedTest
    @CsvSource({
            "-2147483648, -2147483648",
            "2147483647, 2147483647",
            "-32601, -32601",
            "0, 0",
            "1.0, 1",
            "1e3, 1000"
    })
    void acceptsStandardAndCustomIntegralErrorCodesInIntRange(String code, int expected) {
        assertEquals(JsonRpcResponse.failure(JsonRpcId.of(1), new JsonRpcError(expected, "")), parser.parse(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":" + code + ",\"message\":\"\"}}"));
    }

    @Test
    void representsAnExplicitNullErrorIdWithoutGeneratingAnIdOrErrorData() {
        assertEquals(JsonRpcResponse.protocolFailure(new JsonRpcError(-32700, "Parse error")), parser.parse("""
                {"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"Parse error"}}
                """));
    }

    @Test
    void ignoresAdditionalPropertiesWithoutInterpretingNestedNamesAsMessageFields() {
        assertEquals(new JsonRpcRequest(JsonRpcId.of(1), "example"), parser.parse("""
                {"jsonrpc":"2.0","id":1,"method":"example","custom":{"result":null,"method":false}}
                """));
        assertEquals(new JsonRpcNotification("example"), parser.parse("""
                {"jsonrpc":"2.0","method":"example","custom":true}
                """));
        assertEquals(JsonRpcResponse.success(JsonRpcId.of(1), JsonNull.INSTANCE), parser.parse("""
                {"jsonrpc":"2.0","id":1,"result":null,"custom":"ignored"}
                """));
        assertEquals(JsonRpcResponse.failure(JsonRpcId.of(1), new JsonRpcError(-1, "custom")), parser.parse("""
                {"jsonrpc":"2.0","id":1,"error":{"code":-1,"message":"custom","extra":[]},"custom":null}
                """));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "true", "1", "\"message\"", "[]", "[{\"jsonrpc\":\"2.0\",\"method\":\"m\"}]"})
    void rejectsNonObjectDocumentsIncludingBatches(String json) {
        assertInvalid(json, "object");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "true", "2.0", "{}", "[]", "\"1.0\"", "\"2.0 \"", "\"\""})
    void requiresTheExactStringVersion(String version) {
        assertInvalid("{\"jsonrpc\":" + version + ",\"method\":\"example\"}", "jsonrpc");
    }

    @ParameterizedTest
    @MethodSource("invalidStructures")
    void rejectsConflictingFieldsAndMissingRequiredFields(String json, String expectedField) {
        assertInvalid(json, expectedField);
    }

    static Stream<Arguments> invalidStructures() {
        return Stream.of(
                arguments("{\"id\":1,\"method\":\"example\"}", "jsonrpc"),
                arguments("{\"jsonrpc\":\"2.0\"}", "method"),
                arguments("{\"jsonrpc\":\"2.0\",\"id\":1}", "result"),
                arguments("{\"jsonrpc\":\"2.0\",\"params\":{}}", "method"),
                arguments("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"m\",\"result\":null}", "result"),
                arguments("{\"jsonrpc\":\"2.0\",\"method\":\"m\",\"error\":null}", "error"),
                arguments("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null,\"error\":null}", "result"),
                arguments("{\"jsonrpc\":\"2.0\",\"result\":null}", "id"),
                arguments("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-1,\"message\":\"\"}}", "id"),
                arguments("{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"m\"}", "id"),
                arguments("{\"jsonrpc\":\"2.0\",\"id\":null,\"result\":null}", "id"),
                arguments("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"message\":\"custom\"}}", "error.code"),
                arguments("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-1}}", "error.message"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "null", "true", "1", "{}", "[]"})
    void rejectsEmptyOrNonStringMethodsForRequestsAndNotifications(String method) {
        for (String id : List.of("", ",\"id\":1")) {
            assertInvalid("{\"jsonrpc\":\"2.0\"" + id + ",\"method\":" + method + "}", "method");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "true", "1", "\"invalid\""})
    void rejectsPrimitiveAndNullParamsForRequestsAndNotifications(String params) {
        for (String id : List.of("", ",\"id\":1")) {
            assertInvalid("{\"jsonrpc\":\"2.0\"" + id + ",\"method\":\"m\",\"params\":" + params + "}", "params");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "{}", "[]"})
    void rejectsBooleanAndContainerIdsForRequestsAndBothResponseVariants(String id) {
        for (String payload : List.of("\"method\":\"m\"", "\"result\":null",
                "\"error\":{\"code\":-1,\"message\":\"custom\"}")) {
            assertInvalid("{\"jsonrpc\":\"2.0\",\"id\":" + id + "," + payload + "}", "id");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "true", "1", "\"invalid\"", "[]"})
    void requiresAnErrorObject(String error) {
        assertInvalid("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":" + error + "}", "error");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "true", "\"1\"", "{}", "[]", "1.5", "2147483648", "-2147483649", "1e999"})
    void rejectsNonNumericFractionalAndOutOfRangeErrorCodes(String code) {
        assertInvalid("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":" + code
                + ",\"message\":\"custom\"}}", "error.code");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "true", "1", "{}", "[]"})
    void requiresAStringErrorMessage(String message) {
        assertInvalid("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-1,\"message\":" + message + "}}",
                "error.message");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"jsonrpc\":\"1.0\",\"jsonrpc\":\"2.0\",\"method\":\"m\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":null,\"id\":1,\"method\":\"m\"}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"\",\"method\":\"m\"}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"m\",\"params\":null,\"params\":{}}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null,\"result\":{}}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":1.5,\"code\":1,\"message\":\"m\"}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"m\",\"custom\":{\"value\":1,\"value\":2}}"
    })
    void rejectsDuplicatesBeforeTheyCanOverwriteStandardFieldsOrAdditionalProperties(String json) {
        assertThrows(JsonParseException.class, () -> parser.parse(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \n\t", "{\"jsonrpc\":\"2.0\",\"method\":}", "{} {}", "{} true"})
    void propagatesMalformedEmptyAndMultipleDocumentFailuresAsJsonParseExceptions(String json) {
        assertThrows(JsonParseException.class, () -> parser.parse(json));
    }

    @Test
    void honorsInjectedInputAndNestingLimitsAtTheirBoundaries() {
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"☃\",\"params\":{\"items\":[]}}";
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        var expected = new JsonRpcNotification("☃", Optional.of(JsonObject.of("items", JsonArray.of())));

        assertEquals(expected, new JsonRpcMessageParser(new JacksonJsonCodec(bytes, 3)).parse(json));
        assertThrows(JsonParseException.class,
                () -> new JsonRpcMessageParser(new JacksonJsonCodec(bytes - 1, 3)).parse(json));
        assertThrows(JsonParseException.class,
                () -> new JsonRpcMessageParser(new JacksonJsonCodec(bytes, 2)).parse(json));
    }

    @Test
    void reportsTheInvalidFieldWithoutEchoingTheDocumentAndPreservesTheNumericCause() {
        String json = """
                {"jsonrpc":"2.0","id":1,"error":{"code":1.5,"message":"sensitive-value"}}
                """;

        JsonRpcValidationException exception = assertThrows(JsonRpcValidationException.class, () -> parser.parse(json));

        assertTrue(exception.getMessage().contains("error.code"));
        assertFalse(exception.getMessage().contains("sensitive-value"));
        assertFalse(exception.getMessage().contains(json));
        assertInstanceOf(ArithmeticException.class, exception.getCause());
    }

    @Test
    void rejectsNullCodecAndInput() {
        assertThrows(NullPointerException.class, () -> new JsonRpcMessageParser(null));
        assertThrows(NullPointerException.class, () -> parser.parse(null));
    }

    private void assertInvalid(String json, String expectedField) {
        JsonRpcValidationException exception = assertThrows(JsonRpcValidationException.class, () -> parser.parse(json));
        assertTrue(exception.getMessage().contains(expectedField), exception.getMessage());
    }
}
