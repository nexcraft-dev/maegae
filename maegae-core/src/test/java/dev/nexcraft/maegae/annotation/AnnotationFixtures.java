package dev.nexcraft.maegae.annotation;

public final class AnnotationFixtures {
    public @interface Valid {
        String value();
        Kind kind() default Kind.DEFAULT;
    }
    public enum Kind { DEFAULT }
    public interface ToolDependency {
        dev.nexcraft.maegae.tool.ToolFixtures.Contract tool();
    }
    public interface ProcessorDependency {
        dev.nexcraft.maegae.processor.ProcessorFixture processor();
    }
    public interface NettyDependency {
        io.netty.fixture.NetworkFixture network();
    }
}
