package dev.nexcraft.maegae.internal.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.json.JsonFactory;

import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Converts between JSON text and the internal immutable JSON value model.
 * Duplicate object property names are rejected at every nesting depth.
 */
public final class JacksonJsonCodec implements JsonCodec {

    private static final int DEFAULT_MAX_INPUT_BYTES = 1_048_576;
    private static final int DEFAULT_MAX_NESTING_DEPTH = 64;
    private static final int MAX_NUMBER_LENGTH = 1_000;
    private static final int ENCODE_BUFFER_SIZE = 8_192;

    private final int maxInputBytes;
    private final JsonFactory jsonFactory;

    /** Creates a codec with a 1 MiB UTF-8 input limit and a maximum nesting depth of 64. */
    public JacksonJsonCodec() {
        this(DEFAULT_MAX_INPUT_BYTES, DEFAULT_MAX_NESTING_DEPTH);
    }

    /**
     * Creates a codec with explicit input and nesting limits.
     *
     * @param maxInputBytes maximum number of UTF-8 bytes accepted by {@link #parse(String)}; must be positive
     * @param maxNestingDepth maximum number of nested JSON objects or arrays; zero permits primitive values only
     * @throws IllegalArgumentException if {@code maxInputBytes} is not positive or {@code maxNestingDepth} is negative
     */
    public JacksonJsonCodec(int maxInputBytes, int maxNestingDepth) {
        if (maxInputBytes <= 0) {
            throw new IllegalArgumentException("maxInputBytes must be positive");
        }
        if (maxNestingDepth < 0) {
            throw new IllegalArgumentException("maxNestingDepth must not be negative");
        }

        this.maxInputBytes = maxInputBytes;
        this.jsonFactory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(maxNestingDepth)
                        .maxNumberLength(MAX_NUMBER_LENGTH)
                        .build())
                .streamWriteConstraints(StreamWriteConstraints.builder()
                        .maxNestingDepth(maxNestingDepth)
                        .build())
                .build();
    }

    @Override
    public JsonValue parse(String json) {
        Objects.requireNonNull(json, "json");
        validateUtf8Input(json);

        try (JsonParser parser = jsonFactory.createParser(ObjectReadContext.empty(), json)) {
            JsonToken firstToken = parser.nextToken();
            if (firstToken == null) {
                throw new JsonParseException("JSON input is empty");
            }

            JsonValue value = parseValue(parser, firstToken);
            if (parser.nextToken() != null) {
                throw new JsonParseException("JSON input contains trailing content");
            }
            return value;
        } catch (StreamConstraintsException exception) {
            throw JsonParseException.resourceLimit("JSON input exceeds a resource limit", exception);
        } catch (JacksonException exception) {
            throw new JsonParseException("Malformed JSON input", exception);
        }
    }

    @Override
    public String serialize(JsonValue value) {
        Objects.requireNonNull(value, "value");

        var output = new StringWriter();
        try (JsonGenerator generator = jsonFactory.createGenerator(ObjectWriteContext.empty(), output)) {
            writeValue(generator, value);
        } catch (JacksonException exception) {
            throw new JsonSerializationException("Unable to serialize JSON value", exception);
        }
        return output.toString();
    }

    private void validateUtf8Input(String json) {
        var encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer input = CharBuffer.wrap(json);
        ByteBuffer output = ByteBuffer.allocate(ENCODE_BUFFER_SIZE);
        long byteCount = 0;

        try {
            while (true) {
                CoderResult result = encoder.encode(input, output, true);
                byteCount += output.position();
                if (byteCount > maxInputBytes) {
                    throw JsonParseException.resourceLimit("JSON input exceeds the maximum UTF-8 size");
                }
                output.clear();
                if (result.isError()) {
                    result.throwException();
                }
                if (result.isUnderflow()) {
                    break;
                }
            }

            while (true) {
                CoderResult result = encoder.flush(output);
                byteCount += output.position();
                if (byteCount > maxInputBytes) {
                    throw JsonParseException.resourceLimit("JSON input exceeds the maximum UTF-8 size");
                }
                output.clear();
                if (result.isError()) {
                    result.throwException();
                }
                if (result.isUnderflow()) {
                    return;
                }
            }
        } catch (CharacterCodingException exception) {
            throw new JsonParseException("JSON input contains malformed UTF-16", exception);
        }
    }

    private static void writeValue(JsonGenerator generator, JsonValue value) throws JacksonException {
        switch (value) {
            case JsonObject object -> {
                generator.writeStartObject();
                for (var entry : object.values().entrySet()) {
                    generator.writeName(entry.getKey());
                    writeValue(generator, entry.getValue());
                }
                generator.writeEndObject();
            }
            case JsonArray array -> {
                generator.writeStartArray();
                for (JsonValue element : array.values()) {
                    writeValue(generator, element);
                }
                generator.writeEndArray();
            }
            case JsonString string -> generator.writeString(string.value());
            case JsonNumber number -> generator.writeNumber(number.value());
            case JsonBoolean bool -> generator.writeBoolean(bool.value());
            case JsonNull ignored -> generator.writeNull();
        }
    }

    private static JsonValue parseValue(JsonParser parser, JsonToken token) {
        return switch (token) {
            case START_OBJECT -> parseObject(parser);
            case START_ARRAY -> parseArray(parser);
            case VALUE_STRING -> JsonValue.of(parser.getString());
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> parseNumber(parser);
            case VALUE_TRUE -> JsonValue.of(true);
            case VALUE_FALSE -> JsonValue.of(false);
            case VALUE_NULL -> JsonNull.instance();
            default -> throw new JsonParseException("JSON value expected");
        };
    }

    private static JsonNumber parseNumber(JsonParser parser) {
        if (parser.getStringLength() > MAX_NUMBER_LENGTH) {
            throw JsonParseException.resourceLimit("JSON number exceeds the maximum length");
        }
        return JsonValue.of(parser.getDecimalValue());
    }

    private static JsonObject parseObject(JsonParser parser) {
        var members = new LinkedHashMap<String, JsonValue>();
        while (true) {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.END_OBJECT) {
                return new JsonObject(members);
            }
            if (token != JsonToken.PROPERTY_NAME) {
                throw new JsonParseException("JSON object member name expected");
            }

            String name = parser.getString();
            JsonToken valueToken = parser.nextToken();
            if (valueToken == null) {
                throw new JsonParseException("JSON object member value is missing");
            }
            members.put(name, parseValue(parser, valueToken));
        }
    }

    private static JsonArray parseArray(JsonParser parser) {
        var values = new ArrayList<JsonValue>();
        while (true) {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.END_ARRAY) {
                return new JsonArray(values);
            }
            if (token == null) {
                throw new JsonParseException("JSON array is incomplete");
            }
            values.add(parseValue(parser, token));
        }
    }
}
