package io.github.rkaushikdev.swagebay.identity;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A weld, and which repair round of it this is.
 *
 * <p>The identifier carries the repair history. {@code GW-104} is the weld as first made,
 * {@code GW-104R1} the first repair of it, {@code GW-104R2} the second. The repair round is what
 * the repair rate is computed against, and a shop that files repairs as fresh welds shows a repair
 * rate of zero forever.
 *
 * @param prefix the two letter kind of weld, one of {@link #PREFIXES}
 * @param number the weld number, 1 to 99999
 * @param repair the repair round, 0 for the weld as first made, up to 99
 */
public record WeldId(String prefix, int number, int repair) implements Comparable<WeldId> {

    /** The kinds of weld, by the prefix the identifier starts with. */
    public static final Map<String, String> PREFIXES;

    static {
        Map<String, String> prefixes = new LinkedHashMap<>();
        prefixes.put("GW", "girth weld");
        prefixes.put("LW", "longitudinal weld");
        prefixes.put("FW", "fillet weld");
        prefixes.put("BW", "branch weld");
        prefixes.put("SW", "socket weld");
        PREFIXES = Collections.unmodifiableMap(prefixes);
    }

    private static final Pattern SHAPE = Pattern.compile(
            "(?<prefix>[A-Z]{2})-(?<number>\\d{1,5})(?:R(?<repair>\\d{1,2}))?", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Comparator<WeldId> ORDER = Comparator.comparing(WeldId::prefix)
            .thenComparingInt(WeldId::number)
            .thenComparingInt(WeldId::repair);

    /**
     * A weld identifier.
     *
     * @throws ValidationError for an unknown prefix or a number or round out of range
     */
    public WeldId {
        if (!PREFIXES.containsKey(prefix)) {
            throw new ValidationError("unknown weld prefix", Context.of("prefix", prefix));
        }
        if (number < 1 || number > 99_999) {
            throw new ValidationError("a weld number is one to five digits", Context.of("number", number));
        }
        if (repair < 0 || repair > 99) {
            throw new ValidationError("a repair round is zero to ninety nine", Context.of("repair", repair));
        }
    }

    /** The weld as first made. */
    public WeldId(String prefix, int number) {
        this(prefix, number, 0);
    }

    /** Read a weld identifier, ignoring surrounding white space and case; it is stored upper case. */
    public static WeldId parse(String text) {
        Matcher match = SHAPE.matcher(Text.strip(text).toUpperCase(Locale.ROOT));
        if (!match.matches()) {
            throw new ValidationError("not a weld identifier", Context.of("text", text));
        }
        String repair = match.group("repair");
        return new WeldId(
                match.group("prefix"),
                Integer.parseInt(match.group("number")),
                repair == null ? 0 : Integer.parseInt(repair));
    }

    /** Whether this is a repair round rather than the weld as first made. */
    public boolean isRepair() {
        return repair > 0;
    }

    /** The weld as first made, whichever round this is. */
    public WeldId original() {
        return new WeldId(prefix, number, 0);
    }

    /** The next repair round of the same weld. */
    public WeldId nextRepair() {
        return new WeldId(prefix, number, repair + 1);
    }

    /** The kind of weld spelt out: {@code branch weld 7, repair 2}. */
    public String describe() {
        String kind = PREFIXES.get(prefix);
        if (repair != 0) {
            return kind + " " + number + ", repair " + repair;
        }
        return kind + " " + number;
    }

    @Override
    public int compareTo(WeldId other) {
        return ORDER.compare(this, other);
    }

    /** The canonical form, {@code GW-104R1}. */
    @Override
    public String toString() {
        return prefix + "-" + number + (repair != 0 ? "R" + repair : "");
    }
}
