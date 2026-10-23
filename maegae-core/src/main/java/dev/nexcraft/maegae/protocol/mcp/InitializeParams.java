package dev.nexcraft.maegae.protocol.mcp;

import dev.nexcraft.maegae.internal.json.JsonNumber;
import dev.nexcraft.maegae.internal.json.JsonObject;
import dev.nexcraft.maegae.internal.json.JsonString;

import java.util.Objects;
import java.util.Optional;

/** MCP 2025-11-25 initialize parameters, separate from the JSON-RPC envelope. */
record InitializeParams(ProtocolVersion protocolVersion, ClientCapabilities capabilities,
                        ImplementationInfo clientInfo, Optional<JsonObject> meta) {
    /** Requires the initialization components and validates an optional progress token. */
    InitializeParams {
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(clientInfo, "clientInfo");
        Objects.requireNonNull(meta, "meta");
        meta.ifPresent(metadata -> {
            final var token = metadata.values().get("progressToken");
            if (token != null && !(token instanceof JsonString || token instanceof JsonNumber)) {
                throw new IllegalArgumentException("progressToken must be a string or number");
            }
        });
    }

    /** Creates initialize parameters without optional request metadata. */
    InitializeParams(final ProtocolVersion protocolVersion, final ClientCapabilities capabilities,
                     final ImplementationInfo clientInfo) {
        this(protocolVersion, capabilities, clientInfo, Optional.empty());
    }
}
