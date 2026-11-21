package io.github.rkaushikdev.swagebay.api;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.github.rkaushikdev.swagebay.SwagebayError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.validator.engine.HibernateConstraintViolation;

/**
 * Everything a request can be refused for, said the same way wherever it was found: a constraint on
 * a shape, a body that is not JSON or not the right kind of JSON, a query or path value of the wrong
 * kind.
 *
 * <p>The names and sentences are the ones a client of this service already handles, so a refusal
 * reads the same whichever way the request was wrong: {@code missing} and {@code Field required}, a
 * string too short or too long with the limit it broke, a number at or under its floor, a value of
 * the wrong kind, and {@code value_error} for a value the domain itself refused, quoting the
 * domain's error. Several problems on one shape come back in the order its fields are declared.
 */
final class Problems {

    static final String REQUIRED = "Field required";

    private static final ObjectMapper JSON = Json.mapper();
    private static final TypeReference<LinkedHashMap<String, Object>> FIELDS = new TypeReference<>() {};
    private static final PropertyNamingStrategies.NamingBase WIRE = new PropertyNamingStrategies.SnakeCaseStrategy();

    private Problems() {}

    /** Each violation as a problem below {@code root}, in the order the fields are declared. */
    static List<Problem> of(Collection<? extends ConstraintViolation<?>> violations, List<Object> root) {
        List<ConstraintViolation<?>> ordered = new ArrayList<>(violations);
        ordered.sort(
                Comparator.comparing(Problems::rank, Problems::compareRanks).thenComparing(violation -> violation
                        .getConstraintDescriptor()
                        .getAnnotation()
                        .annotationType()
                        .getName()));
        return ordered.stream().map(violation -> of(violation).under(root)).toList();
    }

    /** One violation as a problem, located from the shape that was validated. */
    static Problem of(ConstraintViolation<?> violation) {
        List<Object> loc = location(violation.getPropertyPath());
        Object value = violation.getInvalidValue();
        Annotation constraint = violation.getConstraintDescriptor().getAnnotation();
        return switch (constraint) {
            case NotNull required -> new Problem("missing", loc, REQUIRED, sent(violation.getLeafBean()));
            case Size size -> sized(loc, value, size);
            case Positive positive -> floor("greater_than", "greater than", 0, loc, value);
            case PositiveOrZero positiveOrZero ->
                floor("greater_than_equal", "greater than or equal to", 0, loc, value);
            case Min min -> floor("greater_than_equal", "greater than or equal to", min.value(), loc, value);
            case Pattern pattern ->
                new Problem(
                        "string_pattern_mismatch",
                        loc,
                        "String should match pattern '" + pattern.regexp() + "'",
                        value,
                        Map.of("pattern", pattern.regexp()));
            default -> refusedBy(loc, value, domainError(violation), violation.getMessage());
        };
    }

    /**
     * Why Jackson could not read a body, located from where it stopped: a body that is not JSON at
     * all, a value of the wrong kind, or a value whose shape refused to be built.
     */
    static Problem unreadable(JsonMappingException mapping) {
        List<Object> loc = location(mapping);
        if (mapping instanceof InvalidFormatException invalid) {
            return mismatch(loc, invalid.getTargetType(), invalid.getValue(), invalid.getOriginalMessage());
        }
        if (mapping instanceof MismatchedInputException mismatched && mismatched.getTargetType() != null) {
            return mismatch(loc, mismatched.getTargetType(), null, mismatched.getOriginalMessage());
        }
        Throwable cause = mapping.getCause() != null ? mapping.getCause() : mapping;
        SwagebayError domain = cause instanceof SwagebayError error ? error : null;
        return refusedBy(loc, null, Optional.ofNullable(domain), cause.getMessage());
    }

    /** A body that is not JSON, with where the reading stopped. */
    static Problem notJson(JsonParseException trouble) {
        JsonLocation where = trouble.getLocation();
        long at = where == null ? 0 : where.getCharOffset() >= 0 ? where.getCharOffset() : where.getByteOffset();
        return new Problem(
                "json_invalid",
                List.of("body", Math.max(at, 0)),
                "JSON decode error",
                Map.of(),
                Map.of("error", trouble.getOriginalMessage()));
    }

    /** A route that takes a body was sent none. */
    static Problem noBody() {
        return new Problem("missing", List.of("body"), REQUIRED, null);
    }

