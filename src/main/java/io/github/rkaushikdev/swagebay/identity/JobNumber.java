package io.github.rkaushikdev.swagebay.identity;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A job: two digit year and a four digit serial, the way the office files it ({@code 24-0417}).
 *
 * @param year the two digit year, 0 to 99
 * @param serial the serial within the year, 1 to 9999
 */
public record JobNumber(int year, int serial) implements Comparable<JobNumber> {

    private static final Pattern SHAPE =
            Pattern.compile("(?<year>\\d{2})-(?<serial>\\d{4})", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Comparator<JobNumber> ORDER =
            Comparator.comparingInt(JobNumber::year).thenComparingInt(JobNumber::serial);

    /**
     * A job number.
     *
     * @throws ValidationError for a year or serial out of range
     */
    public JobNumber {
        if (year < 0 || year > 99) {
            throw new ValidationError("a job year is two digits", Context.of("year", year));
        }
        if (serial < 1 || serial > 9999) {
            throw new ValidationError("a job serial is one to four digits", Context.of("serial", serial));
        }
    }

    /** Read a job number, ignoring surrounding white space and case. */
    public static JobNumber parse(String text) {
        Matcher match = SHAPE.matcher(Text.strip(text).toUpperCase(Locale.ROOT));
        if (!match.matches()) {
            throw new ValidationError("not a job number", Context.of("text", text));
        }
        return new JobNumber(Integer.parseInt(match.group("year")), Integer.parseInt(match.group("serial")));
    }

    /**
     * The next serial in the same year.
     *
     * @throws ValidationError when the year's serials are used up
     */
    public JobNumber next() {
        if (serial == 9999) {
            throw new ValidationError("the job serial is exhausted for this year", Context.of("year", year));
        }
        return new JobNumber(year, serial + 1);
    }

    @Override
    public int compareTo(JobNumber other) {
        return ORDER.compare(this, other);
    }

    /** The canonical form, {@code 24-0417}. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%02d-%04d", year, serial);
    }
}
