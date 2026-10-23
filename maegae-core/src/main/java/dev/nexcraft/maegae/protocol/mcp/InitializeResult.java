package dev.nexcraft.maegae.protocol.mcp;

import dev.nexcraft.maegae.internal.json.JsonObject;

import java.util.Objects;
import java.util.Optional;

/** An MCP 2025-11-25 initialization result payload without response generation. */
record InitializeResult(ProtocolVersion protocolVersion, ServerCapabilities capabilities,
                        ImplementationInfo serverInfo, Optional<String> instructions, Optional<JsonObject> meta) {
    /** Requires the initialization components and preserves optional instructions and metadata. */
    InitializeResult {
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(serverInfo, "serverInfo");
        Objects.requireNonNull(instructions, "instructions");
        Objects.requireNonNull(meta, "meta");
    }

    /** Creates a result without optional instructions or metadata. */
    InitializeResult(final ProtocolVersion protocolVersion, final ServerCapabilities capabilities,
                     final ImplementationInfo serverInfo) {
        this(protocolVersion, capabilities, serverInfo, Optional.empty(), Optional.empty());
    }
}
