package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.safety.Inspection.preUse;
import static io.github.rkaushikdev.swagebay.safety.Inspection.quarterly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.models.InspectionRow;
import io.github.rkaushikdev.swagebay.safety.Defect;
import io.github.rkaushikdev.swagebay.safety.Inspection;
import io.github.rkaushikdev.swagebay.safety.InspectionItem;
import io.github.rkaushikdev.swagebay.safety.MaintenanceRegister;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaintenanceRepositoryTest {

    private static final LocalDate STRIPPED = LocalDate.of(2025, 4, 14);
    private static final LocalDate MORNING = LocalDate.of(2025, 6, 19);
    private static final Defect CABLE =
            new Defect(InspectionItem.DRIVE_CABLE, "three strands broken near the connector");
    private static final Defect WHEEL = new Defect(InspectionItem.WHEELS, "one castor seized");

    @Test
    void anInspectionComesBackTheWayItWentIn() {
        List<Inspection> found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(quarterly("D-77", STRIPPED, "0303", List.of(WHEEL)));
            found = work.maintenance().forDevice("D-77");
        }
        assertThat(found).hasSize(1);
        assertThat(found.get(0).isQuarterly()).isTrue();
        assertThat(found.get(0).performedBy()).isEqualTo("0303");
        assertThat(found.get(0).defects()).containsExactly(WHEEL);
    }

    @Test
    void theItemsCheckedSurviveTheRoundTrip() {
        Inspection found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(preUse("D-77", MORNING, "0142"));
            found = work.maintenance().forDevice("D-77").get(0);
        }
        assertThat(found.itemsChecked()).contains(InspectionItem.DRIVE_CABLE).doesNotContain(InspectionItem.WHEELS);
    }

    @Test
    void aPreUseCheckIsNotStoredAsAQuarterly() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(preUse("D-77", MORNING, "0142"));
            assertThat(work.maintenance().forDevice("D-77").get(0).isQuarterly())
                    .isFalse();
        }
    }

    @Test
    void twoDefectsOnOneInspectionBothComeBack() {
        Inspection found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(quarterly("D-77", STRIPPED, "0303", List.of(CABLE, WHEEL)));
            found = work.maintenance().forDevice("D-77").get(0);
        }
        assertThat(found.defects()).hasSize(2);
        assertThat(found.blockingDefects()).containsExactly(CABLE);
    }

    @Test
    void anotherDevicesInspectionsAreNotInThisOnesHistory() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(quarterly("D-77", STRIPPED, "0303"));
            work.maintenance().record(quarterly("D-78", STRIPPED, "0303"));
            assertThat(work.maintenance().forDevice("D-77")).hasSize(1);
        }
    }

    @Test
    void theHistoryReadsInDateOrderWhateverOrderItWasWritten() {
        List<LocalDate> days;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(preUse("D-77", MORNING, "0142"));
            work.maintenance().record(quarterly("D-77", STRIPPED, "0303"));
            days = work.maintenance().forDevice("D-77").stream()
                    .map(Inspection::performedOn)
                    .toList();
        }
        assertThat(days).containsExactly(STRIPPED, MORNING);
    }

    @Test
    void aRegisterAnswersTheMorningQuestion() {
        MaintenanceRegister register;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(quarterly("D-77", STRIPPED, "0303"));
            work.maintenance().record(preUse("D-77", MORNING, "0142"));
            register = work.maintenance().registerFor(List.of("D-77"));
        }
        assertThat(register.mayGoOut("D-77", MORNING)).isTrue();
        assertThat(register.mayGoOut("D-77", MORNING.plusDays(1))).isFalse();
    }

    @Test
    void aRegisterForNothingIsEmptyRatherThanEverything() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(quarterly("D-77", STRIPPED, "0303"));
            assertThat(work.maintenance().registerFor(List.of()).inspections()).isEmpty();
        }
    }

    @Test
    void aRegisterOnlyLoadsTheDevicesItWasAskedFor() {
        MaintenanceRegister register;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(quarterly("D-77", STRIPPED, "0303"));
            work.maintenance().record(quarterly("D-78", STRIPPED, "0303"));
            register = work.maintenance().registerFor(List.of("D-77"));
        }
        assertThat(register.inspections()).hasSize(1);
    }

    @Test
    void everyDeviceThatHasHadACableFaultCanBeListed() {
        List<MaintenanceRepository.Fault> found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(quarterly("D-77", STRIPPED, "0303", List.of(CABLE)));
            work.maintenance().record(quarterly("D-78", STRIPPED, "0303", List.of(WHEEL)));
            found = work.maintenance().defectsOn(InspectionItem.DRIVE_CABLE);
        }
        assertThat(found)
                .containsExactly(new MaintenanceRepository.Fault("D-77", "three strands broken near the connector"));
    }

    @Test
    void anItemNothingHasEverFailedListsNothing() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(quarterly("D-77", STRIPPED, "0303"));
            assertThat(work.maintenance().defectsOn(InspectionItem.GUIDE_TUBE)).isEmpty();
        }
    }

    @Test
    void anInspectionSurvivesTheTransactionThatWroteIt() {
        Database made = TestDatabase.fresh();
        made.transaction(work -> work.maintenance().record(quarterly("D-77", STRIPPED, "0303", List.of(CABLE))));
        try (UnitOfWork work = made.unitOfWork()) {
            assertThat(work.maintenance().forDevice("D-77").get(0).defects()).containsExactly(CABLE);
        }
    }

    @Test
    void aRowNamingAnItemNothingInspectsIsRefusedOnTheWayOut() {
        InspectionRow row = new InspectionRow();
        row.setDeviceSerial("D-77");
        row.setPerformedOn(MORNING);
        row.setPerformedBy("0142");
        row.setQuarterly(false);
        row.setItemsChecked("FLUX_CAPACITOR");
        row.setNotes("");
        assertThatThrownBy(() -> MaintenanceRepository.rowToInspection(row)).isInstanceOf(ValidationError.class);
    }
}
