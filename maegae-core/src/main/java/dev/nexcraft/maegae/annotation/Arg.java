package dev.nexcraft.maegae.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares Tool parameter metadata for future compile-time processing.
 * This annotation is optional. The intended future policy uses source parameter names
 * and marks unannotated parameters as required.
 * No name resolution, runtime argument validation, or binding is implemented here.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface Arg {
    /**
     * The explicit argument name. An empty value denotes the Java parameter name from the compiler model.
     * Future name resolution requires neither runtime reflection nor {@code -parameters} metadata.
     *
     * @return the name override, or an empty string
     */
    String name() default "";

    /**
     * Human-readable argument documentation. An empty value means no description is provided.
     *
     * @return the description, or an empty string
     */
    String description() default "";

    /**
     * Whether future input-schema generation should mark the argument as required.
     * This is metadata only. A false value supplies no default, missing-argument conversion,
     * primitive null handling, or optional binding and does not make every Java type safely optional.
     *
     * @return whether the argument is required, defaulting to true
     */
    boolean required() default true;
}
