package io.github.rkaushikdev.swagebay.api;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Paging, done on a cursor rather than on an offset.
 *
 * <p>An offset page is wrong the moment anything is inserted. A client walking a job's radiographs
 * at fifty a page, while a night crew is recording more of them, sees some twice and misses others,
 * and the ones it misses are the new ones, which are the ones it wanted.
 *
 * <p>A cursor page is anchored to the last row the client saw. Rows inserted behind the cursor do
 * not shift it, so nothing is skipped, and the client can stop and resume days later from the same
 * cursor.
 *
 * <p>The cursor is the sort key rather than an opaque token. That is a deliberate choice: an opaque
 * token has to be decoded, which means a format, which means a version, and this is a small enough
 * system that a radiograph number as a cursor is honest and debuggable. The cost is that the sort
 * key has to be unique, so every paged listing sorts on something with a unique constraint behind
 * it.
 */
public final class Paging {

    /** The page size when none is asked for. */
    public static final int DEFAULT_PAGE = 50;

    /** The largest page the server serves; a bigger one asked for is cut to this. */
    public static final int MAX_PAGE = 500;

    private Paging() {}

    /**
     * A page size the server is prepared to serve: the default when none was asked for, and never
     * more than {@link #MAX_PAGE}.
     *
     * @throws ValidationError for a size below one
     */
    public static int clamp(Optional<Integer> size) {
        if (size.isEmpty()) {
            return DEFAULT_PAGE;
        }
        int wanted = size.get();
        if (wanted < 1) {
            throw new ValidationError("a page holds at least one row", Context.of("size", wanted));
        }
        return Math.min(wanted, MAX_PAGE);
    }

    /** {@link #clamp(Optional)} for a size that was asked for. */
    public static int clamp(int size) {
        return clamp(Optional.of(size));
    }

    /** {@link #paginate(List, Function, Optional)} at the default page size. */
    public static <T> Page<T> paginate(List<T> rows, Function<? super T, ?> key) {
        return paginate(rows, key, Optional.empty());
    }

    /** {@link #paginate(List, Function, Optional)} at a page size that was asked for. */
    public static <T> Page<T> paginate(List<T> rows, Function<? super T, ?> key, int size) {
        return paginate(rows, key, Optional.of(size));
    }

    /**
     * Cut a sorted list into one page, with the cursor for the next.
     *
     * <p>One extra row is fetched by the caller and handed in, which is how the server knows whether
     * there is a next page without counting the whole table. That is why this takes a list one
     * longer than the page rather than doing the query itself.
     *
     * @param key the sort key of a row, whose text is the cursor
     * @throws ValidationError for a size below one, or when there is no key to make a cursor from
     */
    public static <T> Page<T> paginate(List<T> rows, Function<? super T, ?> key, Optional<Integer> size) {
        int wanted = clamp(size);
        if (key == null) {
            throw new ValidationError("a cursor needs a key function");
        }
        if (rows.size() <= wanted) {
            return new Page<>(rows, Optional.empty(), wanted);
        }
        List<T> kept = rows.subList(0, wanted);
        return new Page<>(kept, Optional.of(String.valueOf(key.apply(kept.get(kept.size() - 1)))), wanted);
    }

    /** A cursor from a query string, a blank one read as none, surrounding white space removed. */
    public static Optional<String> after(Optional<String> cursor) {
        return cursor.map(Text::strip).filter(cleaned -> !cleaned.isEmpty());
    }

    /** How many rows to ask the database for, which is one more than the page. */
    public static int fetchSize(Optional<Integer> size) {
        return clamp(size) + 1;
    }

    /** {@link #fetchSize(Optional)} for a size that was asked for. */
    public static int fetchSize(int size) {
        return fetchSize(Optional.of(size));
    }
}
