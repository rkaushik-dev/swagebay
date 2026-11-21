package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.testing.TestDatabase.assertTheSchemaRefuses;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.persistence.models.CalibrationRow;
import io.github.rkaushikdev.swagebay.persistence.models.ControlStripRow;
import io.github.rkaushikdev.swagebay.persistence.models.FiledRow;
import io.github.rkaushikdev.swagebay.persistence.models.IncidentRow;
import io.github.rkaushikdev.swagebay.persistence.models.LotRow;
import io.github.rkaushikdev.swagebay.persistence.models.QualificationRow;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RegisterTablesTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 20);
    private static final LocalDateTime MOMENT = LocalDateTime.of(2025, 3, 20, 23, 30);

    private static IncidentRow incident(String reference, LocalDateTime notified) {
        IncidentRow row = new IncidentRow();
        row.setReference(reference);
        row.setOccurrence("dose over a limit");
        row.setDiscoveredAt(MOMENT);
        row.setSite("Come By Chance");
        row.setReportedBy("0142");
        row.setAccount("the source hung up");
        row.setNotifiedAt(notified);
        return row;
    }

    private static IncidentRow incident() {
        return incident("INC-1", null);
    }

    private static CalibrationRow calibration(LocalDate due) {
        CalibrationRow row = new CalibrationRow();
        row.setSerial("LU-3-1188");
        row.setInstrumentKind("survey meter");
        row.setCalibratedOn(DAY);
        row.setDueOn(due);
        row.setLaboratory("NRC");
        row.setCertificateReference("C-1188");
        return row;
    }

    private static LotRow lot(String number, int sheets, LocalDate expires) {
        LotRow row = new LotRow();
        row.setLotNumber(number);
        row.setFilmKey("d7");
        row.setSize("4x10");
        row.setSheets(sheets);
        row.setReceivedOn(DAY);
        row.setExpiresOn(expires);
        return row;
    }

    private static LotRow lot(String number) {
        return lot(number, 100, LocalDate.of(2026, 3, 20));
    }

    private static QualificationRow qualification(Length coupon) {
        QualificationRow row = new QualificationRow();
        row.setWelder("w-19");
        row.setProcess("SMAW");
        row.setPosition("6G");
        row.setCoupon(coupon);
        row.setMaterialGroup("P1");
        row.setQualifiedOn(DAY);
        row.setLastUsedOn(DAY);
        return row;
    }

    private static QualificationRow qualification() {
        return qualification(mm(12));
    }

    private static FiledRow filed() {
        FiledRow row = new FiledRow();
        row.setRadiograph("25-0001-R0001");
        row.setJob("25-0001");
        row.setFiledOn(DAY);
        row.setRetention("five years");
        return row;
    }

    private static ControlStripRow strip(BigDecimal speed) {
        ControlStripRow row = new ControlStripRow();
        row.setProcessor("P-1");
        row.setReadOn(DAY);
        row.setSpeed(speed);
        row.setContrast(BigDecimal.valueOf(3));
        row.setFog(new BigDecimal("0.18"));
        row.setOperator("dr");
        return row;
    }

    @Test
    void everyRegisterIsOnTheMetadata() {
        assertThat(Schema.tableNames())
                .contains("incident", "calibration", "filed", "lot", "qualification", "control_strip");
    }

    @Test
    void anIncidentRoundTrips() {
        try (TestSession db = TestSession.open()) {
            db.add(incident());
            db.commit();
            IncidentRow found = db.one(IncidentRow.class);
            assertThat(found.getReference()).isEqualTo("INC-1");
            assertThat(found.getNotifiedAt()).isEmpty();
        }
    }

    @Test
    void anIncidentCannotBeReportedBeforeItWasFound() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(incident("INC-1", MOMENT.minusHours(1)));
                db.commit();
            });
        }
    }

    @Test
    void anIncidentReferenceIsUsedOnce() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(incident());
                db.add(incident());
                db.commit();
            });
        }
    }

    @Test
    void aCertificateExpiresAfterItWasIssued() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(calibration(DAY));
                db.commit();
            });
        }
    }

    @Test
    void theSameInstrumentCanHoldSeveralCertificates() {
        try (TestSession db = TestSession.open()) {
            db.add(calibration(LocalDate.of(2026, 3, 20)));
            db.add(calibration(LocalDate.of(2027, 3, 20)));
            db.commit();
            assertThat(db.all(CalibrationRow.class)).hasSize(2);
        }
    }

    @Test
    void aFiledRadiographMayHaveNoLocationYet() {
        try (TestSession db = TestSession.open()) {
            db.add(filed());
            db.commit();
            FiledRow found = db.one(FiledRow.class);
            assertThat(found.getSite()).isEmpty();
            assertThat(found.getBox()).isEmpty();
        }
    }

    @Test
    void aRadiographIsFiledOnce() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(filed());
                db.add(filed());
                db.commit();
            });
        }
    }

    @Test
    void aLotCannotHoldANegativeNumberOfSheets() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(lot("L-1", -1, LocalDate.of(2026, 3, 20)));
                db.commit();
            });
        }
    }

    @Test
    void aLotExpiresAfterItArrived() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(lot("L-1", 100, DAY));
                db.commit();
            });
        }
    }

    @Test
    void aLotNumberIsUsedOnce() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(lot("L-1"));
                db.add(lot("L-1"));
                db.commit();
            });
        }
    }

    @Test
    void aQualificationNeedsACouponWithAThickness() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(qualification(Length.ZERO));
                db.commit();
            });
        }
    }

    @Test
    void aWelderQualifiesOnOneProcessOnceADay() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(qualification());
                db.add(qualification());
                db.commit();
            });
        }
    }

    @Test
    void aQualificationKeepsItsCouponAsALength() {
        try (TestSession db = TestSession.open()) {
            db.add(qualification());
            db.commit();
            assertThat(db.one(QualificationRow.class).getCoupon()).isEqualTo(mm(12));
        }
    }

    @Test
    void aControlStripNeedsBelievableReadings() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(strip(BigDecimal.ZERO));
                db.commit();
            });
        }
    }

    @Test
    void oneStripAProcessorADay() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(strip(BigDecimal.valueOf(2)));
                db.add(strip(BigDecimal.valueOf(2)));
                db.commit();
            });
        }
    }
}
