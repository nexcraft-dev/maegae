package dev.nexcraft.maegae.processor.schema;

public final class SchemaFixtures {
    public static class Valid {
        private dev.nexcraft.maegae.processor.model.ModelFixtures.Metadata model;
        private javax.lang.model.type.TypeMirror type;
    }
    public static class GeneratorDependency {
        private dev.nexcraft.maegae.processor.generator.GeneratorFixtures.Valid generator;
    }
    public static class EntryDependency {
        private dev.nexcraft.maegae.processor.EntryFixtures.Contract entry;
    }
    public static class Cycle {
        private dev.nexcraft.maegae.processor.model.ModelFixtures.Cycle model;
    }
}
