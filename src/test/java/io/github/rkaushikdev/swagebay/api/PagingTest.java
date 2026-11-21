package io.github.rkaushikdev.swagebay.api;

import static io.github.rkaushikdev.swagebay.api.Paging.DEFAULT_PAGE;
import static io.github.rkaushikdev.swagebay.api.Paging.MAX_PAGE;
import static io.github.rkaushikdev.swagebay.api.Paging.after;
import static io.github.rkaushikdev.swagebay.api.Paging.clamp;
import static io.github.rkaushikdev.swagebay.api.Paging.fetchSize;
import static io.github.rkaushikdev.swagebay.api.Paging.paginate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PagingTest {

    private static final Function<String, String> TEXT = String::valueOf;

    private static List<String> rows(int count) {
        return rows(count, 1);
    }

    private static List<String> rows(int count, int start) {
        return IntStream.range(start, start + count)
                .mapToObj(i -> String.format(Locale.ROOT, "R-%04d", i))
                .toList();
    }

    @Test
    void aShortListIsTheLastPage() {
        Page<String> page = paginate(rows(10), TEXT, 50);
        assertThat(page.count()).isEqualTo(10);
        assertThat(page.isLast()).isTrue();
        assertThat(page.nextCursor()).isEmpty();
    }

    @Test
    void aFullListCarriesACursorToTheNextPage() {
        Page<String> page = paginate(rows(51), TEXT, 50);
        assertThat(page.count()).isEqualTo(50);
        assertThat(page.isLast()).isFalse();
        assertThat(page.nextCursor()).contains("R-0050");
    }

    @Test
    void theCursorIsTheLastRowTheClientSaw() {
        Page<String> page = paginate(rows(6), TEXT, 5);
        assertThat(page.nextCursor()).contains(page.items().get(page.items().size() - 1));
    }

    @Test
    void aListExactlyThePageSizeIsTheLastPage() {
        Page<String> page = paginate(rows(50), TEXT, 50);
        assertThat(page.isLast()).isTrue();
    }

    @Test
    void anEmptyListIsALastPageWithNothingInIt() {
        Page<String> page = paginate(List.<String>of(), TEXT, 50);
        assertThat(page.count()).isZero();
        assertThat(page.isLast()).isTrue();
    }

    @Test
    void theDefaultPageSizeIsUsedWhenNoneIsAskedFor() {
        assertThat(clamp(Optional.empty())).isEqualTo(DEFAULT_PAGE);
        assertThat(paginate(rows(100), TEXT).count()).isEqualTo(DEFAULT_PAGE);
    }

    @Test
    void aPageSizeOverTheCeilingIsClampedRatherThanRefused() {
        assertThat(clamp(10_000)).isEqualTo(MAX_PAGE);
        assertThat(paginate(rows(1000), TEXT, 10_000).size()).isEqualTo(MAX_PAGE);
    }

    @Test
    void aPageSizeBelowOneIsRefused() {
        assertThatThrownBy(() -> clamp(0)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> clamp(-5)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theFetchSizeIsOneMoreThanThePage() {
        assertThat(fetchSize(50)).isEqualTo(51);
        assertThat(fetchSize(Optional.empty())).isEqualTo(DEFAULT_PAGE + 1);
        assertThat(fetchSize(10_000)).isEqualTo(MAX_PAGE + 1);
    }

    @Test
    void aCursorWalksTheWholeListWithoutRepeatingARow() {
        List<String> everything = rows(23);
        List<String> seen = new ArrayList<>();
        Optional<String> cursor = Optional.empty();
        while (true) {
            Optional<String> from = cursor;
            List<String> remaining = everything.stream()
                    .filter(row -> from.isEmpty() || row.compareTo(from.get()) > 0)
                    .toList();
            Page<String> page = paginate(remaining, TEXT, 5);
            seen.addAll(page.items());
            if (page.isLast()) {
                break;
            }
            cursor = page.nextCursor();
        }
        assertThat(seen).isEqualTo(everything);
        assertThat(new HashSet<>(seen)).hasSameSizeAs(seen);
    }

    @Test
    void aRowInsertedBehindTheCursorDoesNotShiftThePage() {
        Page<String> first = paginate(rows(10), TEXT, 5);
        String cursor = first.nextCursor().orElseThrow();
        List<String> withInsert = new ArrayList<>(rows(10));
        withInsert.add("R-0002a");
        withInsert.sort(null);
        List<String> remaining =
                withInsert.stream().filter(row -> row.compareTo(cursor) > 0).toList();
        Page<String> second = paginate(remaining, TEXT, 5);
        assertThat(second.items()).doesNotContainAnyElementsOf(first.items());
    }

    @Test
    void aBlankCursorReadsAsNoCursor() {
        assertThat(after(Optional.empty())).isEmpty();
        assertThat(after(Optional.of(""))).isEmpty();
        assertThat(after(Optional.of("   "))).isEmpty();
        assertThat(after(Optional.of(" R-0005 "))).contains("R-0005");
    }

    /**
     * A key that is not a function no longer compiles; the one way left to hand over no key is an
     * absent one, which is refused the same way.
     */
    @Test
    void aKeyThatIsNotCallableIsRefused() {
        assertThatThrownBy(() -> paginate(rows(3), null, 2))
                .isInstanceOf(ValidationError.class)
                .hasMessage("a cursor needs a key function");
    }

    @Test
    void thePageReportsTheSizeItWasCutTo() {
        assertThat(paginate(rows(3), TEXT, 2).size()).isEqualTo(2);
        assertThat(paginate(rows(3), TEXT).size()).isEqualTo(DEFAULT_PAGE);
    }
}
