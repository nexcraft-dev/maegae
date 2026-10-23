package dev.nexcraft.maegae.protocol.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolVersionTest {
    @ParameterizedTest
    @ValueSource(strings = {"2025-11-25", "2024-11-05", "2028-02-29", "2099-12-31"})
    void preservesValidDatesWithoutRestrictingSupportedRevisions(final String value) {
        assertEquals(value, new ProtocolVersion(value).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1.0.0", "2025-1-25", "2025-11-5", "2025-02-29", "2025-13-01",
            "2025-04-31", " 2025-11-25", "2025-11-25\n", "２０２５-１１-２５"})
    void rejectsMalformedOrImpossibleDates(final String value) {
        assertThrows(IllegalArgumentException.class, () -> new ProtocolVersion(value));
    }

    @Test
    void rejectsMissingVersion() {
        assertThrows(NullPointerException.class, () -> new ProtocolVersion(null));
    }
}
