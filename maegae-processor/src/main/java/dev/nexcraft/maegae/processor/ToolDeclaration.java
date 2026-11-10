package dev.nexcraft.maegae.processor;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/** Validated compiler-model information for a Tool method. */
record ToolDeclaration(TypeElement provider, ExecutableElement method, String name,
                       List<ArgumentDeclaration> arguments) { }

/** Validated compiler-model information for one Tool argument. */
record ArgumentDeclaration(VariableElement parameter, TypeMirror type, String name,
                           String description, boolean required) { }
