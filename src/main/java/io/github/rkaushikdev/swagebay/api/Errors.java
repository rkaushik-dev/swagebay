package io.github.rkaushikdev.swagebay.api;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.QualificationError;
import io.github.rkaushikdev.swagebay.SafetyError;
import io.github.rkaushikdev.swagebay.SwagebayError;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import jakarta.validation.ConstraintViolation;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turning the domain errors into responses without inventing new ones.
 *
 * <p>Every error this package throws already carries a stable code and a context of named facts.
 * The response body is exactly {@link SwagebayError#asMap()}: code, message and context. The message
 * is not rewritten into something friendlier, because the message is what a coordinator will paste
 * into an email to a client and a friendlier version of it is a less exact one.
 *
 * <p>The status codes are the only decision made here, and they are a mapping rather than a
 * judgement. A validation error is the caller's fault, so 422. A conflict is a state problem rather
 * than a request problem, so 409. A technique error is a refusal to plan something impossible, which
 * is also the caller's fault but a different kind, so it gets 422 with its own code so a client can
 * tell them apart. A configuration error is ours, so 500.
 *
 * <p>A request that never reached its handler, because its body or a parameter was not the shape
 * the route takes, is refused here too, as a {@link RequestRejected}. Anything that is neither is
 * not caught: it is a fault, and it propagates as one.
 */
@RestControllerAdvice
public class Errors {

    /** The status each kind of error maps to, tried in order, so the specific ones come first. */
    public static final List<Mapping> STATUS_BY_ERROR = List.of(
            new Mapping(NotFoundError.class, 404),
            new Mapping(ConflictError.class, 409),
            new Mapping(ValidationError.class, 422),
            new Mapping(TechniqueError.class, 422),
            new Mapping(QualificationError.class, 422),
            new Mapping(SafetyError.class, 409),
            new Mapping(ConfigurationError.class, 500));

    /** The status of any error the mapping does not name. */
    static final int UNCLASSIFIED = 500;

    /** The status of a request that is not the shape its route takes. */
    static final int UNPROCESSABLE = 422;

    /** The first mapping that matches, most specific first; 500 when none does. */
    public static int statusFor(SwagebayError error) {
        return STATUS_BY_ERROR.stream()
                .filter(mapping -> mapping.kind().isInstance(error))
                .mapToInt(Mapping::status)
                .findFirst()
                .orElse(UNCLASSIFIED);
    }

    /** The response body of an error: its dictionary, unchanged. */
    public static Map<String, Object> errorBody(SwagebayError error) {
        return error.asMap();
    }

    /** The single handler every domain error goes through. */
    @ExceptionHandler(SwagebayError.class)
    public ResponseEntity<Map<String, Object>> domain(SwagebayError error) {
        return ResponseEntity.status(statusFor(error)).body(errorBody(error));
    }

    /** A request refused before its handler ran. */
    @ExceptionHandler(RequestRejected.class)
    public ResponseEntity<Map<String, Object>> rejected(RequestRejected rejected) {
        return ResponseEntity.status(UNPROCESSABLE).body(rejected.body());
    }

    /** A body that was read but broke the constraints on its shape. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException failure) {
        List<ConstraintViolation<?>> violations = failure.getBindingResult().getAllErrors().stream()
                .filter(error -> error.contains(ConstraintViolation.class))
                .<ConstraintViolation<?>>map(Errors::violation)
                .toList();
        return rejected(new RequestRejected(Problems.of(violations, List.of("body"))));
    }

    /** A body that could not be read at all: none, not JSON, or a value of the wrong kind. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException failure) {
        Problem problem =
                switch (failure.getCause()) {
                    case JsonParseException notJson -> Problems.notJson(notJson);
                    case JsonMappingException mapping ->
                        Problems.unreadable(mapping).under(List.of("body"));
                    case null, default -> Problems.noBody();
                };
        return rejected(new RequestRejected(List.of(problem)));
    }

    /** A query value the route needs, left out. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> missing(MissingServletRequestParameterException failure) {
        return rejected(new RequestRejected(List.of(Problems.noQueryValue(failure.getParameterName()))));
    }

    /** A query or path value that is not the kind the route takes. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> mismatched(MethodArgumentTypeMismatchException failure) {
        String where = failure.getParameter().hasParameterAnnotation(PathVariable.class) ? "path" : "query";
        Class<?> type = failure.getParameter().nestedIfOptional().getNestedParameterType();
        Problem problem = Problems.mismatch(
                List.of(where, failure.getName()),
                type,
                failure.getValue(),
                failure.getMostSpecificCause().getMessage());
        return rejected(new RequestRejected(List.of(problem)));
    }

    private static ConstraintViolation<?> violation(ObjectError error) {
        return error.unwrap(ConstraintViolation.class);
    }

    /**
     * One kind of error and the status it is answered with.
     *
     * @param kind the class of error, matching it and every subclass
     * @param status the HTTP status
     */
    public record Mapping(Class<? extends SwagebayError> kind, int status) {}
}
