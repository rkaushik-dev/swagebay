package io.github.rkaushikdev.swagebay.api.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.swagebay.api.Http;
import io.github.rkaushikdev.swagebay.api.RouteTest;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@RouteTest
class ApiPagingTest {

    private static final String SHOT = "2025-03-06T23:30:00";
    private static final String BASE = "/jobs/25-0001";

    @Autowired
    private MockMvc mvc;

    private Http api;

    private static Map<String, Object> aShot(int index) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("exposure_index", index);
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
                        "checked_at", "2025-03-06T18:00:00",
                        "step_nominal", "2.00",
                        "step_read", "2.01",
                        "instrument", "X-Rite 301"));
        return body;
    }

    @BeforeEach
    void twelveFilmsOnOneWeld() {
        TestDatabase.fresh();
        api = new Http(mvc);
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("client", "c");
        job.put("site", "s");
        job.put("acceptance_code", "API 1104");
        job.put("procedure_number", "RT-001");
        job.put("opened_on", "2025-03-01");
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
        weld.put("welded_on", "2025-03-01");
        api.postJson(BASE + "/welds", weld);
        for (int index = 1; index <= 12; index++) {
            api.postJson(BASE + "/welds/GW-104/shots", aShot(index));
        }
    }

    private static List<String> numbers(JsonNode body) {
        List<String> numbers = new ArrayList<>();
        body.get("items").forEach(item -> numbers.add(item.get("number").asText()));
        return numbers;
    }

    @Test
    void aShortListingComesBackAsOneLastPage() {
        JsonNode body = api.get(BASE + "/radiographs", "size", "50").json();
        assertThat(body.get("items")).hasSize(12);
        assertThat(body.get("last").asBoolean()).isTrue();
        assertThat(body.get("next_cursor").isNull()).isTrue();
    }

    @Test
    void aLongListingIsCutAndCarriesACursor() {
        JsonNode body = api.get(BASE + "/radiographs", "size", "5").json();
        assertThat(body.get("items")).hasSize(5);
        assertThat(body.get("last").asBoolean()).isFalse();
        assertThat(body.get("next_cursor").asText())
                .isEqualTo(body.get("items").get(4).get("number").asText());
    }

    @Test
    void theCursorWalksTheWholeListingWithoutRepeating() {
        List<String> seen = new ArrayList<>();
        String cursor = null;
        while (true) {
            JsonNode body = cursor == null
                    ? api.get(BASE + "/radiographs", "size", "5").json()
                    : api.get(BASE + "/radiographs", "size", "5", "cursor", cursor)
                            .json();
            seen.addAll(numbers(body));
            if (body.get("last").asBoolean()) {
                break;
            }
            cursor = body.get("next_cursor").asText();
        }
        assertThat(seen).hasSize(12);
        assertThat(new HashSet<>(seen)).hasSize(12);
        assertThat(seen).isSorted();
    }

    @Test
    void aBlankCursorStartsAtTheBeginning() {
        JsonNode first = api.get(BASE + "/radiographs", "size", "3").json();
        JsonNode blank =
                api.get(BASE + "/radiographs", "size", "3", "cursor", "  ").json();
        assertThat(blank.get("items")).isEqualTo(first.get("items"));
    }

    @Test
    void aPageSizeBelowOneIsRefused() {
        assertThat(api.get(BASE + "/radiographs", "size", "0").status()).isEqualTo(422);
    }

    @Test
    void aPageSizeOverTheCeilingIsClampedRatherThanRefused() {
        JsonNode body = api.get(BASE + "/radiographs", "size", "100000").json();
        assertThat(body.get("last").asBoolean()).isTrue();
        assertThat(body.get("items")).hasSize(12);
    }

    @Test
    void theListingCarriesTheWeldEachFilmBelongsTo() {
        JsonNode body = api.get(BASE + "/radiographs", "size", "2").json();
        assertThat(body.get("items"))
                .allSatisfy(item -> assertThat(item.get("weld").asText()).isEqualTo("GW-104"));
        assertThat(body.get("items").get(0).get("admissible").asBoolean()).isTrue();
    }

    @Test
    void aJobThatIsNotThereIsAFourOhFour() {
        assertThat(api.get("/jobs/25-0099/radiographs").status()).isEqualTo(404);
    }

    @Test
    void theOpenapiDocumentListsThePagedRoute() {
        assertThat(api.get("/openapi.json").json().get("paths").has("/jobs/{number}/radiographs"))
                .isTrue();
    }
}
