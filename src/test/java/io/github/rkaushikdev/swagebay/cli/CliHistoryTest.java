package io.github.rkaushikdev.swagebay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliHistoryTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2025, 5, 22, 8, 30);
    private static final LocalDateTime SIGNED = LocalDateTime.of(2025, 5, 22, 16, 0);
    private static final LocalDateTime AFTER = LocalDateTime.of(2025, 5, 23, 9, 0);
    private static final String FILM = "24-0001-R0001";

    @TempDir
    Path tmp;

    private String url;
    private Shell shop;

    @BeforeEach
    void anEmptyShop() {
        url = Shell.fileUrl(tmp, "shop");
        shop = Shell.against(url);
        shop.run("init");
    }

    private void readAndReread() {
        Shell.seed(url, work -> {
            work.audit().created(WHEN, "0142", "radiograph", FILM);
            work.audit()
                    .changed(WHEN, "0142", "radiograph", FILM, "indications", "nothing", "ESI 20.0 mm", "second pass");
        });
    }

    @Test
    void aRecordWithNoHistorySaysSo() {
        Shell.Result result = shop.run("history", "record", "radiograph", FILM);
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("nothing recorded");
    }

    @Test
    void oneRecordsHistoryPrintsTheOldValue() {
        readAndReread();
        Shell.Result result = shop.run("history", "record", "radiograph", FILM);
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("ESI 20.0 mm", "second pass", "created");
    }

    @Test
    void aRecordKindNothingKnowsAboutIsRefused() {
        assertThat(shop.run("history", "record", "gizmo", FILM).code()).isNotZero();
    }

    @Test
    void oneBadgesDayPrintsWithoutTheWhoColumn() {
        readAndReread();
        Shell.Result result = shop.run("history", "actor", "0142", "--on", "2025-05-22");
        assertThat(result.code()).isZero();
        assertThat(result.output()).doesNotContain("who").contains("ESI 20.0 mm");
    }

    @Test
    void aBadgeThatChangedNothingSaysSo() {
        readAndReread();
        assertThat(shop.run("history", "actor", "0303", "--on", "2025-05-22").out())
                .contains("changed nothing");
    }

    @Test
    void aDayTheBadgeWasNotWorkingIsEmpty() {
        readAndReread();
        assertThat(shop.run("history", "actor", "0142", "--on", "2025-06-01").out())
                .contains("changed nothing");
    }

    @Test
    void moreThanOneDayCanBeAskedFor() {
        readAndReread();
        Shell.Result result = shop.run("history", "actor", "0142", "--on", "2025-05-20", "--days", "5");
        assertThat(result.out()).contains("ESI 20.0 mm");
    }

    @Test
    void zeroDaysIsRefused() {
        Shell.Result result = shop.run("history", "actor", "0142", "--on", "2025-05-22", "--days", "0");
        assertThat(result.code()).isNotZero();
    }

    @Test
    void nothingAfterASignatureNobodyGave() {
        readAndReread();
        Shell.Result result = shop.run("history", "after-signing", "radiograph", FILM);
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("nothing changed");
    }

    @Test
    void aChangeAfterSigningExitsFour() {
        readAndReread();
        Shell.seed(url, work -> {
            work.audit().signed(SIGNED, "0303", "radiograph", FILM);
            work.audit().changed(AFTER, "0201", "radiograph", FILM, "indications", "ESI 20.0 mm", "nothing");
        });
        Shell.Result result = shop.run("history", "after-signing", "radiograph", FILM);
        assertThat(result.code()).isEqualTo(4);
        assertThat(result.out()).contains("changed after signing");
    }

    @Test
    void theSignatureItselfIsNotAChangeAfterIt() {
        Shell.seed(url, work -> work.audit().signed(SIGNED, "0303", "radiograph", FILM));
        Shell.Result result = shop.run("history", "after-signing", "radiograph", FILM);
        assertThat(result.code()).isZero();
    }
}
