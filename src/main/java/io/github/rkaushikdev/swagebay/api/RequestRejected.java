package io.github.rkaushikdev.swagebay.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A request that is not the shape its route takes, refused before any handler ran.
 *
 * <p>This is not a domain error and does not come back in the domain's shape. It comes back as a
 * {@code 422} whose body is {@code {"detail": [...]}}, one {@link Problem} for each thing wrong,
 * which is what every client of this service already reads for a malformed request. A domain
 * {@code ValidationError} raised by a handler is a different thing: the request had the right shape
 * and the domain refused what it said.
 */
public final class RequestRejected extends RuntimeException {

    private final List<Problem> problems;

    /** A refusal listing what was wrong, in the order it was found. */
    public RequestRejected(List<Problem> problems) {
        super(summary(problems));
        this.problems = List.copyOf(problems);
    }

    /** What was wrong. */
    public List<Problem> problems() {
        return problems;
    }

    /** The response body: {@code {"detail": [...]}}. */
    public Map<String, Object> body() {
        return Map.of("detail", problems.stream().map(Problem::asMap).toList());
    }

    private static String summary(List<Problem> problems) {
        return problems.stream()
                .map(problem -> problem.loc().stream().map(String::valueOf).collect(Collectors.joining(".")) + ": "
                        + problem.msg())
                .collect(Collectors.joining("; ", "the request was refused: ", ""));
    }
}
