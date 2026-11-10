package dev.nexcraft.maegae.processor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.Optional;

/** The Java types that a Tool can accept as arguments or return as results. */
enum ToolType {
    STRING("string", "getString", null),
    BOOLEAN("boolean", "getBoolean", "java.lang.Boolean"),
    BYTE("integer", "getByte", "java.lang.Byte"),
    SHORT("integer", "getShort", "java.lang.Short"),
    INT("integer", "getInt", "java.lang.Integer"),
    LONG("integer", "getLong", "java.lang.Long"),
    FLOAT("number", "getFloat", "java.lang.Float"),
    DOUBLE("number", "getDouble", "java.lang.Double"),
    BIG_INTEGER("integer", "getBigInteger", null),
    BIG_DECIMAL("number", "getBigDecimal", null);

    private final String jsonType;
    private final String getter;
    private final String boxedType;

    ToolType(final String jsonType, final String getter, final String boxedType) {
        this.jsonType = jsonType;
        this.getter = getter;
        this.boxedType = boxedType;
    }

    /** Returns the JSON Schema type name. */
    String jsonType() {
        return jsonType;
    }

    /** Returns the name of the {@code ToolArguments} getter. */
    String getter() {
        return getter;
    }

    /** Returns the wrapper class name for a primitive-compatible type, or null for a reference type. */
    String boxedType() {
        return boxedType;
    }

    /** Maps a compiler type to a supported type. Generic and unlisted types are unsupported. */
    static Optional<ToolType> of(final TypeMirror type) {
        return Optional.ofNullable(switch (type.getKind()) {
            case BOOLEAN -> BOOLEAN;
            case BYTE -> BYTE;
            case SHORT -> SHORT;
            case INT -> INT;
            case LONG -> LONG;
            case FLOAT -> FLOAT;
            case DOUBLE -> DOUBLE;
            case DECLARED -> declared((DeclaredType) type);
            default -> null;
        });
    }

    private static ToolType declared(final DeclaredType type) {
        if (!type.getTypeArguments().isEmpty() || !(type.asElement() instanceof TypeElement element)) {
            return null;
        }
        return switch (element.getQualifiedName().toString()) {
            case "java.lang.String" -> STRING;
            case "java.lang.Boolean" -> BOOLEAN;
            case "java.lang.Byte" -> BYTE;
            case "java.lang.Short" -> SHORT;
            case "java.lang.Integer" -> INT;
            case "java.lang.Long" -> LONG;
            case "java.lang.Float" -> FLOAT;
            case "java.lang.Double" -> DOUBLE;
            case "java.math.BigInteger" -> BIG_INTEGER;
            case "java.math.BigDecimal" -> BIG_DECIMAL;
            default -> null;
        };
    }
}
