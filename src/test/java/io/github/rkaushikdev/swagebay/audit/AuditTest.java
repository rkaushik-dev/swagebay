package io.github.rkaushikdev.swagebay.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2025, 5, 12, 9, 0);
    private static final LocalDateTime LATER = WHEN.plusHours(1);
    private static final LocalDateTime LATEST = WHEN.plusHours(2);

    private static Entry aChange(String was, String became, String reason) {
        return new Entry(WHEN, "0142", Action.CHANGED, "weld", "GW-104", "disposition", was, became, reason);
    }

    private static Entry aChange() {
        return aChange("repair", "accept", "");
    }

    @Test
    void aChangeNamesTheFieldAndBothValues() {
        Entry entry = aChange();
        assertThat(entry.was()).isEqualTo("repair");
        assertThat(entry.became()).isEqualTo("accept");
        assertThat(entry.fieldName()).isEqualTo("disposition");
    }

    @Test
    void aChangeWithNoActorIsRefused() {
        assertThatThrownBy(() -> new Entry(WHEN, "  ", Action.CREATED, "weld", "GW-104"))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aChangeThatNamesNoRecordIsRefused() {
        assertThatThrownBy(() -> new Entry(WHEN, "0142", Action.CREATED, "  ", "GW-104"))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Entry(WHEN, "0142", Action.CREATED, "weld", "  "))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aChangeThatNamesNoFieldIsRefused() {
        assertThatThrownBy(() -> new Entry(WHEN, "0142", Action.CHANGED, "weld", "GW-104", "", "a", "b"))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aChangeThatChangedNothingIsRefused() {
        assertThatThrownBy(() -> aChange("accept", "accept", ""))
                .isInstanceOfSatisfying(
                        ValidationError.class,
                        caught -> assertThat(caught.context().get("field")).isEqualTo("disposition"));
    }

    @Test
    void theHistoryOfOneRecordReadsOldestFirst() {
        Trail log = new Trail();
        log.changed(LATER, "0201", "weld", "GW-104", "disposition", "repair", "accept");
        log.created(WHEN, "0142", "weld", "GW-104");
        List<Entry> found = log.forRecord("weld", "GW-104");
        assertThat(found).extracting(Entry::action).containsExactly(Action.CREATED, Action.CHANGED);
    }

    @Test
    void anotherRecordIsNotInThisHistory() {
        Trail log = new Trail();
        log.created(WHEN, "0142", "weld", "GW-104");
        log.created(WHEN, "0142", "weld", "GW-110");
        assertThat(log.forRecord("weld", "GW-104")).hasSize(1);
    }

    @Test
    void whatOnePersonDidCanBePulledOut() {
        Trail log = new Trail();
        log.created(WHEN, "0142", "weld", "GW-104");
        log.created(WHEN, "0201", "weld", "GW-110");
        assertThat(log.byActor("0142")).hasSize(1);
        assertThat(log.byActor("0303")).isEmpty();
    }

    @Test
    void aFieldHistoryReadsWithoutReplayingTheWholeTrail() {
        Trail log = new Trail();
        log.changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair");
        log.changed(LATER, "0201", "weld", "GW-104", "disposition", "repair", "accept");
        assertThat(log.fieldHistory("weld", "GW-104", "disposition")).containsExactly("none", "repair", "accept");
    }

    @Test
    void theCurrentValueIsTheLastOneItBecame() {
        Trail log = new Trail();
        log.changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair");
        log.changed(LATER, "0201", "weld", "GW-104", "disposition", "repair", "accept");
        assertThat(log.current("weld", "GW-104", "disposition")).hasValue("accept");
    }

    @Test
    void aFieldNobodyTouchedHasNoHistoryAndNoCurrentValue() {
        Trail log = new Trail();
        log.created(WHEN, "0142", "weld", "GW-104");
        assertThat(log.fieldHistory("weld", "GW-104", "welder")).isEmpty();
        assertThat(log.current("weld", "GW-104", "welder")).isEmpty();
    }

    @Test
    void nothingChangedAfterSigningWhenNothingWasSigned() {
        Trail log = new Trail();
        log.changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair");
        assertThat(log.changedAfterSigning("weld", "GW-104")).isEmpty();
    }

    @Test
    void aChangeAfterTheSignatureIsWhatADisputeIsAbout() {
        Trail log = new Trail();
        log.changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair");
        log.signed(LATER, "0303", "weld", "GW-104");
        log.changed(LATEST, "0201", "weld", "GW-104", "disposition", "repair", "accept");
        List<Entry> after = log.changedAfterSigning("weld", "GW-104");
        assertThat(after).hasSize(1);
        assertThat(after.get(0).actor()).isEqualTo("0201");
    }

    @Test
    void aChangeBeforeTheSignatureIsNot() {
        Trail log = new Trail();
        log.changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair");
        log.signed(LATER, "0303", "weld", "GW-104");
        assertThat(log.changedAfterSigning("weld", "GW-104")).isEmpty();
    }

    @Test
    void anEntryPrintsTheWayTheTrailReads() {
        String text = aChange("repair", "accept", "client agreed").describe();
        assertThat(text).contains("2025-05-12 09:00 0142 weld GW-104 disposition repair to accept");
        assertThat(text).contains("(client agreed)");
    }

    @Test
    void aCreationPrintsWithoutFieldDetail() {
        Trail log = new Trail();
        log.created(WHEN, "0142", "weld", "GW-104");
        assertThat(log.entries().get(0).describe()).endsWith("weld GW-104 created");
    }

    @Test
    void anEmptyValuePrintsAsNothingRatherThanAGap() {
        Entry entry = new Entry(WHEN, "0142", Action.CHANGED, "weld", "GW-104", "welder", "", "w-19");
        assertThat(entry.describe()).contains("welder nothing to w-19");
    }
}
