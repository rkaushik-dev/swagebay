package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.util.Repr;
import io.github.rkaushikdev.swagebay.util.Text;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The discontinuities a weld can contain, named the way the code names them, and the one
 * distinction that matters.
 *
 * <p>Codes classify by shape before they classify by cause, and for a reason worth stating plainly.
 * A planar discontinuity is a crack or an unfused surface: it has essentially no thickness, it has
 * a sharp edge, and the sharp edge concentrates stress. A volumetric one is a bubble or a lump of
 * slag: it has a rounded boundary and it concentrates almost nothing. That is why a crack a
 * millimetre long is rejected and a pore three millimetres across is accepted, why the aggregate
 * length rules apply to some kinds and not others, and why the code has more to say about
 * incomplete penetration than about porosity.
 *
 * <p>The abbreviations are the ones written on the report because they are the ones written in API
 * 1104, and a client reading IPD knows what it means without a legend. They are not renamed to be
 * readable; {@link #toString()} is the abbreviation and {@link #value()} the description.
 */
public enum Kind {
    IP("inadequate penetration without high low"),
    IPD("inadequate penetration due to high low"),
    ICP("inadequate cross penetration"),
    IF("incomplete fusion"),
    IFD("incomplete fusion due to cold lap"),
    IC("internal concavity"),
    BT("burn through"),
    ESI("elongated slag inclusion"),
    ISI("isolated slag inclusion"),
    P("porosity"),
    CP("cluster porosity"),
    HB("hollow bead"),
    C("crack"),
    U("undercut"),
    AB("arc burn");

    private static final Set<Kind> PLANAR_KINDS = EnumSet.of(IP, IPD, ICP, IF, IFD, C, HB);
    private static final Set<Kind> AGGREGATED_KINDS = EnumSet.of(IP, IPD, ICP, IF, IFD, IC, ESI, HB, U);

    /** Every planar kind, in declaration order. */
    public static final List<Kind> PLANAR =
            Arrays.stream(values()).filter(Kind::isPlanar).toList();

    /** Every volumetric kind, in declaration order. */
    public static final List<Kind> VOLUMETRIC =
            Arrays.stream(values()).filter(Kind::isVolumetric).toList();

    private final String value;

    Kind(String value) {
        this.value = value;
    }

    /**
     * Read a report abbreviation back into a kind, ignoring surrounding white space and case.
     *
     * @throws IllegalArgumentException for an abbreviation that names no kind
     */
    public static Kind byCode(String code) {
        String wanted = Text.strip(code).toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(kind -> kind.name().equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no discontinuity code " + Repr.of(code)));
    }

    /** The description: {@code elongated slag inclusion}. */
    public String value() {
        return value;
    }

    /** Sharp edged, stress concentrating, and judged far harder. */
    public boolean isPlanar() {
        return PLANAR_KINDS.contains(this);
    }

    /** Rounded at the boundary; every kind that is not planar. */
    public boolean isVolumetric() {
        return !isPlanar();
    }

    /** Porosity and cluster porosity, which are measured across rather than along. */
    public boolean isRounded() {
        return this == P || this == CP;
    }

    /** Undercut, burn through, arc burn and internal concavity: the ones a finger would find. */
    public boolean isSurfaceBreaking() {
        return this == U || this == BT || this == AB || this == IC;
    }

    /** Whether several of these add up against a length of weld. */
    public boolean isAggregated() {
        return AGGREGATED_KINDS.contains(this);
    }

    /** A crack is a crack. There is no length that makes one acceptable. */
    public boolean isAlwaysRejectable() {
        return this == C;
    }

    /** The abbreviation the report writes: {@code ESI}. */
    public String code() {
        return name();
    }

    /** The abbreviation, the description and the shape: {@code C, crack, planar}. */
    public String describe() {
        return code() + ", " + value + ", " + (isPlanar() ? "planar" : "volumetric");
    }

    /** The abbreviation. */
    @Override
    public String toString() {
        return name();
    }
}
