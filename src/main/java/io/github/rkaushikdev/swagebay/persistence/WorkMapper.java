package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.evaluation.Kind;
import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.geometry.JointForm;
import io.github.rkaushikdev.swagebay.identity.ClockPosition;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.Admissibility;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.persistence.models.IndicationRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import io.github.rkaushikdev.swagebay.records.Radiograph;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Mapping welds, radiographs and indications.
 *
 * <p>A joint is stored across the weld row rather than in a table of its own. That is a
 * denormalisation and it is the right one, because a joint has no identity without the weld it
 * belongs to, and a joint table would be a second row that can only ever be joined one to one. The
 * absence of a diameter is stored as an empty column, which is what tells plate apart from pipe on
 * the way back out.
 *
 * <p>An admissibility verdict is stored as a flag and a reason string rather than as a structure.
 * The structure is a thing this software made up: the gates that ran, the evidence they ran against.
 * What the client and the auditor need is whether the film was admissible and if not why, in words.
 * Storing the structure would make the schema follow the software, and the schema outlives the
 * software.
 */
public final class WorkMapper {

    /** What separates the reasons a film is not admissible, in the one column that holds them. */
    public static final String REASON_SEPARATOR = "; ";

    private WorkMapper() {}

    /** Write a joint, and the identity of its weld, onto a weld row; the same row comes back. */
    public static WeldRow jointToRow(Joint joint, WeldId weld, WeldRow row) {
        row.setPrefix(weld.prefix());
        row.setNumber(weld.number());
        row.setRepairRound(weld.repair());
        row.setMaterialKey(joint.material().key());
        row.setOutsideDiameter(joint.outsideDiameter().orElse(null));
        row.setWall(joint.wall());
        row.setReinforcement(joint.reinforcement());
        return row;
    }

    /** A weld row back into the joint the planner needs: plate without a diameter, pipe with one. */
    public static Joint rowToJoint(WeldRow row) {
        JointForm form = row.getOutsideDiameter().isEmpty() ? JointForm.PLATE_BUTT : JointForm.PIPE_GIRTH;
        return Joint.builder(form, row.getWall(), Materials.material(row.getMaterialKey()))
                .outsideDiameter(row.getOutsideDiameter())
                .reinforcement(row.getReinforcement())
                .build();
    }

    /** The weld identifier a row carries, repair round included. */
    public static WeldId rowToWeldId(WeldRow row) {
        return new WeldId(row.getPrefix(), row.getNumber(), row.getRepairRound());
    }

    /** A radiograph as a row shot on {@code medium}, its indications hanging off it. */
    public static RadiographRow radiographToRow(Radiograph radiograph, String medium) {
        RadiographRow row = new RadiographRow();
        row.setNumber(radiograph.number());
        row.setExposureIndex(radiograph.exposureIndex());
        row.setExposedAt(radiograph.exposedAt());
        row.setTechniqueReference(radiograph.techniqueReference());
        row.setMedium(medium);
        row.setAdmissible(radiograph.admissibility().admissible());
        row.setInadmissibleReason(reasonText(radiograph.admissibility()).orElse(null));
        row.setReadBy(radiograph.readBy().isEmpty() ? null : radiograph.readBy());
        row.setReadAt(radiograph.readAt().orElse(null));
        for (Indication indication : radiograph.indications()) {
            row.addIndication(indicationToRow(indication));
        }
        return row;
    }

    /** A radiograph row, attached to its weld, back into the record with its indications. */
    public static Radiograph rowToRadiograph(RadiographRow row) {
        return new Radiograph(
                row.getNumber(),
                rowToWeldId(row.getWeld().orElseThrow()),
                row.getExposureIndex(),
                row.getExposedAt(),
                row.getTechniqueReference(),
                admissibility(row),
                row.getIndications().stream().map(WorkMapper::rowToIndication).toList(),
                row.getReadBy().orElse(""),
                row.getReadAt());
    }

    /** An indication as a row, its kind by its code and its clock position in minutes. */
    public static IndicationRow indicationToRow(Indication indication) {
        IndicationRow row = new IndicationRow();
        row.setKind(indication.kind().code());
        row.setStartsAt(indication.startsAt());
        row.setLength(indication.length());
        row.setWidth(indication.width());
        row.setDepthFraction(indication.depthFraction().orElse(null));
        row.setClockMinutes(indication.clock().map(ClockPosition::minutes).orElse(null));
        return row;
    }

    /**
     * An indication row back into the indication, naming the film it was seen on when the row hangs
     * off one.
     *
     * @throws ConfigurationError for a stored code that is not a kind
     */
    public static Indication rowToIndication(IndicationRow row) {
        Kind kind;
        try {
            kind = Kind.byCode(row.getKind());
        } catch (IllegalArgumentException unknown) {
            throw new ConfigurationError("stored indication code is not a kind", Context.of("kind", row.getKind()));
        }
        return new Indication(
                kind,
                row.getStartsAt(),
                row.getLength(),
                row.getWidth(),
                row.getDepthFraction(),
                row.getClockMinutes().map(ClockPosition::new),
                row.getRadiograph().map(RadiographRow::getNumber).orElse(""));
    }

    private static Optional<String> reasonText(Admissibility admissibility) {
        if (admissibility.admissible()) {
            return Optional.empty();
        }
        return Optional.of(String.join(REASON_SEPARATOR, admissibility.reasons()));
    }

    private static Admissibility admissibility(RadiographRow row) {
        if (row.isAdmissible()) {
            return new Admissibility(true, List.of(), List.of());
        }
        List<String> reasons = Arrays.stream(
                        row.getInadmissibleReason().orElse("").split(Pattern.quote(REASON_SEPARATOR), -1))
                .filter(reason -> !reason.isEmpty())
                .toList();
        return new Admissibility(false, reasons, List.of());
    }
}
