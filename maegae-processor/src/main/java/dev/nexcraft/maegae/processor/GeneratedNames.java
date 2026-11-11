package dev.nexcraft.maegae.processor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Derives generated class name suffixes from provider binary names.
 * A suffix has a fixed length, so generated file names stay below file system limits
 * for any provider name. Two different providers must never share a suffix.
 */
final class GeneratedNames {
    private final Function<String, String> suffixFunction;
    private final Map<String, String> owners = new HashMap<>();

    GeneratedNames() {
        this(GeneratedNames::sha256Hex);
    }

    GeneratedNames(final Function<String, String> suffixFunction) {
        this.suffixFunction = suffixFunction;
    }

    /** Returns the deterministic suffix for a provider binary name. */
    String suffix(final String binaryName) {
        return suffixFunction.apply(binaryName);
    }

    /**
     * Records that a provider uses a suffix.
     *
     * @return the binary name of another provider that already uses the suffix, if any
     */
    Optional<String> claim(final String suffix, final String binaryName) {
        final String owner = owners.putIfAbsent(suffix, binaryName);
        return owner == null || owner.equals(binaryName) ? Optional.empty() : Optional.of(owner);
    }

    private static String sha256Hex(final String binaryName) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(binaryName.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by every Java platform.", exception);
        }
    }
}
