package io.github.rkaushikdev.swagebay.api.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.swagebay.api.Http;
import io.github.rkaushikdev.swagebay.api.RouteTest;
import io.github.rkaushikdev.swagebay.testing.CsvRows;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@RouteTest
class ApiReportsTest {

    private static final String SHOT = "2024-12-09T23:30:00";
    private static final String READ = "2024-12-10T08:00:00";
    private static final String BASE = "/jobs/24-0001";

    @Autowired
    private MockMvc mvc;

    private Http api;

    @BeforeEach
    void aJobWithOneWeld() {
        TestDatabase.fresh();
        api = new Http(mvc);
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("client", "Maritime Pipelines");
        job.put("site", "Come By Chance");
        job.put("acceptance_code", "API 1104");
        job.put("procedure_number", "RT-001");
        job.put("opened_on", "2024-12-01");
        api.postJson("/jobs", job);
        Map<String, Object> weld = new LinkedHashMap<>();
        weld.put("weld", "GW-104");
        weld.put(
                "joint",
                Map.of(
                        "material", "carbon_steel",
                        "wall", Map.of("text", "10.97 mm"),
                        "outside_diameter", Map.of("text", "168.3 mm"),
                        "reinforcement", Map.of("text", "2 mm")));
        weld.put("weld_length", Map.of("text", "529 mm"));
        weld.put("welder", "w-19");
        weld.put("welded_on", "2024-12-01");
        api.postJson(BASE + "/welds", weld);
    }

    private static Map<String, Object> aShot() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("exposure_index", 1);
        body.put("exposed_at", SHOT);
        body.put("technique_reference", "RT-001 rev 1");
        body.put("medium", "film");
        body.put("film", "d7");
        body.put("penetrated", Map.of("text", "25.94 mm"));
        body.put("indicator", Map.of("hole_identity", 30, "hole_visible", true));
        body.put(
                "densities",
                List.of(
                        Map.of("label", "indicator", "density", "2.50", "through_indicator", true),
                        Map.of("label", "weld", "density", "2.50")));
        body.put(
                "calibration",
                Map.of(
                        "checked_at", "2024-12-09T18:00:00",
                        "step_nominal", "2.00",
                        "step_read", "2.01",
                        "instrument", "X-Rite 301"));
        return body;
    }

    private String shoot() {
        return api.postJson(BASE + "/welds/GW-104/shots", aShot())
                .json()
                .get("number")
                .asText();
    }

    /** Shoot the one exposure of GW-104 and have badge 0142 read it. */
    private String shootAndRead() {
        String number = shoot();
        Map<String, Object> reading = new LinkedHashMap<>();
        reading.put("reader_badge", "0142");
        reading.put("read_at", READ);
        api.postJson(BASE + "/welds/GW-104/radiographs/" + number + "/reading", reading);
        return number;
    }

    private static List<String> texts(JsonNode array) {
        List<String> found = new ArrayList<>();
        array.forEach(item -> found.add(item.asText()));
        return found;
    }

    @Test
    void anUnfinishedJobReportsItsFaults() {
        JsonNode body = api.get(BASE + "/report").json();
        assertThat(body.get("complete").asBoolean()).isFalse();
        assertThat(texts(body.get("faults"))).contains("GW-104 has no radiographs");
    }

    @Test
    void aFinishedJobReportsCompleteWithAnEmptyFaultList() {
        shootAndRead();
        JsonNode body = api.get(BASE + "/report").json();
        assertThat(body.get("complete").asBoolean()).isTrue();
        assertThat(body.get("faults").isArray()).isTrue();
        assertThat(body.get("faults")).isEmpty();
    }

    @Test
    void thePackageCarriesTheJobDetails() {
        JsonNode body = api.get(BASE + "/report").json();
        assertThat(body.get("job").asText()).isEqualTo("24-0001");
        assertThat(body.get("client").asText()).isEqualTo("Maritime Pipelines");
        assertThat(body.get("code").asText()).isEqualTo("API 1104");
        assertThat(body.get("closed_on").isNull()).isTrue();
    }

    @Test
    void theWeldSummaryCarriesTheDisposition() {
        shootAndRead();
        JsonNode weld = api.get(BASE + "/report").json().get("welds").get(0);
        assertThat(weld.get("weld").asText()).isEqualTo("GW-104");
        assertThat(weld.get("disposition").asText()).isEqualTo("accept");
        assertThat(weld.get("wall_mm").isTextual()).isTrue();
        assertThat(weld.get("wall_mm").asText()).isEqualTo("10.97");
    }

    @Test
    void theCsvComesBackAsTextRatherThanJson() {
        shootAndRead();
        Http.Answer response = api.get(BASE + "/report/welds.csv");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.result().getResponse().getContentType()).startsWith("text/plain");
        List<List<String>> rows = CsvRows.parse(response.text());
        assertThat(rows.get(0).get(0)).isEqualTo("weld");
        assertThat(rows.get(1).get(0)).isEqualTo("GW-104");
    }

    @Test
    void everyPublishedSheetCanBeFetched() {
        shootAndRead();
        List<String> sheets = texts(api.get(BASE + "/report/sheets").json());
        assertThat(sheets).isNotEmpty();
        for (String sheet : sheets) {
            assertThat(api.get(BASE + "/report/" + sheet + ".csv").status()).isEqualTo(200);
        }
    }

    @Test
    void aSheetNobodyPublishesIsRefused() {
        assertThat(api.get(BASE + "/report/everything.csv").status()).isEqualTo(422);
    }

    @Test
    void theSummaryCsvIsOneRowUnderItsHeader() {
        shootAndRead();
        List<List<String>> rows =
                CsvRows.parse(api.get(BASE + "/report/summary.csv").text());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).get(0)).isEqualTo("24-0001");
    }

    @Test
    void aSoundJobHasNoBrokenTraceabilityChains() {
        shootAndRead();
        JsonNode broken = api.get(BASE + "/report/traceability").json();
        assertThat(broken.isObject()).isTrue();
        assertThat(broken).isEmpty();
    }

    @Test
    void anUnreadFilmShowsUpAsABrokenChain() {
        String number = shoot();
        JsonNode broken = api.get(BASE + "/report/traceability").json();
        assertThat(broken.has(number)).isTrue();
        assertThat(texts(broken.get(number))).anyMatch(line -> line.contains("read by"));
    }

    @Test
    void aJobThatIsNotThereIsAFourOhFour() {
        assertThat(api.get("/jobs/24-0099/report").status()).isEqualTo(404);
    }

    @Test
    void theRepairRateComesBackOnThePackage() {
        shootAndRead();
        assertThat(api.get(BASE + "/report").json().get("repair_rate").asText()).isEqualTo("0");
    }

    @Test
    void theOpenapiDocumentListsTheReportRoutes() {
        JsonNode paths = api.get("/openapi.json").json().get("paths");
        assertThat(paths.has("/jobs/{number}/report")).isTrue();
        assertThat(paths.has("/jobs/{number}/report/{sheet}.csv")).isTrue();
    }
}
