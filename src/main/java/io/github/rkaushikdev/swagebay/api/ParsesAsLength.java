package io.github.rkaushikdev.swagebay.api;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

/**
 * The text is a length the package's own parser reads, so {@code "0.375 in"} works in a request
 * exactly as it works in a procedure. A text that does not parse is refused with the parser's own
 * error, which the refusal carries so the response can quote it. An absent text is left to
 * {@code @NotNull}.
 */
@Documented
@Constraint(validatedBy = ParsesAsLength.Parser.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface ParsesAsLength {

    /** Only a fallback: the response quotes the parser's error instead. */
    String message() default "not a length";

    /** The groups the check belongs to. */
    Class<?>[] groups() default {};

    /** Payload, unused. */
    Class<? extends Payload>[] payload() default {};

    /** Runs the length parser over the text and keeps its error when it refuses. */
    final class Parser implements ConstraintValidator<ParsesAsLength, String> {

        @Override
        public boolean isValid(String text, ConstraintValidatorContext context) {
            if (text == null) {
                return true;
            }
            try {
                Length.parse(text);
                return true;
            } catch (ValidationError refused) {
                context.unwrap(HibernateConstraintValidatorContext.class).withDynamicPayload(refused);
                return false;
            }
        }
    }
}
