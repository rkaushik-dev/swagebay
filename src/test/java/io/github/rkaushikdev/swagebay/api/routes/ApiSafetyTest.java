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
class ApiSafetyTest {

    private static final String WHEN = "2024-12-05T23:30:00";
    private static final String DUE = "2025-06-01T00:00:00";
    private static final String SURVEYS = "/safety/radiographs/R-001/surveys";

    private static final Map<String, Object> AT_THE_BOUNDARY =
            Map.of("label", "boundary", "distance", Map.of("text", "140 m"), "mr_per_hour", "2");

    @Autowired
    private MockMvc mvc;

    private Http api;

    @BeforeEach
    void freshDatabase() {
        TestDatabase.fresh();
        api = new Http(mvc);
    }

    private static Map<String, Object> aField() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("isotope", "ir192");
        body.put("curies", "74");
        return body;
    }

    private static Map<String, Object> aSurvey(String stage) {
        return aSurvey(
                stage,
                List.of(
                        AT_THE_BOUNDARY,
                        Map.of(
                                "label",
                                "device",
                                "distance",
                                Map.of("text", "1 m"),
                                "mr_per_hour",
                                "1",
                                "at_device_surface",
                                true)));
    }

    private static Map<String, Object> aSurvey(String stage, List<Map<String, Object>> points) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stage", stage);
        body.put("performed_at", WHEN);
        body.put("performed_by", "0142");
        body.put("instrument", "Ludlum 3");
        body.put("calibration_due", DUE);
        body.put("points", points);
        return body;
    }

    private static List<String> texts(JsonNode array) {
        List<String> found = new ArrayList<>();
        array.forEach(item -> found.add(item.asText()));
        return found;
    }

    @Test
    void theBoundariesComeBackForEveryPosting() {
        JsonNode body = api.postJson("/safety/boundaries", aField()).json();
        List<String> kinds = new ArrayList<>();
        body.get("boundaries")
                .forEach(boundary -> kinds.add(boundary.get("kind").asText()));
        assertThat(kinds).containsExactly("restricted area", "radiation area", "high radiation area");
    }

    @Test
    void aBareSourceIsNotDirectional() {
        JsonNode body = api.postJson("/safety/boundaries", aField()).json();
        assertThat(body.get("directional").asBoolean()).isFalse();
        JsonNode first = body.get("boundaries").get(0);
        assertThat(first.get("down_beam")).isEqualTo(first.get("off_beam"));
    }

    @Test
    void aCollimatorMakesItDirectional() {
        Map<String, Object> field = aField();
        field.put("collimator", "w60");
        JsonNode body = api.postJson("/safety/boundaries", field).json();
        assertThat(body.get("directional").asBoolean()).isTrue();
        JsonNode first = body.get("boundaries").get(0);
        assertThat(first.get("off_beam").get("um").asLong())
                .isLessThan(first.get("down_beam").get("um").asLong());
    }

    @Test
    void aBarrierPullsEveryBoundaryIn() {
        JsonNode bare = api.postJson("/safety/boundaries", aField()).json().get("boundaries");
        Map<String, Object> field = aField();
        field.put("barrier_material", "lead");
        field.put("barrier_thickness", Map.of("text", "30 mm"));
        JsonNode shielded = api.postJson("/safety/boundaries", field).json().get("boundaries");
        assertThat(shielded).hasSameSizeAs(bare);
        for (int at = 0; at < bare.size(); at++) {
            assertThat(shielded.get(at).get("down_beam").get("um").asLong())
                    .isLessThan(bare.get(at).get("down_beam").get("um").asLong());
        }
    }

    @Test
    void aBarrierWithNoThicknessIsRefused() {
        Map<String, Object> field = aField();
        field.put("barrier_material", "lead");
        assertThat(api.postJson("/safety/boundaries", field).status()).isEqualTo(422);
    }

    @Test
    void anIsotopeOrCollimatorNothingKnowsAboutIsRefused() {
        Map<String, Object> collimated = aField();
        collimated.put("collimator", "w99");
        assertThat(api.postJson("/safety/boundaries", collimated).status()).isEqualTo(500);
        Map<String, Object> radium = aField();
        radium.put("isotope", "ra226");
        assertThat(api.postJson("/safety/boundaries", radium).status()).isEqualTo(500);
    }

    @Test
    void aSourceOfNothingIsRefusedByTheSchema() {
        Map<String, Object> field = aField();
        field.put("curies", "0");
        assertThat(api.postJson("/safety/boundaries", field).status()).isEqualTo(422);
    }

    @Test
    void aSoundSurveyComesBackClear() {
        Http.Answer response = api.postJson(SURVEYS, aSurvey("after"));
        assertThat(response.status()).isEqualTo(201);
        JsonNode body = response.json();
        assertThat(body.get("clear").asBoolean()).isTrue();
        assertThat(body.get("faults")).isEmpty();
        assertThat(body.get("hottest_label").asText()).isEqualTo("boundary");
    }

    @Test
    void aSurveyWithAHotDeviceIsStoredAndReported() {
        List<Map<String, Object>> points = List.of(Map.of(
                "label", "device", "distance", Map.of("text", "1 m"), "mr_per_hour", "900", "at_device_surface", true));
        JsonNode body = api.postJson(SURVEYS, aSurvey("after", points)).json();
        assertThat(body.get("clear").asBoolean()).isFalse();
        assertThat(body.get("faults").get(0).asText()).contains("not fully retracted");
    }

    @Test
    void anAfterSurveyWithNoSurfaceReadingIsAFault() {
        JsonNode body = api.postJson(SURVEYS, aSurvey("after", List.of(AT_THE_BOUNDARY)))
                .json();
        assertThat(texts(body.get("faults"))).contains("no reading was taken at the device surface");
    }

    @Test
    void aBeforeSurveyNeedsNoSurfaceReading() {
        JsonNode body = api.postJson(SURVEYS, aSurvey("before", List.of(AT_THE_BOUNDARY)))
                .json();
        assertThat(body.get("clear").asBoolean()).isTrue();
    }

    @Test
    void aStageNobodyPublishesIsRefusedByTheSchema() {
        assertThat(api.postJson(SURVEYS, aSurvey("during")).status()).isEqualTo(422);
    }

    @Test
    void aSurveyWithNoReadingsIsRefused() {
        assertThat(api.postJson(SURVEYS, aSurvey("after", List.of())).status()).isEqualTo(422);
    }

    @Test
    void theOpenapiDocumentListsTheSafetyRoutes() {
        JsonNode paths = api.get("/openapi.json").json().get("paths");
        assertThat(paths.has("/safety/boundaries")).isTrue();
        assertThat(paths.has("/safety/radiographs/{radiograph}/surveys")).isTrue();
    }
}
