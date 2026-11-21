package io.github.rkaushikdev.swagebay.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.github.rkaushikdev.swagebay.util.Times;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.function.Function;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * How the service reads and writes JSON, in one place so the application and the tests of its
 * shapes cannot drift apart.
 *
 * <ul>
 *   <li>Field names are snake case: {@code acceptance_code}, {@code next_cursor}.
 *   <li>A decimal goes out as its text, {@code "9.53"}, never as a JSON number. A client reading a
 *       wall thickness through a floating point number gets a thickness nobody measured.
 *   <li>A day is {@code 2025-07-02}; a moment is a wall clock time with the seconds always
 *       written, {@code 2025-03-06T23:30:00}, read back from the same form, with a space for the
 *       {@code T} or as a bare day meaning its midnight. A moment with an offset is refused: every
 *       moment the shop records is a wall clock time.
 *   <li>A text field takes a JSON string and nothing else, so {@code 9.53} sent where a length's
 *       text belongs is refused rather than read as {@code "9.53"}; a whole number field refuses a
 *       number with a fraction rather than dropping it.
 *   <li>A field nobody declared is ignored.
 * </ul>
 */
public final class Json {

    private Json() {}

    /** Apply the service's JSON rules to a builder: the application's, or a test's. */
    public static void configure(Jackson2ObjectMapperBuilder builder) {
        builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .postConfigurer(Json::finish);
    }

    /** A mapper built the way the service builds its own. */
    public static ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        configure(builder);
        return builder.build();
    }

    /**
     * What has to be added once the builder has done its own work: the value types, registered last
     * so they win over the defaults for the same types, and text that only a string may fill.
     */
    private static void finish(ObjectMapper mapper) {
        mapper.registerModule(values());
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    /** Decimals as their text, days and moments in the service's own written form. */
    private static SimpleModule values() {
        SimpleModule module = new SimpleModule("swagebay");
        module.addSerializer(BigDecimal.class, new Written<>(BigDecimal::toString));
        module.addSerializer(LocalDate.class, new Written<>(Times::iso));
        module.addSerializer(LocalDateTime.class, new Written<>(Times::iso));
        module.addDeserializer(LocalDate.class, new Read<>(LocalDate.class, LocalDate::parse));
        module.addDeserializer(LocalDateTime.class, new Read<>(LocalDateTime.class, Times::parse));
        return module;
    }

    /** A value written as the string {@code text} gives for it. */
    private static final class Written<T> extends JsonSerializer<T> {

        private final Function<T, String> text;

        Written(Function<T, String> text) {
            this.text = text;
        }

        @Override
        public void serialize(T value, JsonGenerator out, SerializerProvider provider) throws IOException {
            out.writeString(text.apply(value));
        }
    }

    /**
     * A day or a moment read from a JSON string by {@code parser}. Anything other than a string, or
     * a string the parser refuses, is refused with the parser's reason.
     */
    private static final class Read<T> extends JsonDeserializer<T> {

        private final Class<T> type;
        private final Function<String, T> parser;

        Read(Class<T> type, Function<String, T> parser) {
            this.type = type;
            this.parser = parser;
        }

        @Override
        public T deserialize(JsonParser in, DeserializationContext context) throws IOException {
            if (in.currentToken() != JsonToken.VALUE_STRING) {
                return type.cast(context.handleUnexpectedToken(type, in));
            }
            String text = in.getText();
            try {
                return parser.apply(text);
            } catch (DateTimeParseException refused) {
                throw InvalidFormatException.from(in, refused.getMessage(), text, type);
            }
        }

        @Override
        public Class<?> handledType() {
            return type;
        }
    }
}
