package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.persistence.models.LeakTestRow;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.LeakTest;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import io.github.rkaushikdev.swagebay.sources.SourceState;
import java.util.Arrays;

/**
 * Turning sealed sources into rows and back again.
 *
 * <p>Both directions are written by hand and neither is generated. That is a deliberate cost. A
 * generated mapper follows the shape of the class, so renaming a field silently renames a column,
 * and a column rename in a schema with four years of data in it is not a refactor, it is a
 * migration with a data step. Writing it out means the compiler and the tests notice.
 *
 * <p>The round trip is the thing worth testing rather than either direction on its own. A pair of
 * mappers that both use the same wrong column produce a perfect round trip through the objects and
 * lose the data on the way to the database, so the tests go through a real database rather than
 * calling the two directions back to back.
 *
 * <p>The state is stored as its value rather than its name, because the value is the phrase a human
 * reads in the utilisation log and the name is a detail of the code that will get renamed one day.
 */
public final class SourceMapper {

    private SourceMapper() {}

    /** A sealed source as a row, its leak tests hanging off it. */
    public static SourceRow sourceToRow(SealedSource source) {
        SourceCertificate certificate = source.certificate();
        SourceRow row = new SourceRow();
        row.setSerial(certificate.serial());
        row.setIsotopeKey(certificate.isotope().key());
        row.setAssayActivity(certificate.assayActivity());
        row.setAssayAt(certificate.assayAt());
        row.setManufacturer(certificate.manufacturer());
        row.setModel(certificate.model());
        row.setActiveDiameter(certificate.activeDiameter());
        row.setActiveLength(certificate.activeLength());
        row.setState(source.state().value());
        row.setWorkingFloor(source.workingFloor());
        for (LeakTest test : source.leakTests()) {
            row.addLeakTest(leakTestToRow(test));
        }
        return row;
    }

    /** A leak test as a row, not yet hung off a source. */
    public static LeakTestRow leakTestToRow(LeakTest test) {
        LeakTestRow row = new LeakTestRow();
        row.setPerformedAt(test.performedAt());
        row.setBecquerelsRemoved(test.becquerelsRemoved());
        row.setTechnician(test.technician());
        return row;
    }

    /**
     * A row and its leak tests, back into the source the planner uses.
     *
     * @throws ConfigurationError for a stored state that is not a state
     */
    public static SealedSource rowToSource(SourceRow row) {
        SourceCertificate certificate = new SourceCertificate(
                row.getSerial(),
                Isotopes.isotope(row.getIsotopeKey()),
                row.getAssayActivity(),
                row.getAssayAt(),
                row.getManufacturer(),
                row.getModel(),
                row.getActiveDiameter(),
                row.getActiveLength());
        return new SealedSource(
                certificate,
                stateFromValue(row.getState()),
                row.getWorkingFloor(),
                row.getLeakTests().stream().map(SourceMapper::rowToLeakTest).toList());
    }

    /** A leak test row back into the leak test. */
    public static LeakTest rowToLeakTest(LeakTestRow row) {
        return new LeakTest(row.getPerformedAt(), row.getBecquerelsRemoved(), row.getTechnician());
    }

    /**
     * Read the stored phrase back into a state, refusing anything unknown.
     *
     * @throws ConfigurationError for a phrase that names no state
     */
    public static SourceState stateFromValue(String value) {
        return Arrays.stream(SourceState.values())
                .filter(state -> state.value().equals(value))
                .findFirst()
                .orElseThrow(
                        () -> new ConfigurationError("no source state with that value", Context.of("value", value)));
    }
}
