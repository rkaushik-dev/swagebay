package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.quantities.Units.seconds;
import static io.github.rkaushikdev.swagebay.quantities.Units.usv;
import static io.github.rkaushikdev.swagebay.quantities.Units.usvPerHour;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import jakarta.persistence.AttributeConverter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What the value object columns do when something else is handed to them.
 *
 * <p>Each of these columns exists to stop a plain integer being written where a quantity belongs.
 * The compiler refuses a bare number at every typed call, so the refusal that matters at run time is
 * the one the persistence provider meets: it holds each converter through its erased type, and a
 * value of the wrong type is turned away by the converter's own cast, naming the type it wanted.
 *
 * <p>The identity guards are here for the same reason. The parsers are covered from every direction;
 * the constructors are what an import script calls, and an import script is where an out of range
 * number actually comes from.
 */
class ColumnGuardsTest {

    /** One column, a value it holds, and the name of that value's type. */
    record Case<T>(AttributeConverter<T, Long> column, T value, String name) {

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Case<?>> columns() {
        return Stream.of(
                new Case<>(new LengthColumn(), mm(10), "Length"),
                new Case<>(new ActivityColumn(), curie(90), "Activity"),
                new Case<>(new DurationColumn(), seconds(30), "Duration"),
                new Case<>(new DoseColumn(), usv(1000), "Dose"),
                new Case<>(new DoseRateColumn(), usvPerHour(20), "DoseRate"));
    }

    /**
     * A converter as the persistence provider holds it: through its erased type, so nothing but the
     * converter's own cast stands between a stray value and the column.
     */
    @SuppressWarnings("unchecked")
    private static AttributeConverter<Object, Long> erased(AttributeConverter<?, Long> column) {
        return (AttributeConverter<Object, Long>) column;
    }

    /** The type the column's write side is declared to take. */
    private static Class<?> writes(AttributeConverter<?, Long> column) {
        return Arrays.stream(column.getClass().getDeclaredMethods())
                .filter(method -> method.getName().equals("convertToDatabaseColumn") && !method.isBridge())
                .map(Method::getParameterTypes)
                .map(types -> types[0])
                .findFirst()
                .orElseThrow();
    }

    private static <T> void assertRoundTrip(Case<T> sample) {
        Long stored = sample.column().convertToDatabaseColumn(sample.value());
        assertThat(stored).isInstanceOf(Long.class);
        assertThat(sample.column().convertToEntityAttribute(stored)).isEqualTo(sample.value());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("columns")
    void aQuantityGoesInAsAWholeNumber(Case<?> sample) {
        assertRoundTrip(sample);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("columns")
    void nothingGoesInAsNothing(Case<?> sample) {
        assertThat(sample.column().convertToDatabaseColumn(null)).isNull();
        assertThat(sample.column().convertToEntityAttribute(null)).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("columns")
    void aBareIntegerIsRefusedByName(Case<?> sample) {
        assertThat(writes(sample.column()).getSimpleName()).isEqualTo(sample.name());
        assertThatThrownBy(() -> erased(sample.column()).convertToDatabaseColumn(1000L))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("quantities." + sample.name() + " ");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("columns")
    void theWrongQuantityIsRefusedToo(Case<?> sample) {
        Object other = sample.name().equals("Activity") ? mm(1) : curie(1);
        assertThatThrownBy(() -> erased(sample.column()).convertToDatabaseColumn(other))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void aJobYearOutsideTwoDigitsIsRefused() {
        assertThatThrownBy(() -> new JobNumber(100, 1))
                .isInstanceOfSatisfying(ValidationError.class, refused -> assertThat(refused.context())
                        .containsEntry("year", 100));
        assertThatThrownBy(() -> new JobNumber(-1, 1)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aJobSerialOutsideFourDigitsIsRefused() {
        assertThatThrownBy(() -> new JobNumber(24, 0))
                .isInstanceOfSatisfying(ValidationError.class, refused -> assertThat(refused.context())
                        .containsEntry("serial", 0));
        assertThatThrownBy(() -> new JobNumber(24, 10000)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aWeldPrefixNothingKnowsAboutIsRefused() {
        assertThatThrownBy(() -> new WeldId("ZZ", 104))
                .isInstanceOfSatisfying(ValidationError.class, refused -> assertThat(refused.context())
                        .containsEntry("prefix", "ZZ"));
    }

    @Test
    void aWeldNumberOutsideFiveDigitsIsRefused() {
        assertThatThrownBy(() -> new WeldId("GW", 0)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new WeldId("GW", 100000)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aRepairRoundOutsideTwoDigitsIsRefused() {
        assertThatThrownBy(() -> new WeldId("GW", 104, 100))
                .isInstanceOfSatisfying(ValidationError.class, refused -> assertThat(refused.context())
                        .containsEntry("repair", 100));
        assertThatThrownBy(() -> new WeldId("GW", 104, -1)).isInstanceOf(ValidationError.class);
    }
}
