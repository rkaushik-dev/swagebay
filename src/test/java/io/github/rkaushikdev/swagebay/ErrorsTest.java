package io.github.rkaushikdev.swagebay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorsTest {

    private static final List<SwagebayError> ALL_ERRORS = List.of(
            new SwagebayError("x"),
            new ConfigurationError("x"),
            new ValidationError("x"),
            new UnitError("x"),
            new TechniqueError("x"),
            new QualificationError("x"),
            new SafetyError("x"),
            new NotFoundError("x", "y"),
            new ConflictError("x"));

    @Test
    void everyErrorDescendsFromTheBase() {
        for (SwagebayError error : ALL_ERRORS) {
            assertThat(error).isInstanceOf(SwagebayError.class).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void codesAreUniqueAndNamespaced() {
        List<String> codes = ALL_ERRORS.stream().map(SwagebayError::code).toList();
        assertThat(codes).doesNotHaveDuplicates();
        assertThat(codes).allSatisfy(code -> assertThat(code).startsWith("qi."));
    }

    @Test
    void aUnitErrorIsAValidationError() {
        assertThat(ValidationError.class).isAssignableFrom(UnitError.class);
        assertThatThrownBy(() -> {
                    throw new UnitError("cannot add curies to millimetres");
                })
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void messageAloneRendersPlainly() {
        SwagebayError error = new TechniqueError("source to film distance below the minimum");
        assertThat(error.getMessage()).isEqualTo("source to film distance below the minimum");
    }

    @Test
    void contextIsAppendedInAStableOrder() {
        SwagebayError error = new TechniqueError("too close", Context.of("sfd_mm", 400, "minimum_mm", 610));
        assertThat(error.getMessage()).isEqualTo("too close (minimum_mm=610, sfd_mm=400)");
    }

    @Test
    void asMapCarriesTheCodeAndACopyOfTheContext() {
        SwagebayError error = new QualificationError("density below range", Context.of("density", 1.4, "floor", 1.8));
        Map<String, Object> body = error.asMap();
        assertThat(body)
                .isEqualTo(Map.of(
                        "code", "qi.qualification",
                        "message", "density below range",
                        "context", Map.of("density", 1.4, "floor", 1.8)));
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.get("context");
        context.put("density", 9.9);
        assertThat(error.context().get("density")).isEqualTo(1.4);
    }

    @Test
    void notFoundBuildsItsOwnMessage() {
        NotFoundError error = new NotFoundError("source", "IR-2211");
        assertThat(error.kind()).isEqualTo("source");
        assertThat(error.identity()).isEqualTo("IR-2211");
        assertThat(error.getMessage()).contains("no source with identity 'IR-2211'");
    }

    @Test
    void safetyAndConflictAreDistinctBranches() {
        assertThat(ConflictError.class.isAssignableFrom(SafetyError.class)).isFalse();
        assertThat(SafetyError.class.isAssignableFrom(ConflictError.class)).isFalse();
    }

    @Test
    void raisingTheBaseStillCarriesContext() {
        assertThatThrownBy(() -> {
                    throw new SwagebayError("something", Context.of("weld", "GW-104"));
                })
                .isInstanceOfSatisfying(SwagebayError.class, caught -> assertThat(caught.context())
                        .isEqualTo(Map.of("weld", "GW-104")));
    }

    /**
     * Every refusal of a bad value is one catch: a {@link ValidationError} catches its unit leaf as
     * well, and both are unchecked like any other bad argument.
     */
    @Test
    void aValidationErrorIsTheOneCatchForABadValue() {
        assertThat(RuntimeException.class).isAssignableFrom(ValidationError.class);
        assertThat(ValidationError.class).isAssignableFrom(UnitError.class);
        assertThatThrownBy(() -> {
                    throw new ValidationError("that is not a length");
                })
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theOtherBranchesAreNotValidationErrors() {
        for (Class<?> kind :
                List.of(TechniqueError.class, QualificationError.class, SafetyError.class, ConflictError.class)) {
            assertThat(ValidationError.class.isAssignableFrom(kind)).isFalse();
        }
    }
}
