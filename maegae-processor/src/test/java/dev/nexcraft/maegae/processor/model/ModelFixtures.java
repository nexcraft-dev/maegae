package dev.nexcraft.maegae.processor.model;

public final class ModelFixtures {
    public static class Metadata {
        private javax.lang.model.element.TypeElement element;
        private dev.nexcraft.maegae.tool.ToolContractFixture coreContract;
    }
    public static class SchemaDependency {
        private dev.nexcraft.maegae.processor.schema.SchemaFixtures.Valid schema;
    }
    public static class GeneratorDependency {
        private dev.nexcraft.maegae.processor.generator.GeneratorFixtures.Valid generator;
    }
    public static class EntryDependency {
        private dev.nexcraft.maegae.processor.EntryFixtures.Contract entry;
    }
    public static class Cycle {
        private dev.nexcraft.maegae.processor.schema.SchemaFixtures.Cycle schema;
    }
}
