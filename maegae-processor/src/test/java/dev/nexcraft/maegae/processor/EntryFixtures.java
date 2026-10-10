package dev.nexcraft.maegae.processor;

import dev.nexcraft.maegae.processor.generator.GeneratorFixtures;
import dev.nexcraft.maegae.processor.model.ModelFixtures;
import dev.nexcraft.maegae.processor.schema.SchemaFixtures;

// Test-only entry-point roles; no annotation processor is implemented.
public final class EntryFixtures {
    public interface Contract { }
    public static class Valid {
        private ModelFixtures.Metadata model;
        private SchemaFixtures.Valid schema;
        private GeneratorFixtures.Valid generator;
        private javax.annotation.processing.ProcessingEnvironment environment;
        private javax.lang.model.element.Element element;
        private javax.tools.JavaFileObject source;
        private dev.nexcraft.maegae.annotation.AnnotationContractFixture annotation;
        private dev.nexcraft.maegae.tool.ToolContractFixture tool;
    }
    public static class NettyDependency {
        private io.netty.fixture.ProcessorNetworkFixture network;
    }
    public static class TransportDependency {
        private dev.nexcraft.maegae.transport.netty.ProcessorTransportFixture transport;
    }
    public static class ExampleDependency {
        private dev.nexcraft.maegae.example.ProcessorExampleFixture example;
    }
    public static class InternalDependency {
        private dev.nexcraft.maegae.internal.ProcessorInternalFixture internal;
    }
    public static class Cycle {
        private GeneratorFixtures.EntryCycle generator;
    }
}
