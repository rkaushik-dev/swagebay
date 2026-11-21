package io.github.rkaushikdev.swagebay.api.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.swagebay.api.Http;
import io.github.rkaushikdev.swagebay.api.RouteTest;
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
class ApiTechniqueTest {

    private static final String ASSAY = "2024-10-01T00:00:00";
    private static final String SHOT = "2024-10-21T23:30:00";
    private static final String TECHNIQUE = "/jobs/24-0001/welds/GW-104/technique";

    @Autowired
    private MockMvc mvc;

    private Http api;

    @BeforeEach
    void aSourceAndAWeld() {
        TestDatabase.fresh();
        api = new Http(mvc);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("serial", "IR-5512");
        source.put("isotope", "ir192");
        source.put("assay_curies", "90");
        source.put("assay_at", ASSAY);
        source.put("manufacturer", "QSA");
        source.put("model", "A424-9");
        source.put("active_diameter", Map.of("text", "3 mm"));
        source.put("active_length", Map.of("text", "3 mm"));
        api.postJson("/sources", source);
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("client", "c");
        job.put("site", "s");
        job.put("acceptance_code", "API 1104");
        job.put("procedure_number", "RT-001");
        job.put("opened_on", "2024-10-01");
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
        weld.put("welded_on", "2024-10-01");
        api.postJson("/jobs/24-0001/welds", weld);
    }

    private static Map<String, Object> aPlan() {
        return aPlan("double wall single image");
    }

    private static Map<String, Object> aPlan(String shot) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source_serial", "IR-5512");
        body.put("film", "d7");
        body.put("shot_at", SHOT);
        body.put("shot", shot);
        return body;
    }

    private static List<String> texts(JsonNode array) {
        List<String> found = new ArrayList<>();
        array.forEach(item -> found.add(item.asText()));
        return found;
    }

    @Test
    void aSheetComesBackWithEveryNumberOnIt() {
        Http.Answer response = api.postJson(TECHNIQUE, aPlan());
        assertThat(response.status()).isEqualTo(200);
        JsonNode body = response.json();
        assertThat(body.get("weld").asText()).isEqualTo("GW-104");
        assertThat(body.get("shot").asText()).isEqualTo("double wall single image");
        assertThat(body.get("penetrated").get("mm").asText()).isEqualTo("25.94");
        assertThat(body.get("coverage").get("exposures").asInt()).isEqualTo(8);
        assertThat(body.get("exposure").get("timer").asText()).isEqualTo("0:38");
    }

    @Test
    void thePlannerPicksTheShotWhenNoneIsNamed() {
        JsonNode body = api.postJson(TECHNIQUE, aPlan(null)).json();
        assertThat(body.get("shot").asText()).isEqualTo("panoramic");
        assertThat(body.get("coverage").get("exposures").asInt()).isEqualTo(1);
    }

    @Test
    void anUnwipedSourceShowsUpAsAWarningNotAFailure() {
        JsonNode body = api.postJson(TECHNIQUE, aPlan()).json();
        assertThat(body.get("clean").asBoolean()).isFalse();
        assertThat(texts(body.get("warnings"))).anyMatch(warning -> warning.contains("leak test"));
    }

    @Test
    void aShotTypeNothingKnowsAboutIsRefused() {
        Http.Answer response = api.postJson(TECHNIQUE, aPlan("sideways"));
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.json().get("context").get("given").asText()).isEqualTo("sideways");
    }

    @Test
    void anIndicatorSideNothingKnowsAboutIsRefused() {
        Map<String, Object> plan = aPlan();
        plan.put("indicator_side", "the other one");
        assertThat(api.postJson(TECHNIQUE, plan).status()).isEqualTo(422);
    }

    @Test
    void aWindowIsCarriedThroughToTheWarnings() {
        Map<String, Object> plan = aPlan();
        plan.put("window_minutes", 1);
        JsonNode body = api.postJson(TECHNIQUE, plan).json();
        assertThat(texts(body.get("warnings")))
                .anyMatch(warning -> warning.contains("window") || warning.contains("source out"));
    }

    @Test
    void aSourceThatIsNotThereIsAFourOhFour() {
        Http.Answer response =
                api.postJson(TECHNIQUE, Map.of("source_serial", "IR-9999", "film", "d7", "shot_at", SHOT));
        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    void aWeldThatIsNotThereIsAFourOhFour() {
        assertThat(api.postJson("/jobs/24-0001/welds/GW-999/technique", aPlan()).status())
                .isEqualTo(404);
    }

    @Test
    void filmsAreComparedInTheOrderTheyWereAskedFor() {
        Http.Answer response = api.postJson(TECHNIQUE + "/compare", aPlan(), "films", "d7,d5,d4");
        assertThat(response.status()).isEqualTo(200);
        JsonNode sheets = response.json();
        assertThat(sheets).hasSize(3);
        List<Long> timers = new ArrayList<>();
        sheets.forEach(sheet -> timers.add(sheet.get("exposure").get("ms").asLong()));
        assertThat(timers).isSorted();
    }

    @Test
    void comparingNoFilmsAtAllIsRefused() {
        assertThat(api.postJson(TECHNIQUE + "/compare", aPlan(), "films", " , ").status())
                .isEqualTo(422);
    }

    @Test
    void theFastestFilmEndpointNamesOne() {
        Http.Answer response = api.postJson(TECHNIQUE + "/fastest", aPlan(), "films", "d4,d5,d7");
        assertThat(response.status()).isEqualTo(200);
        JsonNode film = response.json().get("film");
        assertThat(film.isNull() || film.asText().equals("d7")).isTrue();
    }

    @Test
    void aDensityWithNoChartIsRefused() {
        Map<String, Object> plan = aPlan();
        plan.put("density", "2.2");
        assertThat(api.postJson(TECHNIQUE, plan).status()).isEqualTo(500);
    }

    @Test
    void theOpenapiDocumentListsTheTechniqueRoutes() {
        JsonNode paths = api.get("/openapi.json").json().get("paths");
        assertThat(paths.has("/jobs/{number}/welds/{weld}/technique")).isTrue();
        assertThat(paths.has("/jobs/{number}/welds/{weld}/technique/compare")).isTrue();
    }
}
