package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One walk round with a meter, and the readings taken on it.
 *
 * <p>Survey points hang off a survey rather than being free standing readings. A reading with no
 * survey has no instrument, no signature and no stage, which makes it a number somebody wrote on
 * their hand. Both survey lookups key on the radiograph and the day log keys on the time, so both
 * are indexed.
 */
@Entity
@Table(
        name = "survey",
        indexes = {
            @Index(name = "ix_survey_radiograph_number", columnList = "radiograph_number"),
            @Index(name = "ix_survey_performed_at", columnList = "performed_at")
        })
@Check(name = "ck_survey_stage_known", constraints = "stage IN ('before', 'after')")
public class SurveyRow extends Keyed {

    @Column(name = "radiograph_number", length = 40)
    private String radiographNumber;

    @Column(name = "stage", length = 10, nullable = false)
    private String stage;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @Column(name = "performed_by", length = 40, nullable = false)
    private String performedBy;

    @Column(name = "instrument", length = 80, nullable = false)
    private String instrument;

    @Column(name = "calibration_due", nullable = false)
    private LocalDateTime calibrationDue;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private final List<SurveyPointRow> points = new ArrayList<>();

    public Optional<String> getRadiographNumber() {
        return Optional.ofNullable(radiographNumber);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setRadiographNumber(String radiographNumber) {
        this.radiographNumber = radiographNumber;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public LocalDateTime getPerformedAt() {
        return performedAt;
    }

    public void setPerformedAt(LocalDateTime performedAt) {
        this.performedAt = performedAt;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    public LocalDateTime getCalibrationDue() {
        return calibrationDue;
    }

    public void setCalibrationDue(LocalDateTime calibrationDue) {
        this.calibrationDue = calibrationDue;
    }

    public List<SurveyPointRow> getPoints() {
        return Collections.unmodifiableList(points);
    }

    /** Hang {@code surveyPoint} off this row; it is written with this one. */
    public void addPoint(SurveyPointRow surveyPoint) {
        surveyPoint.setSurvey(this);
        points.add(surveyPoint);
    }
}
