package dev.nexcraft.maegae.protocol.mcp;

import java.util.Objects;
import java.util.Optional;

/** Client or server identity with optional MCP 2025-11-25 title and description. */
record ImplementationInfo(String name, String version, Optional<String> title, Optional<String> description) {
    /** Requires identity fields and optional containers while preserving their contents. */
    ImplementationInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
    }

    /** Creates an identity without optional display metadata. */
    ImplementationInfo(final String name, final String version) {
        this(name, version, Optional.empty(), Optional.empty());
    }
}
