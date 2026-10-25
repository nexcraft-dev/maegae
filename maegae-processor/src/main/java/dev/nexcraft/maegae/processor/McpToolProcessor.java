package dev.nexcraft.maegae.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * Establishes the javac entry point for future MCP Tool processing.
 * This foundation performs no discovery, validation, generation, or diagnostic emission.
 */
public final class McpToolProcessor extends AbstractProcessor {
    /** Declares only Tool support. Arg remains parameter metadata. */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("dev.nexcraft.maegae.annotation.Tool");
    }

    /** Declares the project's Java 25 source contract. */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_25;
    }

    /**
     * Leaves annotations unclaimed because no meaningful processing is implemented yet.
     * All rounds, including empty and final rounds, require no work or retained state.
     */
    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        return false;
    }
}
