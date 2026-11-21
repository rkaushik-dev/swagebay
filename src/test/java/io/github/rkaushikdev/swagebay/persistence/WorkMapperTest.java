package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.materials.Materials.STEEL;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.evaluation.Kind;
import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.geometry.JointForm;
import io.github.rkaushikdev.swagebay.geometry.PipeSchedule;
import io.github.rkaushikdev.swagebay.identity.ClockPosition;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.Admissibility;
import io.github.rkaushikdev.swagebay.persistence.models.IndicationRow;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.records.Radiograph;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkMapperTest {

    private static final LocalDateTime SHOT = LocalDateTime.of(2024, 8, 16, 23, 15);
    private static final LocalDateTime READ = LocalDateTime.of(2024, 8, 17, 8, 0);
    private static final WeldId WELD = WeldId.parse("GW-104R1");
    private static final Admissibility GOOD = new Admissibility(true, List.of(), List.of("indicator", "density"));
    private static final Admissibility BAD =
            new Admissibility(false, List.of("the 2T hole is not visible", "density is thin"), List.of("indicator"));

    private static WeldRow blankWeldRow() {
        WeldRow row = new WeldRow();
        row.setPrefix("GW");
        row.setNumber(1);
        row.setMaterialKey("carbon_steel");
        row.setWall(mm(1));
        row.setReinforcement(Length.ZERO);
        row.setWeldLength(mm(500));
        row.setWelder("w-19");
        row.setWeldedOn(LocalDate.of(2024, 8, 1));
        return row;
    }

    private static Joint sixInch() {
        return PipeSchedule.girthWeld("6", "80", STEEL);
    }

    /** Write the radiograph on a weld of that joint to a database of its own, and read it back. */
    private static Radiograph persisted(Radiograph radiograph, Joint joint) {
        try (TestSession db = TestSession.open()) {
            JobRow job = new JobRow();
            job.setYear(24);
            job.setSerial(417);
            job.setClient("c");
            job.setSite("s");
            job.setAcceptanceCode("API 1104");
            job.setProcedureNumber("RT-001");
            job.setOpenedOn(LocalDate.of(2024, 8, 1));
            WeldRow weld = WorkMapper.jointToRow(joint, WELD, blankWeldRow());
            weld.setWeldLength(mm(529));
            weld.addRadiograph(WorkMapper.radiographToRow(radiograph, "film"));
            job.addWeld(weld);
            db.add(job);
            db.commit();
            return WorkMapper.rowToRadiograph(db.one(RadiographRow.class));
        }
    }

    private static Radiograph aRadiograph(Admissibility admissibility, List<Indication> indications, boolean read) {
        return new Radiograph(
                "R-001",
                WELD,
                2,
                SHOT,
                "RT-001 rev 1",
                admissibility,
                indications,
                read ? "0142" : "",
                read ? Optional.of(READ) : Optional.empty());
    }

    private static Radiograph aRadiograph() {
        return aRadiograph(GOOD, List.of(), true);
    }

    private static Radiograph withIndications(Indication... indications) {
        return aRadiograph(GOOD, List.of(indications), true);
    }

    @Test
    void aPipeJointSurvivesTheRoundTrip() {
        Joint joint = PipeSchedule.girthWeld("6", "80", STEEL, mm(2));
        WeldRow row = WorkMapper.jointToRow(joint, WELD, blankWeldRow());
        Joint recovered = WorkMapper.rowToJoint(row);
        assertThat(recovered.od()).isEqualTo(joint.od());
        assertThat(recovered.wall()).isEqualTo(joint.wall());
        assertThat(recovered.reinforcement()).isEqualTo(mm(2));
        assertThat(recovered.form()).isSameAs(JointForm.PIPE_GIRTH);
    }

    @Test
    void aPlateJointComesBackAsPlateBecauseTheDiameterIsNull() {
        Joint plate = new Joint(JointForm.PLATE_BUTT, mm(12), STEEL);
        WeldRow row = WorkMapper.jointToRow(plate, WELD, blankWeldRow());
        assertThat(row.getOutsideDiameter()).isEmpty();
        assertThat(WorkMapper.rowToJoint(row).form()).isSameAs(JointForm.PLATE_BUTT);
    }

    @Test
    void theWeldIdentifierKeepsItsRepairRound() {
        WeldRow row = WorkMapper.jointToRow(sixInch(), WELD, blankWeldRow());
        assertThat(row.getRepairRound()).isEqualTo(1);
        assertThat(WorkMapper.rowToWeldId(row)).isEqualTo(WELD);
        assertThat(WorkMapper.rowToWeldId(row)).hasToString("GW-104R1");
    }

    @Test
    void aRadiographSurvivesTheDatabase() {
        Radiograph recovered = persisted(aRadiograph(), sixInch());
        assertThat(recovered.number()).isEqualTo("R-001");
        assertThat(recovered.exposureIndex()).isEqualTo(2);
        assertThat(recovered.weld()).isEqualTo(WELD);
        assertThat(recovered.readBy()).isEqualTo("0142");
    }

    @Test
    void anUnreadRadiographComesBackUnread() {
        Radiograph recovered = persisted(aRadiograph(GOOD, List.of(), false), sixInch());
        assertThat(recovered.hasBeenRead()).isFalse();
        assertThat(recovered.readBy()).isEmpty();
        assertThat(recovered.readAt()).isEmpty();
    }

    @Test
    void anAdmissibleRadiographStoresNoReason() {
        RadiographRow row = WorkMapper.radiographToRow(aRadiograph(), "film");
        assertThat(row.isAdmissible()).isTrue();
        assertThat(row.getInadmissibleReason()).isEmpty();
    }

    @Test
    void theReasonsSurviveAsWordsAndComeBackSplit() {
        Radiograph recovered = persisted(aRadiograph(BAD, List.of(), false), sixInch());
        assertThat(recovered.isAdmissible()).isFalse();
        assertThat(recovered.admissibility().reasons())
                .containsExactly("the 2T hole is not visible", "density is thin");
    }

    @Test
    void indicationsSurviveWithTheirKindAndPosition() {
        Radiograph recovered = persisted(
                withIndications(
                        new Indication(Kind.ESI, mm(100), mm(20), mm(1)).withClock(ClockPosition.parse("3:00")),
                        new Indication(Kind.P, mm(200), Length.ZERO, mm(2))),
                sixInch());
        assertThat(recovered.indications()).extracting(Indication::kind).containsExactly(Kind.ESI, Kind.P);
        assertThat(recovered.indications().get(0).clock()).contains(ClockPosition.parse("3:00"));
        assertThat(recovered.indications().get(1).width()).isEqualTo(mm(2));
    }

    @Test
    void anUndercutDepthSurvivesAsADecimal() {
        Radiograph recovered = persisted(
                withIndications(new Indication(Kind.U, mm(100), mm(20)).withDepthFraction(new BigDecimal("0.125"))),
                sixInch());
        assertThat(recovered.indications().get(0).depthFraction())
                .hasValueSatisfying(depth -> assertThat(depth).isEqualByComparingTo("0.125"));
    }

    @Test
    void anIndicationWithNoClockPositionComesBackWithoutOne() {
        Radiograph recovered = persisted(withIndications(new Indication(Kind.ESI, mm(100), mm(20), mm(1))), sixInch());
        assertThat(recovered.indications().get(0).clock()).isEmpty();
    }

    @Test
    void theIndicationRemembersWhichFilmItWasSeenOn() {
        Radiograph recovered = persisted(withIndications(new Indication(Kind.ESI, mm(100), mm(20), mm(1))), sixInch());
        assertThat(recovered.indications().get(0).radiograph()).isEqualTo("R-001");
    }

    @Test
    void aStoredCodeThatIsNotAKindIsRefused() {
        IndicationRow row = new IndicationRow();
        row.setKind("ZZ");
        row.setStartsAt(mm(1));
        row.setLength(mm(1));
        row.setWidth(mm(1));
        assertThatThrownBy(() -> WorkMapper.rowToIndication(row)).isInstanceOf(ConfigurationError.class);
    }
}
