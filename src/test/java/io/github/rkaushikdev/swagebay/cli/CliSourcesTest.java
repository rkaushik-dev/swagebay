package io.github.rkaushikdev.swagebay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliSourcesTest {

    private static final String ASSAY = "2024-11-01T00:00:00";

    @TempDir
    Path tmp;

    private Shell shop;

    @BeforeEach
    void aShopWithACamera() {
        shop = Shell.against(Shell.fileUrl(tmp, "shop"));
        shop.run("init");
        shop.run("source", "add-device", "880D-221", "880d");
    }

    private Shell.Result register(String serial) {
        return shop.run("source", "register", serial, "--isotope", "ir192", "--curies", "90", "--assayed", ASSAY);
    }

    private Shell.Result register() {
        return register("IR-5512");
    }

    /** Both checks, so the dispatch gate lets the load out. */
    private void inspected() {
        shop.run("device", "inspect", "880D-221", "--quarterly", "--on", "2024-10-14", "--by", "0303");
        shop.run("device", "inspect", "880D-221", "--on", "2024-11-04", "--by", "0142");
    }

    /** Everything the gate asks for: a source, a wipe test and a checked camera. */
    private void ready() {
        register();
        shop.run("source", "wipe", "IR-5512", "--at", ASSAY, "--by", "rp");
        inspected();
    }

    private Shell.Result sendOut(String site) {
        return shop.run(
                "source",
                "send-out",
                "IR-5512",
                "--device",
                "880D-221",
                "--crew",
                "0142",
                "--site",
                site,
                "--on",
                "2024-11-04");
    }

    @Test
    void aSourceIsRegistered() {
        Shell.Result result = register();
        assertThat(result.code()).isZero();
        assertThat(result.out().strip()).isEqualTo("registered IR-5512");
    }

    @Test
    void theSameSerialTwiceIsAConflict() {
        register();
        Shell.Result again = register();
        assertThat(again.code()).isEqualTo(Main.EXIT_CONFLICT);
        assertThat(again.failure()).isEqualTo(ConflictError.CODE);
    }

    @Test
    void anIsotopeNothingKnowsAboutIsRefused() {
        Shell.Result result =
                shop.run("source", "register", "RA-1", "--isotope", "ra226", "--curies", "1", "--assayed", ASSAY);
        assertThat(result.code()).isEqualTo(Main.EXIT_CONFIGURATION);
        assertThat(result.failure()).isEqualTo(ConfigurationError.CODE);
    }

    @Test
    void theSourcesInServiceAreListedWithWhereTheyAre() {
        register();
        String listing = shop.run("source", "list").out();
        assertThat(listing).contains("IR-5512", "90.0 Ci", "shop");
    }

    @Test
    void aSourceWithNoWipeIsReportedUnfit() {
        register();
        Shell.Result result = shop.run("source", "fitness", "IR-5512", "--at", "2024-11-05T22:00:00");
        assertThat(result.out()).contains("IR-5512 fit: no", "leak test is not current");
    }

    @Test
    void aWipedSourceIsReportedFit() {
        register();
        shop.run("source", "wipe", "IR-5512", "--at", ASSAY, "--by", "rp");
        Shell.Result result = shop.run("source", "fitness", "IR-5512", "--at", "2024-11-05T22:00:00");
        assertThat(result.out()).contains("IR-5512 fit: yes");
    }

    @Test
    void aFailingWipeSaysTheSourceIsOutOfService() {
        register();
        Shell.Result result = shop.run("source", "wipe", "IR-5512", "--at", ASSAY, "--removed", "400", "--by", "rp");
        assertThat(result.out()).contains("failed", "awaiting disposal");
    }

    @Test
    void aSourceGoesOutAndComesBack() {
        ready();
        Shell.Result out = sendOut("Come By Chance");
        assertThat(out.out()).contains("out to Come By Chance");
        assertThat(shop.run("source", "list").out()).contains("Come By Chance");
        Shell.Result back = shop.run("source", "bring-back", "IR-5512", "--on", "2024-11-06", "--exposures", "24");
        assertThat(back.out()).contains("back in the shop");
    }

    @Test
    void aSourceCannotGoOutTwice() {
        ready();
        sendOut("site");
        Shell.Result again = sendOut("site");
        assertThat(again.code()).isEqualTo(Main.EXIT_CONFLICT);
        assertThat(again.failure()).isEqualTo(ConflictError.CODE);
    }

    @Test
    void aSourceThatIsNotThereIsNotFound() {
        Shell.Result result = shop.run("source", "fitness", "IR-9999");
        assertThat(result.code()).isEqualTo(Main.EXIT_NOT_FOUND);
        assertThat(result.failure()).isEqualTo(NotFoundError.CODE);
    }

    @Test
    void theOverdueListNamesTheSourcesThatNeedWiping() {
        register();
        assertThat(shop.run("source", "overdue", "--on", "2024-11-05").out()).contains("IR-5512");
        shop.run("source", "wipe", "IR-5512", "--at", ASSAY, "--by", "rp");
        assertThat(shop.run("source", "overdue", "--on", "2024-11-05").out()).doesNotContain("IR-5512");
    }

    @Test
    void theHistoryReadsForwardsAndNamesEveryEvent() {
        ready();
        sendOut("site");
        List<String> lines = shop.run("source", "history", "IR-5512").lines();
        assertThat(lines.get(2)).contains("assay");
        assertThat(lines).anyMatch(line -> line.contains("leak test"));
        assertThat(lines).anyMatch(line -> line.contains("sent out"));
    }

    @Test
    void aRetiredSourceDropsOffTheInServiceList() {
        register();
        assertThat(shop.run("source", "retire", "IR-5512").out()).contains("awaiting disposal");
        assertThat(shop.run("source", "list").out()).doesNotContain("IR-5512");
    }

    @Test
    void aDeviceGoesOnTheInventoryOnce() {
        Shell.Result again = shop.run("source", "add-device", "880D-221", "880d");
        assertThat(again.code()).isEqualTo(Main.EXIT_CONFLICT);
        assertThat(again.failure()).isEqualTo(ConflictError.CODE);
    }
}
