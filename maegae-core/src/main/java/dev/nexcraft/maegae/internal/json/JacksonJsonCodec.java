package dev.nexcraft.maegae.internal.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Converts between JSON text and the internal immutable JSON value model.
 */
public final class JacksonJsonCodec implements JsonCodec {

    private static final JsonFactory JSON_FACTORY = JsonFactory.builder().build();

    @Override
    public JsonValue parse(String json) {
        Objects.requireNonNull(json, "json");

        try (JsonParser parser = JSON_FACTORY.createParser(ObjectReadContext.empty(), json)) {
            JsonToken firstToken = parser.nextToken();
            if (firstToken == null) {
                throw new JsonParseException("JSON input is empty");
            }

            JsonValue value = parseValue(parser, firstToken);
            if (parser.nextToken() != null) {
                throw new JsonParseException("JSON input contains trailing content");
            }
            return value;
        } catch (JacksonException exception) {
            throw new JsonParseException("Malformed JSON input", exception);
        }
    }

    @Override
    public String serialize(JsonValue value) {
        Objects.requireNonNull(value, "value");

        var output = new StringWriter();
        try (JsonGenerator generator = JSON_FACTORY.createGenerator(ObjectWriteContext.empty(), output)) {
            writeValue(generator, value);
        } catch (JacksonException exception) {
            throw new JsonSerializationException("Unable to serialize JSON value", exception);
        }
        return output.toString();
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
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> JsonValue.of(parser.getDecimalValue());
            case VALUE_TRUE -> JsonValue.of(true);
            case VALUE_FALSE -> JsonValue.of(false);
            case VALUE_NULL -> JsonNull.instance();
            default -> throw new JsonParseException("JSON value expected");
        };
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
