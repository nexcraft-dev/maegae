package dev.nexcraft.maegae.protocol.mcp;

import dev.nexcraft.maegae.internal.json.JsonArray;
import dev.nexcraft.maegae.internal.json.JsonNull;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InitializeParamsTest {
    private static final ProtocolVersion VERSION = new ProtocolVersion("2025-11-25");
    private static final ClientCapabilities CAPABILITIES = ClientCapabilities.empty();
    private static final ImplementationInfo CLIENT = new ImplementationInfo("example-client", "dev-build");

    @Test
    void preservesRequiredRequestPayloadWithoutInventingMetadata() {
        final var params = new InitializeParams(VERSION, CAPABILITIES, CLIENT);

        assertEquals(VERSION, params.protocolVersion());
        assertEquals(CAPABILITIES, params.capabilities());
        assertEquals(CLIENT, params.clientInfo());
        assertEquals(Optional.empty(), params.meta());
    }

    @ParameterizedTest
    @MethodSource("requestMetadata")
    void preservesOptionalMetadataAndValidProgressTokens(final JsonObject metadata) {
        final var params = new InitializeParams(VERSION, CAPABILITIES, CLIENT, Optional.of(metadata));

        assertEquals(Optional.of(metadata), params.meta());
    }

    static Stream<JsonObject> requestMetadata() {
        return Stream.of(JsonObject.of(), JsonObject.of("example/context", JsonNull.INSTANCE),
                JsonObject.of("progressToken", JsonValue.of("progress-1")),
                JsonObject.of("progressToken", JsonValue.of(123)),
                JsonObject.of("progressToken", JsonValue.of(new BigDecimal("12345678901234567890.125"))));
    }

    @Test
    void rejectsInvalidPresentProgressTokens() {
        for (final JsonValue token : List.of(JsonNull.INSTANCE, JsonValue.of(true), JsonObject.of(), JsonArray.of())) {
            assertThrows(IllegalArgumentException.class, () -> new InitializeParams(VERSION, CAPABILITIES, CLIENT,
                    Optional.of(JsonObject.of("progressToken", token))));
        }
    }

    @Test
    void rejectsMissingRequiredComponentsAndMetadataContainer() {
        assertThrows(NullPointerException.class, () -> new InitializeParams(null, CAPABILITIES, CLIENT));
        assertThrows(NullPointerException.class, () -> new InitializeParams(VERSION, null, CLIENT));
        assertThrows(NullPointerException.class, () -> new InitializeParams(VERSION, CAPABILITIES, null));
        assertThrows(NullPointerException.class, () -> new InitializeParams(VERSION, CAPABILITIES, CLIENT, null));
    }
}
