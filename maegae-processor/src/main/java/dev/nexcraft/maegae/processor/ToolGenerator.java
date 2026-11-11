package dev.nexcraft.maegae.processor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Generates the metadata class, the invocation adapter and the binding of one provider as one unit.
 * If any declaration is invalid, or any generated name is taken, no file is written.
 */
final class ToolGenerator {
    private static final String METADATA_KIND = "Tool metadata";
    private static final String INVOKERS_KIND = "Tool invokers";
    private static final String BINDING_KIND = "Tool binding";

    private final ProcessingEnvironment environment;
    private final GeneratedNames names;

    ToolGenerator(final ProcessingEnvironment environment, final GeneratedNames names) {
        this.environment = environment;
        this.names = names;
    }

    /**
     * Generates the three classes of a provider.
     *
     * @return the qualified name of the binding if all three classes were written, otherwise empty
     */
    Optional<String> generate(final TypeElement provider, final List<ToolDeclaration> declarations) {
        final List<ToolDeclaration> sorted = declarations.stream()
                .sorted(Comparator.comparing(ToolDeclaration::name)).toList();
        final String packageName = environment.getElementUtils().getPackageOf(provider).getQualifiedName().toString();
        final String binaryName = environment.getElementUtils().getBinaryName(provider).toString();
        final String suffix = names.suffix(binaryName);
        final var owner = names.claim(suffix, binaryName);
        if (owner.isPresent()) {
            environment.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Generated class name suffix '" + suffix + "' is shared by providers '" + owner.get()
                            + "' and '" + binaryName + "'.", provider);
            return Optional.empty();
        }
        final String metadataClass = "MaegaeToolMetadata_" + suffix;
        final String invokersClass = "MaegaeToolInvokers_" + suffix;
        final String bindingClass = "MaegaeToolBinding_" + suffix;
        final var messager = environment.getMessager();
        // Render both sources before the first check, so one run reports every diagnostic.
        final var metadata = new ToolMetadataGenerator(messager).source(packageName, metadataClass, sorted);
        final var invokers = new ToolInvokerGenerator(messager).source(packageName, invokersClass, provider, sorted);
        if (metadata.isEmpty() || invokers.isEmpty()) {
            return Optional.empty();
        }
        final Source metadataSource = new Source(METADATA_KIND, qualified(packageName, metadataClass),
                metadata.get());
        final Source invokersSource = new Source(INVOKERS_KIND, qualified(packageName, invokersClass),
                invokers.get());
        final Source bindingSource = new Source(BINDING_KIND, qualified(packageName, bindingClass),
                new ToolBindingGenerator().source(packageName, bindingClass, provider, metadataClass,
                        invokersClass));
        if (taken(metadataSource, provider) | taken(invokersSource, provider) | taken(bindingSource, provider)) {
            return Optional.empty();
        }
        final List<Element> origins = new ArrayList<>();
        origins.add(provider);
        sorted.forEach(declaration -> origins.add(declaration.method()));
        final boolean written = write(metadataSource, origins, provider)
                && write(invokersSource, origins, provider) && write(bindingSource, origins, provider);
        return written ? Optional.of(bindingSource.qualifiedName()) : Optional.empty();
    }

    private boolean taken(final Source source, final TypeElement provider) {
        if (environment.getElementUtils().getTypeElement(source.qualifiedName()) == null) {
            return false;
        }
        environment.getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot generate " + source.kind() + " '"
                + source.qualifiedName() + "': a class with that name already exists.", provider);
        return true;
    }

    private boolean write(final Source source, final List<Element> origins, final TypeElement provider) {
        try (final var writer = environment.getFiler().createSourceFile(source.qualifiedName(),
                origins.toArray(Element[]::new)).openWriter()) {
            writer.write(source.text());
            return true;
        } catch (IOException exception) {
            environment.getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot generate " + source.kind() + " '"
                    + source.qualifiedName() + "': " + exception.getMessage(), provider);
            return false;
        }
    }

    private static String qualified(final String packageName, final String className) {
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    private record Source(String kind, String qualifiedName, String text) { }
}
