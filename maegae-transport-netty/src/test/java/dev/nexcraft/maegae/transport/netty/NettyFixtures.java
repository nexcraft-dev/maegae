package dev.nexcraft.maegae.transport.netty;

import dev.nexcraft.maegae.transport.netty.internal.InternalFixtures;
import java.util.List;

// All types are isolated architecture fixtures, not runtime transport code.
public final class NettyFixtures {
    public interface RootContract { }
    public static class Valid {
        private dev.nexcraft.maegae.transport.netty.http.HttpFixtures.Valid http;
        private InternalFixtures.Valid implementation;
    }
    public static class ProcessorDependency {
        private dev.nexcraft.maegae.processor.ProcessorFixture processor;
    }
    public static class ExampleDependency {
        private dev.nexcraft.maegae.example.ExampleFixture example;
    }
    public static class CoreInternalDependency {
        private dev.nexcraft.maegae.internal.CoreInternalFixture internal;
    }
    public static class RootCycle {
        private InternalFixtures.EntryCycle implementation;
    }
    public static class FieldLeak { public InternalFixtures.Value value; }
    public static class ConstructorLeak { public ConstructorLeak(InternalFixtures.Value value) { } }
    public static class ReturnLeak { public InternalFixtures.Value value() { return null; } }
    public static class ParameterLeak { public void accept(InternalFixtures.Value value) { } }
    public static class ExceptionLeak { public void run() throws InternalFixtures.Failure { } }
    public static class ConstructorExceptionLeak {
        public ConstructorExceptionLeak() throws InternalFixtures.Failure { }
    }
    public static class ArrayLeak { public InternalFixtures.Value[] values; }
    public static class GenericLeak { public List<InternalFixtures.Value> values; }
    public static class LowerBoundLeak { public List<? super InternalFixtures.Value> values; }
    public static class ClassBoundLeak<T extends InternalFixtures.Value> { }
    public static class MethodBoundLeak {
        public <T extends InternalFixtures.Value> void accept(T value) { }
    }
    public static class SuperclassLeak extends InternalFixtures.Value { }
    public static class InterfaceLeak implements InternalFixtures.Contract { }
    public static class GenericSuperclassLeak extends java.util.ArrayList<InternalFixtures.Value> { }
    public static class GenericInterfaceLeak implements java.util.function.Supplier<InternalFixtures.Value> {
        public InternalFixtures.Value get() { return null; }
    }
    static class HiddenParent { public InternalFixtures.Value inherited; }
    public static class InheritedLeak extends HiddenParent { }
    public static class ProtectedOwner {
        protected static class NestedLeak { protected InternalFixtures.Value value; }
        public static Class<?> fixtureType() { return NestedLeak.class; }
    }
    private static class HiddenApi {
        public InternalFixtures.Value value;
        public static class PublicNested { public InternalFixtures.Value value; }
    }
    public static List<Class<?>> hiddenApiTypes() {
        return List.of(HiddenApi.class, HiddenApi.PublicNested.class);
    }
    public static class ImplementationOnly {
        private InternalFixtures.Value state;
        public void run() { state = new InternalFixtures.Value(); }
    }
    public static class RecursiveBound<T extends RecursiveBound<T>> { }
}
