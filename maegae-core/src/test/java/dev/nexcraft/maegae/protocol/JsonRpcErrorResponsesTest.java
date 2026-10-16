package dev.nexcraft.maegae.protocol;

import dev.nexcraft.maegae.internal.json.JacksonJsonCodec;
import dev.nexcraft.maegae.internal.json.JsonCodec;
import dev.nexcraft.maegae.internal.json.JsonParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class JsonRpcErrorResponsesTest {
    private final JsonCodec codec = new JacksonJsonCodec();
    private final JsonRpcMessageParser parser = new JsonRpcMessageParser(codec);
    private final JsonRpcMessageSerializer serializer = new JsonRpcMessageSerializer(codec);

    @ParameterizedTest
    @MethodSource("standardResponses")
    void producesOnlyStandardErrorFieldsAndRoundTrips(JsonRpcResponse response, String expectedJson) {
        String json = serializer.serialize(response);

        assertEquals(codec.parse(expectedJson), codec.parse(json));
        assertEquals(response, parser.parse(json));
    }

    static Stream<Arguments> standardResponses() {
        JsonRpcId id = JsonRpcId.of(123);
        return Stream.of(
                arguments(JsonRpcErrorResponses.parseError(), """
                        {"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"Parse error"}}
                        """),
                arguments(JsonRpcErrorResponses.invalidRequest(), """
                        {"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid Request"}}
                        """),
                arguments(JsonRpcErrorResponses.invalidRequest(id), """
                        {"jsonrpc":"2.0","id":123,"error":{"code":-32600,"message":"Invalid Request"}}
                        """),
                arguments(JsonRpcErrorResponses.methodNotFound(id), """
                        {"jsonrpc":"2.0","id":123,"error":{"code":-32601,"message":"Method not found"}}
                        """),
                arguments(JsonRpcErrorResponses.invalidParams(id), """
                        {"jsonrpc":"2.0","id":123,"error":{"code":-32602,"message":"Invalid params"}}
                        """),
                arguments(JsonRpcErrorResponses.internalError(), """
                        {"jsonrpc":"2.0","id":null,"error":{"code":-32603,"message":"Internal error"}}
                        """),
                arguments(JsonRpcErrorResponses.internalError(id), """
                        {"jsonrpc":"2.0","id":123,"error":{"code":-32603,"message":"Internal error"}}
                        """));
    }

    @ParameterizedTest
    @MethodSource("ids")
    void preservesKnownIdsAcrossAllIdTakingFactories(JsonRpcId id) {
        for (JsonRpcResponse response : List.of(JsonRpcErrorResponses.invalidRequest(id),
                JsonRpcErrorResponses.methodNotFound(id), JsonRpcErrorResponses.invalidParams(id),
                JsonRpcErrorResponses.internalError(id))) {
            assertEquals(Optional.of(id), response.id());
            assertEquals(response, parser.parse(serializer.serialize(response)));
        }
    }

    static Stream<JsonRpcId> ids() {
        return Stream.concat(Stream.of(JsonRpcId.of(""), JsonRpcId.of("123"), JsonRpcId.of("  request-1  ")),
                Stream.of("123", "123.5", "123456789012345678901234567890",
                                "12345678901234567890.12345678900", "1.2300e+12")
                        .map(number -> JsonRpcId.of(new BigDecimal(number))));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "{\"secret\":\"sensitive-value\",}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"id\":2,\"method\":\"m\"}",
            "{} {}"
    })
    void mapsJsonParsingFailuresToParseErrorWithoutRecoveringIds(String json) {
        JsonParseException failure = assertThrows(JsonParseException.class, () -> parser.parse(json));

        assertFalse(failure.isResourceLimit());
        assertEquals(JsonRpcErrorResponses.parseError(), JsonRpcErrorResponses.fromParsingFailure(failure));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "[]",
            "{\"jsonrpc\":\"1.0\",\"id\":123,\"method\":\"m\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":true,\"method\":\"m\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":123}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"m\",\"params\":null}"
    })
    void mapsStructureFailuresToInvalidRequestWithoutGuessingIdsOrSuppressingIdlessFailures(String json) {
        JsonRpcValidationException failure = assertThrows(JsonRpcValidationException.class, () -> parser.parse(json));

        assertEquals(JsonRpcErrorResponses.invalidRequest(), JsonRpcErrorResponses.fromValidationFailure(failure));
    }

    @ParameterizedTest
    @MethodSource("resourceFailures")
    void rethrowsResourceLimitFailuresFromTheParserUnchanged(JsonCodec limitedCodec, String json) {
        var limitedParser = new JsonRpcMessageParser(limitedCodec);
        JsonParseException failure = assertThrows(JsonParseException.class, () -> limitedParser.parse(json));

        assertTrue(failure.isResourceLimit());
        assertSame(failure, assertThrows(JsonParseException.class,
                () -> JsonRpcErrorResponses.fromParsingFailure(failure)));
    }

    static Stream<Arguments> resourceFailures() {
        String requestPrefix = "{\"jsonrpc\":\"2.0\",\"id\":";
        String requestSuffix = ",\"method\":\"m\"}";
        return Stream.of(
                arguments(new JacksonJsonCodec(1, 64), "{\"jsonrpc\":\"2.0\",\"method\":\"m\"}"),
                arguments(new JacksonJsonCodec(256, 1), "{\"jsonrpc\":\"2.0\",\"method\":\"m\",\"params\":{}}"),
                arguments(new JacksonJsonCodec(), requestPrefix + "9".repeat(1001) + requestSuffix),
                arguments(new JacksonJsonCodec(), requestPrefix + "-" + "9".repeat(1000) + requestSuffix));
    }

    @Test
    void doesNotCopyDiagnosticMessagesOrCausesIntoResponses() {
        String diagnostic = "secret-token /private/config dev.nexcraft.InternalFailure";
        var cause = new IllegalStateException(diagnostic);

        assertEquals(JsonRpcErrorResponses.parseError(), JsonRpcErrorResponses.fromParsingFailure(
                new JsonParseException(diagnostic, cause)));
        assertEquals(JsonRpcErrorResponses.invalidRequest(), JsonRpcErrorResponses.fromValidationFailure(
                new JsonRpcValidationException(diagnostic, cause)));
    }

    @Test
    void rejectsNullIdsInIdTakingFactories() {
        assertThrows(NullPointerException.class, () -> JsonRpcErrorResponses.invalidRequest(null));
        assertThrows(NullPointerException.class, () -> JsonRpcErrorResponses.methodNotFound(null));
        assertThrows(NullPointerException.class, () -> JsonRpcErrorResponses.invalidParams(null));
        assertThrows(NullPointerException.class, () -> JsonRpcErrorResponses.internalError(null));
    }

    @Test
    void rejectsNullFailureArguments() {
        assertThrows(NullPointerException.class, () -> JsonRpcErrorResponses.fromParsingFailure(null));
        assertThrows(NullPointerException.class, () -> JsonRpcErrorResponses.fromValidationFailure(null));
    }
}
