package dev.nexcraft.maegae.protocol.mcp;

import dev.nexcraft.maegae.tool.ToolInvoker;

import java.util.List;
import java.util.Map;

/**
 * Connects one Tool provider class to its generated Tool metadata and invokers.
 *
 * <p>The annotation processor generates one implementation for each provider class and lists it in
 * {@code META-INF/services}. An implementation needs a public no-argument constructor, because
 * {@link java.util.ServiceLoader} requires it. Do not implement this interface by hand.</p>
 */
public interface ToolProviderBinding {
    /**
     * Returns the provider class. For a generic provider this is the raw class.
     *
     * @return the exact provider class
     */
    Class<?> providerType();

    /**
     * Returns the Tool definitions of the provider, sorted by Tool name.
     *
     * @return an immutable list
     */
    List<ToolDefinition> tools();

    /**
     * Returns the Tool invokers of a provider instance by Tool name.
     *
     * @param provider an instance of {@link #providerType()}
     * @return an immutable map in Tool name order
     * @throws NullPointerException     if provider is null
     * @throws IllegalArgumentException if provider is not an instance of {@link #providerType()}; the message
     *                                  names the expected class
     */
    Map<String, ToolInvoker> invokers(Object provider);
}
