package io.github.rkaushikdev.swagebay.stock;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Film stock, which runs out at two in the morning three hundred miles away.
 *
 * <p>Film has an expiry date and it is not advisory: a box a fortnight past its date produces film
 * that passes the density check and fails the indicator. So expired stock is never issued, and
 * stock is issued shortest dated first. The issue order is by expiry rather than by receipt, which
 * is nearly the same thing and differs exactly when a supplier ships something short dated.
 */
public final class Store {

    private static final Comparator<Lot> SHORTEST_DATED =
            Comparator.comparing(Lot::expiresOn).thenComparing(Lot::lotNumber);

    private final List<Lot> lots = new ArrayList<>();

    /** An empty shelf. */
    public Store() {}

    /** Every lot on the shelf, in the order received, empty ones included. */
    public List<Lot> lots() {
        return Collections.unmodifiableList(lots);
    }

    /**
     * Put a lot on the shelf.
     *
     * @throws ConflictError for a lot number already on it
     */
    public void receive(Lot lot) {
        if (lots.stream().anyMatch(existing -> existing.lotNumber().equals(lot.lotNumber()))) {
            throw new ConflictError("that lot is already on the shelf", Context.of("lot", lot.lotNumber()));
        }
        lots.add(lot);
    }

    /** Everything of that film and size that can be issued on that day, shortest dated first. */
    public List<Lot> usableOn(LocalDate day, String filmKey, String size) {
        return lots.stream()
                .filter(lot -> lot.filmKey().equals(filmKey)
                        && lot.size().equals(size)
                        && lot.sheets() > 0
                        && !lot.isExpiredOn(day))
                .sorted(SHORTEST_DATED)
                .toList();
    }

    /** The sheets of that film and size that can be issued on that day. */
    public int onHand(LocalDate day, String filmKey, String size) {
        return total(usableOn(day, filmKey, size));
    }

    /**
     * Take sheets off the shelf, shortest dated first, and say which lots they came from. A refused
     * issue takes nothing.
     *
     * @throws ValidationError for fewer than one sheet
     * @throws ConflictError when there is not that much usable film, naming what was wanted and what
     *     is on hand
     */
    public List<Issue> issue(LocalDate day, String filmKey, String size, int sheets) {
        if (sheets <= 0) {
            throw new ValidationError("an issue is at least one sheet", Context.of("sheets", sheets));
        }
        List<Lot> available = usableOn(day, filmKey, size);
        int onHand = total(available);
        if (onHand < sheets) {
            throw new ConflictError(
                    "there is not enough film on the shelf",
                    Context.of("film", filmKey, "size", size, "wanted", sheets, "on_hand", onHand));
        }
        List<Issue> taken = new ArrayList<>();
        int remaining = sheets;
        for (Lot lot : available) {
            if (remaining == 0) {
                break;
            }
            int take = Math.min(lot.sheets(), remaining);
            lot.take(take);
            remaining -= take;
            taken.add(new Issue(lot.lotNumber(), take));
        }
        return List.copyOf(taken);
    }

    /** The lots with film left in them that have expired by that day, by lot number. */
    public List<String> expiredOn(LocalDate day) {
        return numbersOf(lot -> lot.isExpiredOn(day));
    }

    /** The lots with film left in them that are short dated on that day, by lot number. */
    public List<String> shortDatedOn(LocalDate day) {
        return numbersOf(lot -> lot.isShortDatedOn(day));
    }

    /**
     * Whether what is on hand has come down to the reorder floor.
     *
     * @throws ValidationError for a negative floor
     */
    public boolean reorderAt(LocalDate day, String filmKey, String size, int floor) {
        if (floor < 0) {
            throw new ValidationError("a reorder floor is not negative", Context.of("floor", floor));
        }
        return onHand(day, filmKey, size) <= floor;
    }

    /** The usual dating, eighteen months of thirty days, for a lot that arrives without a date on the box. */
    public static LocalDate shelfLife(LocalDate receivedOn) {
        return shelfLife(receivedOn, 18);
    }

    /**
     * The dating for a shelf life of that many months of thirty days.
     *
     * @throws ValidationError for a shelf life of no months or fewer
     */
    public static LocalDate shelfLife(LocalDate receivedOn, int months) {
        if (months <= 0) {
            throw new ValidationError("a shelf life is a positive number of months");
        }
        return receivedOn.plusDays(months * 30L);
    }

    private List<String> numbersOf(Predicate<Lot> condition) {
        return lots.stream()
                .filter(lot -> condition.test(lot) && lot.sheets() > 0)
                .map(Lot::lotNumber)
                .sorted()
                .toList();
    }

    private static int total(List<Lot> lots) {
        return lots.stream().mapToInt(Lot::sheets).sum();
    }
}