    /**
     * A value that is the wrong kind for {@code type}: text that is not a number, a number with a
     * fraction where a whole one belongs, anything but a string where text belongs, and so on.
     *
     * @param input the value as it was sent, when it could be read
     * @param reason why the value would not parse, for the kinds whose refusal says so
     */
    static Problem mismatch(List<Object> loc, Class<?> type, Object input, String reason) {
        boolean text = input instanceof String;
        String why = Objects.requireNonNullElse(reason, "");
        if (type == String.class) {
            return new Problem("string_type", loc, "Input should be a valid string", input);
        }
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            if (text) {
                return new Problem(
                        "int_parsing",
                        loc,
                        "Input should be a valid integer, unable to parse string as an integer",
                        input);
            }
            if (input instanceof Number) {
                return new Problem(
                        "int_from_float",
                        loc,
                        "Input should be a valid integer, got a number with a fractional part",
                        input);
            }
            return new Problem("int_type", loc, "Input should be a valid integer", input);
        }
        if (type == BigDecimal.class) {
            return text
                    ? new Problem("decimal_parsing", loc, "Input should be a valid decimal", input)
                    : new Problem(
                            "decimal_type",
                            loc,
                            "Decimal input should be an integer, float, string or Decimal object",
                            input);
        }
        if (type == Boolean.class || type == boolean.class) {
            return text
                    ? new Problem(
                            "bool_parsing", loc, "Input should be a valid boolean, unable to interpret input", input)
                    : new Problem("bool_type", loc, "Input should be a valid boolean", input);
        }
        if (type == LocalDate.class) {
            return text
                    ? new Problem(
                            "date_from_datetime_parsing",
                            loc,
                            "Input should be a valid date or datetime, " + why,
                            input,
                            Map.of("error", why))
                    : new Problem("date_type", loc, "Input should be a valid date", input);
        }
        if (type == LocalDateTime.class) {
            return text
                    ? new Problem(
                            "datetime_from_date_parsing",
                            loc,
                            "Input should be a valid datetime or date, " + why,
                            input,
                            Map.of("error", why))
                    : new Problem("datetime_type", loc, "Input should be a valid datetime", input);
        }
        if (Collection.class.isAssignableFrom(type)) {
            return new Problem("tuple_type", loc, "Input should be a valid tuple", input);
        }
        return new Problem(
                "model_attributes_type",
                loc,
                "Input should be a valid dictionary or object to extract fields from",
                input);
    }

    /** A string outside its bounds: under the floor if it is shorter than that, else over the ceiling. */
    private static Problem sized(List<Object> loc, Object value, Size size) {
        int length = value instanceof CharSequence text ? text.length() : 0;
        if (length < size.min()) {
            return new Problem(
                    "string_too_short",
                    loc,
                    "String should have at least " + characters(size.min()),
                    value,
                    Map.of("min_length", size.min()));
        }
        return new Problem(
                "string_too_long",
                loc,
                "String should have at most " + characters(size.max()),
                value,
                Map.of("max_length", size.max()));
    }

    private static String characters(int count) {
        return count + (count == 1 ? " character" : " characters");
    }

    private static Problem floor(String type, String words, long bound, List<Object> loc, Object value) {
        String name = type.equals("greater_than") ? "gt" : "ge";
        return new Problem(type, loc, "Input should be " + words + " " + bound, value, Map.of(name, bound));
    }

    /**
     * A value the domain refused, quoting the domain's error and carrying its message and context, or
     * the bare sentence when the refusal was not the domain's.
     */
    private static Problem refusedBy(List<Object> loc, Object value, Optional<SwagebayError> domain, String fallback) {
        if (domain.isEmpty()) {
            return new Problem("value_error", loc, "Value error, " + fallback, value);
        }
        SwagebayError error = domain.get();
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("message", error.message());
        facts.put("context", error.context());
        return new Problem("value_error", loc, "Value error, " + error.getMessage(), value, Map.of("error", facts));
    }

    /** The domain error a check kept when it refused, if it kept one. */
    private static Optional<SwagebayError> domainError(ConstraintViolation<?> violation) {
        if (!(violation instanceof HibernateConstraintViolation<?> kept)) {
            return Optional.empty();
        }
        return Optional.ofNullable(kept.getDynamicPayload(SwagebayError.class));
    }

    /**
     * The object a field is missing from, as it was sent: the fields that were given, written the
     * way the service writes them.
     */
    private static Object sent(Object owner) {
        if (owner == null) {
            return null;
        }
        Map<String, Object> fields = JSON.convertValue(owner, FIELDS);
        fields.values().removeIf(Objects::isNull);
        return fields;
    }

    /** A bean path as the wire spells it: {@code densities[0].throughIndicator} is {@code densities, 0, through_indicator}. */
    private static List<Object> location(Path path) {
        List<Object> loc = new ArrayList<>();
        for (var step : path) {
            if (step.getIndex() != null) {
                loc.add(step.getIndex());
            }
            if (named(step.getName())) {
                loc.add(WIRE.translate(step.getName()));
            }
        }
        return loc;
    }

    /** Where Jackson stopped, by the field names and list positions it had walked. */
    private static List<Object> location(JsonMappingException mapping) {
        List<Object> loc = new ArrayList<>();
        for (JsonMappingException.Reference step : mapping.getPath()) {
            if (step.getFieldName() != null) {
                loc.add(step.getFieldName());
            } else if (step.getIndex() >= 0) {
                loc.add(step.getIndex());
            }
        }
        return loc;
    }

    /** Whether a path step names a field, rather than standing for a container's element. */
    private static boolean named(String name) {
        return name != null && !name.startsWith("<");
    }

    /**
     * Where a violation sits in declaration order: the position of each field in its record, and the
     * position in each list, from the outside in.
     */
    private static List<Integer> rank(ConstraintViolation<?> violation) {
        List<Integer> rank = new ArrayList<>();
        Type type = violation.getRootBeanClass();
        for (var step : violation.getPropertyPath()) {
            if (step.getIndex() != null) {
                rank.add(step.getIndex());
            }
            if (!named(step.getName())) {
                continue;
            }
            RecordComponent[] components = components(type);
            int at = 0;
            while (at < components.length && !components[at].getName().equals(step.getName())) {
                at++;
            }
            rank.add(at);
            type = at < components.length ? components[at].getGenericType() : Object.class;
        }
        return rank;
    }

    /** The fields of the record behind {@code type}, looking through a list or an optional to its element. */
    private static RecordComponent[] components(Type type) {
        Type element = type;
        while (element instanceof ParameterizedType generic
                && (generic.getRawType() == List.class || generic.getRawType() == Optional.class)) {
            element = generic.getActualTypeArguments()[0];
        }
        if (element instanceof Class<?> shape && shape.isRecord()) {
            return shape.getRecordComponents();
        }
        return new RecordComponent[0];
    }

    private static int compareRanks(List<Integer> left, List<Integer> right) {
        for (int at = 0; at < Math.min(left.size(), right.size()); at++) {
            int order = Integer.compare(left.get(at), right.get(at));
            if (order != 0) {
                return order;
            }
        }
        return Integer.compare(left.size(), right.size());
    }
}
