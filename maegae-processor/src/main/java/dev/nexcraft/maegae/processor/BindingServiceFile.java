package dev.nexcraft.maegae.processor;

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.lang.model.element.Element;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.TreeSet;

/**
 * Writes the {@link java.util.ServiceLoader} file that lists every generated Tool binding.
 * The processor must not depend on Core types at run time, so the service name is a string constant.
 */
final class BindingServiceFile {
    /** The name of the Core service interface {@code dev.nexcraft.maegae.protocol.mcp.ToolProviderBinding}. */
    static final String SERVICE = "dev.nexcraft.maegae.protocol.mcp.ToolProviderBinding";
    static final String PATH = "META-INF/services/" + SERVICE;

    private BindingServiceFile() {
    }

    /** Renders one sorted binding name per line, with a newline at the end. */
    static String content(final Collection<String> bindings) {
        final StringBuilder result = new StringBuilder();
        new TreeSet<>(bindings).forEach(binding -> result.append(binding).append('\n'));
        return result.toString();
    }

    /**
     * Writes the file once.
     *
     * @throws FilerException if the file already exists
     * @throws IOException    if the file cannot be written
     */
    static void write(final Filer filer, final Collection<String> bindings, final Element... origins)
            throws IOException {
        final var file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", PATH, origins);
        try (final Writer writer = file.openWriter()) {
            writer.write(content(bindings));
        }
    }
}
