package dev.nexcraft.maegae.protocol.mcp;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** An exact date-based MCP revision identifier, without a supported-version policy. */
record ProtocolVersion(String value) {
    /** Validates the revision's format and calendar date without normalizing it. */
    ProtocolVersion {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException("Protocol version must use YYYY-MM-DD");
        }
        try {
            LocalDate.parse(value);
        } catch (final DateTimeParseException exception) {
            throw new IllegalArgumentException("Protocol version must be a valid calendar date", exception);
        }
    }
}
