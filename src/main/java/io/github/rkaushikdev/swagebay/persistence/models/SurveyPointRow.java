package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.DoseRateColumn;
import io.github.rkaushikdev.swagebay.persistence.Keyed;
import io.github.rkaushikdev.swagebay.persistence.LengthColumn;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Length;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One reading on one survey.
 *
 * <p>The surface reading flag is a column rather than being inferred from the distance, because
 * what makes a reading a surface reading is that the surveyor put the probe on the device, and no
 * distance in a database can establish that.
 */
@Entity
@Table(name = "survey_point")
@Check(name = "ck_survey_point_point_has_a_distance", constraints = "distance > 0")
public class SurveyPointRow extends Keyed {

    @Column(name = "label", length = 80, nullable = false)
    private String label;

    @Convert(converter = LengthColumn.class)
    @Column(name = "distance", nullable = false)
    private Length distance;

    @Column(name = "bearing_degrees", nullable = false)
    private int bearingDegrees;

    @Convert(converter = DoseRateColumn.class)
    @Column(name = "reading", nullable = false)
    private DoseRate reading;

    @Column(name = "at_device_surface", nullable = false)
    private boolean atDeviceSurface;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "survey_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_survey_point_survey_id_survey"))
    private SurveyRow survey;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Length getDistance() {
        return distance;
    }

    public void setDistance(Length distance) {
        this.distance = distance;
    }

    public int getBearingDegrees() {
        return bearingDegrees;
    }

    public void setBearingDegrees(int bearingDegrees) {
        this.bearingDegrees = bearingDegrees;
    }

    public DoseRate getReading() {
        return reading;
    }

    public void setReading(DoseRate reading) {
        this.reading = reading;
    }

    public boolean isAtDeviceSurface() {
        return atDeviceSurface;
    }

    public void setAtDeviceSurface(boolean atDeviceSurface) {
        this.atDeviceSurface = atDeviceSurface;
    }

    /** The row this one hangs off, once it has been attached to one. */
    public Optional<SurveyRow> getSurvey() {
        return Optional.ofNullable(survey);
    }

    void setSurvey(SurveyRow survey) {
        this.survey = survey;
    }
}
