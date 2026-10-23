package dev.nexcraft.maegae.protocol.mcp;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImplementationInfoTest {
    @Test
    void preservesIdentityWithoutInventingDisplayMetadata() {
        final var info = new ImplementationInfo(" client ", " release candidate ");

        assertEquals(" client ", info.name());
        assertEquals(" release candidate ", info.version());
        assertEquals(Optional.empty(), info.title());
        assertEquals(Optional.empty(), info.description());
    }

    @Test
    void preservesOptionalDisplayTextIncludingExplicitEmptyStrings() {
        final var info = new ImplementationInfo("", "", Optional.of(" display "), Optional.of(""));

        assertEquals("", info.name());
        assertEquals("", info.version());
        assertEquals(Optional.of(" display "), info.title());
        assertEquals(Optional.of(""), info.description());
    }

    @Test
    void rejectsMissingIdentityFieldsAndOptionalContainers() {
        assertThrows(NullPointerException.class, () -> new ImplementationInfo(null, "1"));
        assertThrows(NullPointerException.class, () -> new ImplementationInfo("client", null));
        assertThrows(NullPointerException.class,
                () -> new ImplementationInfo("client", "1", null, Optional.empty()));
        assertThrows(NullPointerException.class,
                () -> new ImplementationInfo("client", "1", Optional.empty(), null));
    }
}
