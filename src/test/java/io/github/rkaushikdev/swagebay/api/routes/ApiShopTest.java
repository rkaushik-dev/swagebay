package io.github.rkaushikdev.swagebay.api.routes;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.swagebay.api.Http;
import io.github.rkaushikdev.swagebay.api.RouteTest;
import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.safety.Inspection;
import io.github.rkaushikdev.swagebay.services.SourceService;
import io.github.rkaushikdev.swagebay.services.WipeService;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@RouteTest
class ApiShopTest {

    private static final LocalDateTime ASSAY = LocalDateTime.of(2025, 7, 1, 0, 0);
    private static final String DAY = "2025-08-20";

    @Autowired
    private MockMvc mvc;

    private Database database;
    private Http api;

    @BeforeEach
    void freshDatabase() {
        database = TestDatabase.fresh();
        api = new Http(mvc);
    }

    private void stock() {
        stock(true, true);
    }

    /** A device and a source on the inventory, wiped and inspected unless told otherwise. */
    private void stock(boolean wiped, boolean inspected) {
        database.transaction(work -> {
            SourceService.registerDevice(work, "D-77", "880d");
            SourceService.registerSource(
                    work,
                    new SealedSource(
                            new SourceCertificate(
                                    "IR-5512", Isotopes.IR192, curie(90), ASSAY, "QSA", "A424-9", mm(3), mm(3)),
                            curie(15)));
            if (wiped) {
                WipeService.recordWipe(work, "IR-5512", LocalDateTime.of(2025, 8, 1, 0, 0), BigDecimal.ZERO, "rp");
            }
            if (inspected) {
                work.maintenance().record(Inspection.quarterly("D-77", LocalDate.of(2025, 8, 1), "0303"));
                work.maintenance().record(Inspection.preUse("D-77", LocalDate.of(2025, 8, 20), "0142"));
            }
        });
    }

    private void aJob() {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("client", "c");
        job.put("site", "s");
        job.put("acceptance_code", "API 1104");
        job.put("procedure_number", "RT-001");
        job.put("opened_on", "2025-08-01");
        api.postJson("/jobs", job);
    }

    /** A quarterly strip down on a device that is not on the inventory. */
    private void anUnknownDevice() {
        database.transaction(
                work -> work.maintenance().record(Inspection.quarterly("D-99", LocalDate.of(2025, 8, 1), "0303")));
    }

    private static List<String> texts(JsonNode array) {
        List<String> found = new ArrayList<>();
        array.forEach(item -> found.add(item.asText()));
        return found;
    }

    @Test
    void aQuietMorningReadsAsQuiet() {
        stock();
        JsonNode body = api.get("/morning", "on", DAY).json();
        assertThat(body.get("quiet").asBoolean()).isTrue();
        assertThat(body.get("stops_today")).isEmpty();
    }

    @Test
    void aDeviceWithNothingOnRecordStopsTheDay() {
        stock(true, false);
        JsonNode body = api.get("/morning", "on", DAY).json();
        assertThat(body.get("quiet").asBoolean()).isFalse();
        assertThat(texts(body.get("stops_today"))).anyMatch(line -> line.contains("no quarterly"));
    }

    @Test
    void theLoadsAreOnlyListedWhenTheyAreAskedFor() {
        stock();
        assertThat(api.get("/morning", "on", DAY).json().get("loads_ready")).isEmpty();
        JsonNode body = api.get("/morning", "on", DAY, "loads", "true").json();
        assertThat(texts(body.get("loads_ready"))).containsExactly("D-77 with IR-5512");
    }

    @Test
    void theMorningDefaultsToTodayWhenNoDayIsGiven() {
        stock();
        assertThat(api.get("/morning").status()).isEqualTo(200);
    }

    @Test
    void aChecklistForAJobThatIsNotThereIsA404() {
        assertThat(api.get("/jobs/25-0001/checklist").status()).isEqualTo(404);
    }

    @Test
    void aChecklistKeepsTheThreeKindsApart() {
        aJob();
        JsonNode body = api.get("/jobs/25-0001/checklist").json();
        assertThat(body.get("clear").asBoolean()).isFalse();
        assertThat(texts(body.get("work_reasons"))).containsExactly("the job has no welds on it");
        assertThat(body.get("report_reasons")).isEmpty();
        assertThat(body.get("image_reasons")).isEmpty();
    }

    @Test
    void anOpenReportShowsUpUnderReports() {
        aJob();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reference", "NCR-1");
        report.put("job", "25-0001");
        report.put("item", "GW-104");
        report.put("requirement", "API 1104 9.3.8");
        report.put("account", "slag");
        report.put("raised_on", "2025-08-02");
        report.put("raised_by", "0142");
        api.postJson("/nonconformances", report);
        JsonNode body = api.get("/jobs/25-0001/checklist").json();
        assertThat(texts(body.get("report_reasons"))).containsExactly("NCR-1 is still open");
    }

    @Test
    void aCleanShopReadsAsHealthy() {
        stock();
        JsonNode body = api.get("/doctor").json();
        assertThat(body.get("healthy").asBoolean()).isTrue();
        assertThat(body.get("findings")).isEmpty();
        assertThat(body.get("checks_run")).hasSameSizeAs(body.get("checks_available"));
    }

    @Test
    void aFindingComesBackWithTheCheckThatFoundIt() {
        anUnknownDevice();
        JsonNode body = api.get("/doctor").json();
        assertThat(body.get("healthy").asBoolean()).isFalse();
        assertThat(body.get("findings").get(0).get("check").asText()).isEqualTo("unknown device");
        assertThat(body.get("findings").get(0).get("subject").asText()).isEqualTo("D-99");
    }

    @Test
    void onlyTheNamedChecksRun() {
        anUnknownDevice();
        JsonNode body = api.get("/doctor", "check", "self closed").json();
        assertThat(texts(body.get("checks_run"))).containsExactly("self closed");
        assertThat(body.get("healthy").asBoolean()).isTrue();
    }

    @Test
    void theOpenapiDocumentListsTheOperationalRoutes() {
        JsonNode paths = api.get("/openapi.json").json().get("paths");
        assertThat(paths.has("/morning")).isTrue();
        assertThat(paths.has("/doctor")).isTrue();
        assertThat(paths.has("/jobs/{number}/checklist")).isTrue();
    }

    @Test
    void theTwoSheetsBothSurviveInTheDocument() {
        JsonNode schemas = api.get("/openapi.json").json().get("components").get("schemas");
        assertThat(schemas.has("MorningOut")).isTrue();
        assertThat(schemas.has("SheetOut")).isTrue();
    }
}
