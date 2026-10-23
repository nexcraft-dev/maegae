package dev.nexcraft.maegae.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares MCP Tool metadata for future compile-time processing.
 * Name resolution and conflict validation belong to the future annotation processor.
 * This annotation imposes no return-type restriction and provides no invocation or result conversion.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Tool {
    /**
     * The explicit Tool name. An empty value denotes the Java method name for future processing.
     *
     * @return the name override, or an empty string
     */
    String name() default "";

    /**
     * Human-readable Tool documentation. An empty value means no description is provided.
     *
     * @return the description, or an empty string
     */
    String description() default "";
}
