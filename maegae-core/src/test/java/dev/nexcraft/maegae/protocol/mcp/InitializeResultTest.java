package dev.nexcraft.maegae.protocol.mcp;

import dev.nexcraft.maegae.internal.json.JsonNull;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InitializeResultTest {
    private static final ProtocolVersion VERSION = new ProtocolVersion("2025-11-25");
    private static final ServerCapabilities CAPABILITIES = ServerCapabilities.toolsOnly();
    private static final ImplementationInfo SERVER = new ImplementationInfo("custom-server", "release-42");

    @Test
    void preservesCallerSuppliedVersionCapabilitiesAndIdentity() {
        final var result = new InitializeResult(VERSION, CAPABILITIES, SERVER);

        assertEquals(VERSION, result.protocolVersion());
        assertEquals(CAPABILITIES, result.capabilities());
        assertEquals(SERVER, result.serverInfo());
        assertEquals(Optional.empty(), result.instructions());
        assertEquals(Optional.empty(), result.meta());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " Use tools carefully.\n"})
    void preservesPresentInstructionsAndMetadataExactly(final String instructions) {
        final var metadata = JsonObject.of("example/info", JsonNull.INSTANCE, "count", JsonValue.of(2));
        final var result = new InitializeResult(VERSION, CAPABILITIES, SERVER,
                Optional.of(instructions), Optional.of(metadata));

        assertEquals(Optional.of(instructions), result.instructions());
        assertEquals(Optional.of(metadata), result.meta());
    }

    @Test
    void rejectsMissingRequiredComponentsAndOptionalContainers() {
        assertThrows(NullPointerException.class, () -> new InitializeResult(null, CAPABILITIES, SERVER));
        assertThrows(NullPointerException.class, () -> new InitializeResult(VERSION, null, SERVER));
        assertThrows(NullPointerException.class, () -> new InitializeResult(VERSION, CAPABILITIES, null));
        assertThrows(NullPointerException.class,
                () -> new InitializeResult(VERSION, CAPABILITIES, SERVER, null, Optional.empty()));
        assertThrows(NullPointerException.class,
                () -> new InitializeResult(VERSION, CAPABILITIES, SERVER, Optional.empty(), null));
    }
}
