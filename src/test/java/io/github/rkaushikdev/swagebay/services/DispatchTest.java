package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.personnel.Level;
import io.github.rkaushikdev.swagebay.personnel.LicenceRole;
import io.github.rkaushikdev.swagebay.personnel.Person;
import io.github.rkaushikdev.swagebay.safety.Defect;
import io.github.rkaushikdev.swagebay.safety.Inspection;
import io.github.rkaushikdev.swagebay.safety.InspectionItem;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.LeakTest;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import io.github.rkaushikdev.swagebay.testing.WorkFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DispatchTest {

    private static final LocalDateTime ASSAY = LocalDateTime.of(2025, 5, 1, 0, 0);
    private static final LocalDate DAY = LocalDate.of(2025, 6, 23);
    private static final Defect CABLE = new Defect(InspectionItem.DRIVE_CABLE, "three strands broken");

    private static SealedSource aSource(boolean wiped) {
        SourceCertificate certificate =
                new SourceCertificate("IR-5512", Isotopes.IR192, curie(90), ASSAY, "QSA", "A424-9", mm(3), mm(3));
        SealedSource source = new SealedSource(certificate, curie(15));
        if (wiped) {
            source.recordLeakTest(new LeakTest(ASSAY, BigDecimal.ZERO, "rp"));
        }
        return source;
    }

    private static SealedSource aSource() {
        return aSource(true);
    }

    private static Person aPerson(LocalDate vision) {
        return new Person(
                "0142", "crew", Level.LEVEL_II, LicenceRole.RADIOGRAPHER, LocalDate.of(2024, 1, 15), vision, vision);
    }

    private static Person aPerson() {
        return aPerson(LocalDate.of(2025, 3, 1));
    }

    private static void stocked(UnitOfWork work, boolean wiped, boolean inspected) {
        SourceService.registerSource(work, aSource(wiped));
        SourceService.registerDevice(work, "D-77", "880d");
        if (inspected) {
            WorkFixtures.passInspections(work, "D-77", DAY);
        }
    }

    private static void stocked(UnitOfWork work) {
        stocked(work, true, true);
    }

    @Test
    void aLoadWithEverythingInOrderIsCleared() {
        Clearance found = TestDatabase.fresh().transactionResult(work -> {
            stocked(work);
            return DispatchService.clearance(work, "D-77", "IR-5512", DAY, List.of(aPerson()));
        });
        assertThat(found.cleared()).isTrue();
        assertThat(found.describe()).contains("is clear for");
    }

    @Test
    void aSourceNobodyRegisteredStopsIt() {
        Clearance found = TestDatabase.fresh().transactionResult(work -> {
            SourceService.registerDevice(work, "D-77", "880d");
            WorkFixtures.passInspections(work, "D-77", DAY);
            return DispatchService.clearance(work, "D-77", "IR-9999", DAY, List.of(aPerson()));
        });
        assertThat(found.cleared()).isFalse();
        assertThat(found.sourceReasons().get(0)).contains("not on the inventory");
    }

    @Test
    void aDeviceNobodyRegisteredStopsIt() {
        Clearance found = TestDatabase.fresh().transactionResult(work -> {
            SourceService.registerSource(work, aSource());
            return DispatchService.clearance(work, "D-99", "IR-5512", DAY, List.of(aPerson()));
        });
        assertThat(found.deviceReasons().get(0)).contains("not on the inventory");
    }

    @Test
    void aSourceWithAStaleWipeTestStopsIt() {
        Clearance found = TestDatabase.fresh().transactionResult(work -> {
            stocked(work, false, true);
            return DispatchService.clearance(work, "D-77", "IR-5512", DAY, List.of(aPerson()));
        });
        assertThat(found.sourceReasons()).anyMatch(reason -> reason.contains("leak test"));
    }

    @Test
    void aDeviceNobodyInspectedStopsIt() {
        Clearance found = TestDatabase.fresh().transactionResult(work -> {
            stocked(work, true, false);
            return DispatchService.clearance(work, "D-77", "IR-5512", DAY, List.of(aPerson()));
        });
        assertThat(found.deviceReasons()).anyMatch(reason -> reason.contains("no quarterly"));
    }

    @Test
    void aBrokenDriveCableThatMorningStopsIt() {
        Clearance found = TestDatabase.fresh().transactionResult(work -> {
            stocked(work, true, false);
            work.maintenance().record(Inspection.quarterly("D-77", DAY.minusDays(30), "0303"));
            work.maintenance().record(Inspection.preUse("D-77", DAY, "0142", List.of(CABLE)));
            return DispatchService.clearance(work, "D-77", "IR-5512", DAY, List.of(aPerson()));
        });
        assertThat(found.deviceReasons()).anyMatch(reason -> reason.contains("drive cable"));
    }

    @Test
    void aCrewWhoseEyesHaveLapsedStopsIt() {
        Clearance found = TestDatabase.fresh().transactionResult(work -> {
            stocked(work);
            Person stale = aPerson(LocalDate.of(2023, 1, 1));
            return DispatchService.clearance(work, "D-77", "IR-5512", DAY, List.of(stale));
        });
        assertThat(found.crewReasons()).isNotEmpty();
        assertThat(found.crewReasons().get(0)).contains("0142");
    }

    @Test
    void aBookingWithNobodyOnItStopsIt() {
        Clearance found = TestDatabase.fresh().transactionResult(work -> {
            stocked(work);
            return DispatchService.clearance(work, "D-77", "IR-5512", DAY);
        });
        assertThat(found.crewReasons()).containsExactly("nobody is named on the booking");
    }

    @Test
    void aBadgeAloneIsEnoughToHaveSomebodyNamed() {
        Clearance found = TestDatabase.fresh().transactionResult(work -> {
            stocked(work);
            return DispatchService.clearance(work, "D-77", "IR-5512", DAY, List.of(), "0142");
        });
        assertThat(found.cleared()).isTrue();
    }

    @Test
    void everyReasonComesBackNotTheFirst() {
        Clearance found = TestDatabase.fresh().transactionResult(work -> {
            stocked(work, false, false);
            return DispatchService.clearance(work, "D-77", "IR-5512", DAY);
        });
        assertThat(found.reasons()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(found.reasons().get(0)).startsWith("source");
        assertThat(found.reasons().get(found.reasons().size() - 1)).isEqualTo("nobody is named on the booking");
    }

    @Test
    void theGateAsARefusalNamesEveryReason() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work, true, false);
            assertThatThrownBy(() -> DispatchService.requireClearance(work, "D-77", "IR-5512", DAY, List.of(aPerson())))
                    .isInstanceOfSatisfying(ConflictError.class, caught -> assertThat(
                                    (String) caught.context().get("reasons"))
                            .contains("no quarterly"));
        });
    }

    @Test
    void theGateLetsAGoodLoadThrough() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            assertThat(DispatchService.requireClearance(work, "D-77", "IR-5512", DAY, List.of(aPerson()))
                            .cleared())
                    .isTrue();
        });
    }

    @Test
    void theCamerasThisSourceCouldGoOutInCanBeListed() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            SourceService.registerDevice(work, "D-78", "880s");
            assertThat(DispatchService.clearedDevices(work, "IR-5512", DAY)).containsExactly("D-77");
        });
    }

    @Test
    void aShopWithNoDevicesAtAllIsANotFound() {
        TestDatabase.fresh().transaction(work -> {
            SourceService.registerSource(work, aSource());
            assertThatThrownBy(() -> DispatchService.clearedDevices(work, "IR-5512", DAY))
                    .isInstanceOf(NotFoundError.class);
        });
    }
}
