package dev.nexcraft.maegae;

import dev.nexcraft.maegae.internal.json.InternalFixtures;
import dev.nexcraft.maegae.protocol.ProtocolFixtures;
import java.util.List;

public final class CoreFixtures {
    public static class NettyDependency {
        private io.netty.fixture.NetworkFixture network;
    }
    public static class ProcessorDependency {
        private dev.nexcraft.maegae.processor.ProcessorFixture processor;
    }
    public static class NettyModuleDependency {
        private dev.nexcraft.maegae.transport.netty.NettyModuleFixture transport;
    }
    public static class ExampleDependency {
        private dev.nexcraft.maegae.example.ExampleFixture example;
    }
    public static class FieldLeak {
        public InternalFixtures.Value value;
    }
    public static class ConstructorLeak {
        public ConstructorLeak(InternalFixtures.Value value) { }
    }
    public static class ReturnLeak {
        public InternalFixtures.Value value() { return null; }
    }
    public static class ParameterLeak {
        public void accept(InternalFixtures.Value value) { }
    }
    public static class ExceptionLeak {
        protected void execute() throws InternalFixtures.Failure { }
    }
    public static class ConstructorExceptionLeak {
        public ConstructorExceptionLeak() throws InternalFixtures.Failure { }
    }
    public static class ArrayLeak {
        public InternalFixtures.Value[][] values() { return null; }
    }
    public static class GenericLeak {
        public List<? extends InternalFixtures.Value[]> values() { return null; }
    }
    public static class LowerBoundLeak {
        public void accept(List<? super InternalFixtures.Value> values) { }
    }
    public static class ClassBoundLeak<T extends InternalFixtures.Value> { }
    public static class MethodBoundLeak {
        public <T extends InternalFixtures.Value> void execute() { }
    }
    public static class RecursiveBound<T extends Comparable<T>> { }
    public static class SuperclassLeak extends InternalFixtures.Value { }
    public static class InterfaceLeak implements InternalFixtures.Contract { }
    public static class GenericInterfaceLeak implements ProtocolFixtures.Contract<InternalFixtures.Value> { }
    public static class GenericSuperclassLeak extends GenericParent<InternalFixtures.Value> { }
    public static class GenericParent<T> { }
    static class HiddenParent {
        public InternalFixtures.Value inherited() { return null; }
    }
    public static class InheritedLeak extends HiddenParent { }
    public static class ImplementationOnly {
        private InternalFixtures.Value value;
        private List<InternalFixtures.Value> values;
        public String execute() { return new InternalFixtures.Value().toString(); }
    }
    static class HiddenApi {
        public InternalFixtures.Value value;
        public static class PublicNested {
            public InternalFixtures.Value value;
        }
    }
    public static class ProtectedNestedOwner {
        protected static class Leak {
            public InternalFixtures.Value value;
        }
        public static Class<?> fixtureType() { return Leak.class; }
    }
    public static class BootstrapCycle {
        private dev.nexcraft.maegae.tool.ToolFixtures.BootstrapCycle tool;
    }
}
