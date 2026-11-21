package io.github.rkaushikdev.swagebay.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StockTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 1, 20);

    private static Lot lot(String number, int sheets, int expiresIn, String film, String size) {
        return new Lot(number, film, size, sheets, TODAY.minusDays(30), TODAY.plusDays(expiresIn));
    }

    private static Lot lot(String number, int sheets, int expiresIn) {
        return lot(number, sheets, expiresIn, "d7", "4x10");
    }

    private static Lot expiring(String number, int expiresIn) {
        return lot(number, 100, expiresIn);
    }

    private static Lot lot(String number) {
        return expiring(number, 200);
    }

    private static Lot lot() {
        return lot("L-1");
    }

    private static Store stocked(Lot... lots) {
        Store store = new Store();
        for (Lot item : lots) {
            store.receive(item);
        }
        return store;
    }

    @Test
    void filmComesOffTheShelfShortestDatedFirst() {
        Store store = stocked(expiring("L-1", 300), expiring("L-2", 100));
        assertThat(store.usableOn(TODAY, "d7", "4x10"))
                .extracting(Lot::lotNumber)
                .containsExactly("L-2", "L-1");
    }

    @Test
    void theIssueOrderIsByExpiryAndNotByReceipt() {
        Lot old = expiring("L-1", 300);
        Lot shortDated = expiring("L-2", 50);
        Store store = stocked(old, shortDated);
        assertThat(store.issue(TODAY, "d7", "4x10", 10)).containsExactly(new Issue("L-2", 10));
    }

    @Test
    void anIssueSpansLotsWhenOneWillNotCoverIt() {
        Store store = stocked(lot("L-1", 30, 50), lot("L-2", 100, 300));
        assertThat(store.issue(TODAY, "d7", "4x10", 50)).containsExactly(new Issue("L-1", 30), new Issue("L-2", 20));
    }

    @Test
    void issuingTakesTheSheetsOffTheShelf() {
        Store store = stocked(lot("L-1", 100, 200));
        store.issue(TODAY, "d7", "4x10", 40);
        assertThat(store.onHand(TODAY, "d7", "4x10")).isEqualTo(60);
    }

    @Test
    void expiredFilmIsNeverIssued() {
        Store store = stocked(expiring("L-1", -1), expiring("L-2", 100));
        assertThat(store.usableOn(TODAY, "d7", "4x10"))
                .extracting(Lot::lotNumber)
                .containsExactly("L-2");
        assertThat(store.onHand(TODAY, "d7", "4x10")).isEqualTo(100);
    }

    @Test
    void askingForMoreThanThereIsNamesTheShortfall() {
        Store store = stocked(lot("L-1", 20, 200));
        assertThatThrownBy(() -> store.issue(TODAY, "d7", "4x10", 50))
                .isInstanceOfSatisfying(ConflictError.class, caught -> {
                    assertThat(caught.context().get("on_hand")).isEqualTo(20);
                    assertThat(caught.context().get("wanted")).isEqualTo(50);
                });
    }

    @Test
    void aFailedIssueTakesNothingOffTheShelf() {
        Store store = stocked(lot("L-1", 20, 200));
        assertThatThrownBy(() -> store.issue(TODAY, "d7", "4x10", 50)).isInstanceOf(ConflictError.class);
        assertThat(store.onHand(TODAY, "d7", "4x10")).isEqualTo(20);
    }

    @Test
    void anIssueOfNothingIsRefused() {
        Store store = stocked(lot());
        assertThatThrownBy(() -> store.issue(TODAY, "d7", "4x10", 0)).isInstanceOf(ValidationError.class);
    }

    @Test
    void filmOfAnotherSizeOrTypeIsNotOnHand() {
        Store store = stocked(
                lot("L-1", 100, 200, "d7", "4x10"),
                lot("L-2", 100, 200, "d7", "7x17"),
                lot("L-3", 100, 200, "d4", "4x10"));
        assertThat(store.onHand(TODAY, "d7", "4x10")).isEqualTo(100);
        assertThat(store.onHand(TODAY, "d7", "7x17")).isEqualTo(100);
        assertThat(store.onHand(TODAY, "d5", "4x10")).isZero();
    }

    @Test
    void theExpiredListOnlyNamesLotsWithFilmLeftInThem() {
        Store store = stocked(expiring("L-1", -1), lot("L-2", 0, -1));
        assertThat(store.expiredOn(TODAY)).containsExactly("L-1");
    }

    @Test
    void theShortDatedListIsWhatGetsUsedFirst() {
        Store store = stocked(expiring("L-1", 30), expiring("L-2", 300));
        assertThat(store.shortDatedOn(TODAY)).containsExactly("L-1");
        assertThat(Lot.SHORT_DATED_DAYS).isEqualTo(60);
    }

    @Test
    void somethingAlreadyExpiredIsNotShortDated() {
        Store store = stocked(expiring("L-1", -1));
        assertThat(store.shortDatedOn(TODAY)).isEmpty();
        assertThat(store.expiredOn(TODAY)).containsExactly("L-1");
    }

    @Test
    void theDaysLeftRunDownAndGoNegative() {
        assertThat(expiring("L-1", 30).daysLeftOn(TODAY)).isEqualTo(30);
        assertThat(expiring("L-1", -5).daysLeftOn(TODAY)).isEqualTo(-5);
    }

    @Test
    void theSameLotTwiceIsRefused() {
        Store store = stocked(lot("L-1"));
        assertThatThrownBy(() -> store.receive(lot("L-1"))).isInstanceOf(ConflictError.class);
    }

    @Test
    void aLotNeedsANumberAndADateThatMakesSense() {
        assertThatThrownBy(() -> lot("  ")).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> lot("L-1", -1, 200)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Lot("L-1", "d7", "4x10", 100, TODAY, TODAY))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theReorderPointFiresAtTheFloorAndNotBelowIt() {
        Store store = stocked(lot("L-1", 25, 200));
        assertThat(store.reorderAt(TODAY, "d7", "4x10", 20)).isFalse();
        assertThat(store.reorderAt(TODAY, "d7", "4x10", 25)).isTrue();
        assertThatThrownBy(() -> store.reorderAt(TODAY, "d7", "4x10", -1)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theDefaultShelfLifeIsEighteenMonths() {
        assertThat(Store.shelfLife(TODAY)).isEqualTo(TODAY.plusDays(540));
        assertThat(Store.shelfLife(TODAY, 6)).isEqualTo(TODAY.plusDays(180));
        assertThatThrownBy(() -> Store.shelfLife(TODAY, 0)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aLotPrintsTheWayTheShelfLabelReads() {
        assertThat(lot().describe()).isEqualTo("L-1 d7 4x10, 100 sheets");
    }
}
