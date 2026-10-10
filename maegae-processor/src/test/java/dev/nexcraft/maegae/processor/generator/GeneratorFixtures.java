package dev.nexcraft.maegae.processor.generator;

public final class GeneratorFixtures {
    public static class Valid {
        private dev.nexcraft.maegae.processor.model.ModelFixtures.Metadata model;
        private dev.nexcraft.maegae.processor.schema.SchemaFixtures.Valid schema;
        private javax.annotation.processing.Filer filer;
    }
    public static class EntryDependency {
        private dev.nexcraft.maegae.processor.EntryFixtures.Contract entry;
    }
    public static class EntryCycle {
        private dev.nexcraft.maegae.processor.EntryFixtures.Cycle entry;
    }
}
