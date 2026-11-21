package io.github.rkaushikdev.swagebay.cli;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The three things every gate command shares: one exit status, one printer and one day.
 *
 * <p>Several commands answer the same shape of question: here is a thing, here is everything wrong
 * with it, and the shell needs to know whether to carry on. They answer it with the same status,
 * print the reasons the same way, and read a day left off the command line as today and not as
 * nothing.
 *
 * <p>The printer takes the groups in the order they should be acted on and prints only the ones
 * with anything in them, because an empty heading reads as a finding that got lost.
 */
public final class Gates {

    /** One number, for every gate that answers no. */
    public static final int EXIT_NOT_CLEAR = 4;

    private Gates() {}

    /** A day option as a day, defaulting to today and not to nothing. */
    public static LocalDate asDay(Optional<LocalDateTime> on) {
        return on.map(LocalDateTime::toLocalDate).orElseGet(LocalDate::now);
    }

    /** {@link #report(PrintWriter, String, List, String)} that prints nothing when all is clear. */
    public static int report(PrintWriter out, String title, List<Map.Entry<String, List<String>>> groups) {
        return report(out, title, groups, "");
    }

    /**
     * Print the reasons under their headings and answer the exit status: {@link Main#EXIT_OK} when
     * there are none, after printing {@code clearMessage} if it is not empty, and
     * {@link #EXIT_NOT_CLEAR} when there are any. A command returns the answer as its own status,
     * which is what makes these commands usable as the last line of a script.
     *
     * <p>A group with an empty label prints its reasons with no heading over them.
     */
    public static int report(
            PrintWriter out, String title, List<Map.Entry<String, List<String>>> groups, String clearMessage) {
        List<Map.Entry<String, List<String>>> found =
                groups.stream().filter(group -> !group.getValue().isEmpty()).toList();
        if (found.isEmpty()) {
            if (!clearMessage.isEmpty()) {
                out.println(clearMessage);
            }
            return Main.EXIT_OK;
        }
        out.println(Render.heading(title));
        for (Map.Entry<String, List<String>> group : found) {
            if (!group.getKey().isEmpty()) {
                out.println(Render.heading(group.getKey()));
            }
            out.println(Render.bullets(group.getValue()));
        }
        return EXIT_NOT_CLEAR;
    }
}
