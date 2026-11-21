package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What API 1104 says about one weld: the acceptance table applied to what the reader found.
 *
 * <p>Four checks run against each kind present, and which of them run is decided by the row
 * rather than by the code here. That is the point of keeping the table separate: adding a rule to
 * the standard means adding a field, not adding a branch. The individual check asks whether any one
 * imperfection is over its own cap. The width check does the same across the weld rather than along
 * it. The aggregate check slides the twelve inch window to its worst position and compares the
 * total. The count check does the same but counts instead of measuring.
 *
 * <p>On a weld shorter than the window the aggregate check is replaced rather than skipped. That is
 * a real difference and it catches out anybody who assumes short welds are judged more leniently: a
 * percentage of a short weld is a small number.
 *
 * <p>Cracks are handled before any of it. There is no arithmetic that makes a crack acceptable, so
 * the only question is whether it is the one exception the code allows, a shallow crater crack
 * under four millimetres, and any crack finding rejects the weld rather than sending it to repair.
 *
 * @param disposition what should happen to the weld
 * @param findings every rule that was broken, kind by kind in alphabetical order of the
 *     abbreviation, then the undercut depths
 * @param wall the wall the weld was judged at
 */
public record Assessment(Disposition disposition, List<Finding> findings, Length wall) {

    /** An assessment; the findings are copied. */
    public Assessment {
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(wall, "wall");
        findings = List.copyOf(findings);
    }

    /**
     * Run every check the table calls for and collect the findings.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static Assessment assess(IndicationSet found, Length wall) {
        if (wall.um() <= 0) {
            throw new ValidationError("a wall thickness has to be positive");
        }
        List<Finding> findings = new ArrayList<>();
        found.kinds().stream()
                .sorted(Comparator.comparing(Kind::name))
                .forEach(kind -> findings.addAll(checkKind(found, kind, wall)));
        findings.addAll(checkUndercutDepth(found, wall));
        return new Assessment(dispositionOf(findings), findings, wall);
    }

    /** Accept with no findings, reject when any finding is against a kind that is always rejectable, repair otherwise. */
    static Disposition dispositionOf(List<Finding> findings) {
        if (findings.isEmpty()) {
            return Disposition.ACCEPT;
        }
        return findings.stream().anyMatch(finding -> finding.kind().isAlwaysRejectable())
                ? Disposition.REJECT
                : Disposition.REPAIR;
    }

    /** Whether the weld is accepted. */
    public boolean accepted() {
        return disposition == Disposition.ACCEPT;
    }

    /** Every clause a finding quotes, once each, sorted. */
    public List<String> clauses() {
        return List.copyOf(findings.stream().map(Finding::clause).collect(Collectors.toCollection(TreeSet::new)));
    }

    /** {@code accept}, or every finding joined with semicolons. */
    public String describe() {
        if (accepted()) {
            return "accept";
        }
        return findings.stream().map(Finding::describe).collect(Collectors.joining("; "));
    }

    private static List<Finding> checkKind(IndicationSet found, Kind kind, Length wall) {
        Criterion row = Api1104.criterion(kind);
        List<Indication> present = found.ofKind(kind);
        List<Finding> out = new ArrayList<>();

        Optional<Length> limit = row.individualLengthLimit(wall);
        if (limit.isPresent()) {
            for (Indication indication : present) {
                if (indication.size().compareTo(limit.get()) > 0) {
                    out.add(new Finding(
                            row.clause(),
                            kind,
                            indication.size().formatMm(1) + " is over the "
                                    + limit.get().formatMm(1) + " allowed for one",
                            List.of(indication)));
                }
            }
        }

        if (row.maxWidth().isPresent()) {
            Length maxWidth = row.maxWidth().get();
            for (Indication indication : present) {
                if (indication.width().compareTo(maxWidth) > 0) {
                    out.add(new Finding(
                            row.clause(),
                            kind,
                            indication.width().formatMm(1) + " across is over the " + maxWidth.formatMm(1) + " allowed",
                            List.of(indication)));
                }
            }
        }

        if (row.maxAggregate().isPresent() && kind.isAggregated()) {
            out.addAll(
                    checkAggregate(found, kind, row.clause(), row.maxAggregate().get(), row.shortWeldFraction()));
        }

        if (row.maxCount().isPresent()) {
            int allowed = row.maxCount().get();
            int count = Aggregates.countInWorstWindow(present, found.weldLength());
            if (count > allowed) {
                out.add(new Finding(
                        row.clause(), kind, count + " in one window is over the " + allowed + " allowed", present));
            }
        }
        return out;
    }

    private static List<Finding> checkAggregate(
            IndicationSet found, Kind kind, String clause, Length allowed, Optional<BigDecimal> shortWeldFraction) {
        List<Indication> present = found.ofKind(kind);
        if (present.isEmpty()) {
            return List.of();
        }
        if (Aggregates.windowApplies(found.weldLength())) {
            Window window = Aggregates.worstWindow(present, found.weldLength());
            if (window.aggregate().compareTo(allowed) > 0) {
                return List.of(new Finding(
                        clause,
                        kind,
                        window.describe() + " is over the " + allowed.formatMm(1) + " allowed in "
                                + Aggregates.STANDARD_WINDOW.formatMm(0),
                        present));
            }
            return List.of();
        }
        if (shortWeldFraction.isEmpty()) {
            return List.of();
        }
        Length total = found.totalLengthOf(kind);
        BigDecimal fraction = Aggregates.fractionOfWeld(total, found.weldLength());
        if (fraction.compareTo(shortWeldFraction.get()) > 0) {
            return List.of(new Finding(
                    clause,
                    kind,
                    total.formatMm(1) + " is " + percent(fraction, 1) + " of a short weld, over "
                            + percent(shortWeldFraction.get(), 0),
                    present));
        }
        return List.of();
    }

    private static List<Finding> checkUndercutDepth(IndicationSet found, Length wall) {
        Length limit = Api1104.undercutDepthLimit(wall);
        List<Finding> out = new ArrayList<>();
        for (Indication indication : found.ofKind(Kind.U)) {
            if (indication.depthFraction().isEmpty()) {
                continue;
            }
            Length depth = wall.times(indication.depthFraction().get());
            if (depth.compareTo(limit) > 0) {
                out.add(new Finding(
                        "9.3.11",
                        Kind.U,
                        depth.formatMm(2) + " deep is over the " + limit.formatMm(2) + " allowed",
                        List.of(indication)));
            }
        }
        return out;
    }

    /** A fraction as a percentage to that many places, a tie going to the even digit: {@code 10.0%}. */
    private static String percent(BigDecimal fraction, int places) {
        return Decimals.halfEven(fraction.movePointRight(2), places).toPlainString() + "%";
    }
}
