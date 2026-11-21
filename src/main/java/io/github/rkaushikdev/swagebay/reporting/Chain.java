package io.github.rkaushikdev.swagebay.reporting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything behind one radiograph, link by link.
 *
 * @param radiograph the radiograph the chain is behind
 * @param links every link, broken ones included, in the order a review walks them
 */
public record Chain(String radiograph, List<Link> links) {

    /** A chain; the links are copied. */
    public Chain {
        Objects.requireNonNull(radiograph, "radiograph");
        links = List.copyOf(links);
    }

    /** Whether every link holds. */
    public boolean isSound() {
        return links.stream().allMatch(Link::holds);
    }

    /** The links that do not hold, in order. */
    public List<Link> broken() {
        return links.stream().filter(link -> !link.holds()).toList();
    }

    /** The radiograph on a line of its own, then every link on its own indented line. */
    public String describe() {
        List<String> lines = new ArrayList<>();
        lines.add(radiograph);
        links.forEach(link -> lines.add("  " + link.describe()));
        return String.join("\n", lines);
    }
}
