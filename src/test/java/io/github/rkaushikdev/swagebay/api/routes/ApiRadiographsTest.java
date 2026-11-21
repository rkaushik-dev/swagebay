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
class ApiRadiographsTest {

    private static final String SHOT = "2024-10-23T23:30:00";
    private static final String READ = "2024-10-24T08:00:00";
    private static final String AGAIN = "2024-10-27T09:00:00";
    private static final String BASE = "/jobs/24-0001/welds/GW-104";

    private static final List<Map<String, Object>> SLAG = List.of(Map.of(
            "kind", "ESI",
            "starts_at", Map.of("text", "100 mm"),
            "length", Map.of("text", "20 mm"),
            "width", Map.of("text", "1 mm")));
    private static final List<Map<String, Object>> POROSITY = List.of(Map.of(
            "kind", "P",
            "starts_at", Map.of("text", "100 mm"),
            "length", Map.of("text", "3 mm"),
            "width", Map.of("text", "3 mm")));

    @Autowired
    private MockMvc mvc;

    private Http api;

    @BeforeEach
    void aJobWithOneWeld() {
        TestDatabase.fresh();
        api = new Http(mvc);
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

    private static Map<String, Object> aShot() {
        return aShot(1, true, "2.50");
    }

    private static Map<String, Object> aShot(int index) {
        return aShot(index, true, "2.50");
    }

    private static Map<String, Object> aShot(int index, boolean visible, String density) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("exposure_index", index);
        body.put("exposed_at", SHOT);
        body.put("technique_reference", "RT-001 rev 1");
        body.put("medium", "film");
        body.put("film", "d7");
        body.put("penetrated", Map.of("text", "25.94 mm"));
        body.put("indicator", Map.of("hole_identity", 30, "hole_visible", visible));
        body.put(
                "densities",
                List.of(
                        Map.of("label", "indicator", "density", density, "through_indicator", true),
                        Map.of("label", "weld", "density", density)));
        body.put(
                "calibration",
                Map.of(
                        "checked_at", "2024-10-23T18:00:00",
                        "step_nominal", "2.00",
                        "step_read", "2.01",
                        "instrument", "X-Rite 301"));
        return body;
    }

    private static Map<String, Object> aReading(String badge) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reader_badge", badge);
        body.put("read_at", READ);
        return body;
    }

    private static Map<String, Object> aReread(String badge, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reader_badge", badge);
        body.put("read_at", AGAIN);
        body.put("reason", reason);
        return body;
    }

    private String shoot(Map<String, Object> shot) {
        return api.postJson(BASE + "/shots", shot).json().get("number").asText();
    }

    private String readFilm() {
        String number = shoot(aShot());
        Map<String, Object> reading = aReading("0142");
        reading.put("indications", SLAG);
        api.postJson(BASE + "/radiographs/" + number + "/reading", reading);
        return number;
    }

    private static List<String> texts(JsonNode array) {
        List<String> found = new ArrayList<>();
        array.forEach(item -> found.add(item.asText()));
        return found;
    }

    @Test
    void aShotIsRecordedAndNumbered() {
        Http.Answer response = api.postJson(BASE + "/shots", aShot());
        assertThat(response.status()).isEqualTo(201);
        JsonNode body = response.json();
        assertThat(body.get("number").asText()).isEqualTo("24-0001-R0001");
        assertThat(body.get("admissible").asBoolean()).isTrue();
        assertThat(texts(body.get("gates_run"))).containsExactly("indicator", "density");
    }

    @Test
    void anInvisibleHoleMakesTheShotInadmissible() {
        JsonNode body = api.postJson(BASE + "/shots", aShot(1, false, "2.50")).json();
        assertThat(body.get("admissible").asBoolean()).isFalse();
        assertThat(body.get("reasons").get(0).asText()).contains("not visible");
    }

    @Test
    void aThinFilmFailsOnDensity() {
        JsonNode body = api.postJson(BASE + "/shots", aShot(1, true, "1.40")).json();
        assertThat(body.get("admissible").asBoolean()).isFalse();
        assertThat(texts(body.get("reasons"))).anyMatch(reason -> reason.contains("floor"));
    }

    @Test
    void theSameExposureTwiceIsAConflict() {
        api.postJson(BASE + "/shots", aShot(1));
        assertThat(api.postJson(BASE + "/shots", aShot(1)).status()).isEqualTo(409);
    }

    @Test
    void anExposureIndexBelowOneIsRefusedByTheSchema() {
        assertThat(api.postJson(BASE + "/shots", aShot(0)).status()).isEqualTo(422);
    }

    @Test
    void aDigitalShotIsGradedOnNoiseInstead() {
        Map<String, Object> shot = aShot();
        shot.put("medium", "computed radiography");
        shot.put("film", "ip1_hd");
        shot.put("indicator", Map.of("wire_number", 11, "finest_wire_visible", 12));
        shot.put("densities", List.of());
        shot.put("mean_grey", "12000");
        shot.put("noise", "100");
        shot.put("resolution", Map.of("text", "0.040 mm"));
        JsonNode body = api.postJson(BASE + "/shots", shot).json();
        assertThat(body.get("admissible").asBoolean()).isTrue();
        assertThat(texts(body.get("gates_run")))
                .containsExactly("indicator", "normalised snr", "basic spatial resolution");
    }

    @Test
    void aShotWithNoIndicatorAtAllIsRefused() {
        Map<String, Object> shot = aShot();
        shot.put("indicator", Map.of());
        assertThat(api.postJson(BASE + "/shots", shot).status()).isEqualTo(422);
    }

    @Test
    void aTechniqueClassNothingKnowsAboutIsRefused() {
        Map<String, Object> shot = aShot();
        shot.put("technique_class", "C");
        assertThat(api.postJson(BASE + "/shots", shot).status()).isEqualTo(422);
    }

    @Test
    void aReadingIsRecordedAgainstTheFilm() {
        String number = shoot(aShot());
        Http.Answer response = api.postJson(BASE + "/radiographs/" + number + "/reading", aReading("0142"));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("read_by").asText()).isEqualTo("0142");
    }

    @Test
    void indicationsAreRecordedWithTheReading() {
        String number = shoot(aShot());
        Map<String, Object> reading = aReading("0142");
        reading.put(
                "indications",
                List.of(Map.of(
                        "kind", "ESI",
                        "starts_at", Map.of("text", "100 mm"),
                        "length", Map.of("text", "20 mm"),
                        "width", Map.of("text", "1 mm"),
                        "clock", "3:00")));
        Http.Answer response = api.postJson(BASE + "/radiographs/" + number + "/reading", reading);
        assertThat(response.status()).isEqualTo(200);
    }

    @Test
    void aDiscontinuityCodeNothingKnowsAboutIsRefused() {
        String number = shoot(aShot());
        Map<String, Object> reading = aReading("0142");
        reading.put(
                "indications",
                List.of(Map.of(
                        "kind", "ZZ", "starts_at", Map.of("text", "100 mm"), "length", Map.of("text", "20 mm"))));
        Http.Answer response = api.postJson(BASE + "/radiographs/" + number + "/reading", reading);
        assertThat(response.status()).isEqualTo(422);
    }

    @Test
    void aFilmCannotBeReadTwice() {
        String number = shoot(aShot());
        api.postJson(BASE + "/radiographs/" + number + "/reading", aReading("0142"));
        assertThat(api.postJson(BASE + "/radiographs/" + number + "/reading", aReading("0142"))
                        .status())
                .isEqualTo(409);
    }

    @Test
    void anInadmissibleFilmCannotBeRead() {
        String number = shoot(aShot(1, false, "2.50"));
        Http.Answer response = api.postJson(BASE + "/radiographs/" + number + "/reading", aReading("0142"));
        assertThat(response.status()).isEqualTo(409);
    }

    @Test
    void aFilmThatDoesNotExistIsAFourOhFour() {
        Http.Answer response = api.postJson(BASE + "/radiographs/24-0001-R9999/reading", aReading("0142"));
        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    void theOpenapiDocumentListsTheRadiographRoutes() {
        JsonNode paths = api.get("/openapi.json").json().get("paths");
        assertThat(paths.has("/jobs/{number}/welds/{weld}/shots")).isTrue();
    }

    @Test
    void aRereadNamesBothReaders() {
        String number = readFilm();
        Map<String, Object> reread = aReread("0201", "client asked");
        reread.put("indications", POROSITY);
        Http.Answer response = api.postJson(BASE + "/radiographs/" + number + "/reread", reread);
        assertThat(response.status()).isEqualTo(200);
        assertThat(texts(response.json().get("readers"))).containsExactly("0142", "0201");
        assertThat(response.json().get("changed").isBoolean()).isTrue();
        assertThat(response.json().get("changed").asBoolean()).isTrue();
    }

    @Test
    void aRereadThatAgreesReportsNoChange() {
        String number = readFilm();
        Map<String, Object> reread = aReread("0201", "second look");
        reread.put("indications", SLAG);
        JsonNode body = api.postJson(BASE + "/radiographs/" + number + "/reread", reread)
                .json();
        assertThat(body.get("changed").isBoolean()).isTrue();
        assertThat(body.get("changed").asBoolean()).isFalse();
    }

    @Test
    void aRereadWithNoReasonIsRefusedByTheSchema() {
        String number = readFilm();
        Http.Answer response = api.postJson(BASE + "/radiographs/" + number + "/reread", aReread("0201", ""));
        assertThat(response.status()).isEqualTo(422);
    }

    @Test
    void aRereadByTheFirstReaderIsAConflict() {
        String number = readFilm();
        Http.Answer response = api.postJson(BASE + "/radiographs/" + number + "/reread", aReread("0142", "why"));
        assertThat(response.status()).isEqualTo(409);
    }

    @Test
    void aRereadOfAnUnreadFilmIsAConflict() {
        String number = shoot(aShot());
        Http.Answer response = api.postJson(BASE + "/radiographs/" + number + "/reread", aReread("0201", "why"));
        assertThat(response.status()).isEqualTo(409);
    }

    @Test
    void theHistoryOfAFilmNobodyArguedAboutIsEmpty() {
        String number = readFilm();
        Http.Answer response = api.get(BASE + "/radiographs/" + number + "/history");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("entries")).isEmpty();
        assertThat(response.json().get("after_signing").asInt()).isZero();
    }

    @Test
    void theHistoryCarriesWhatTheFirstReaderSaid() {
        String number = readFilm();
        Map<String, Object> reread = aReread("0201", "client asked");
        reread.put("indications", POROSITY);
        api.postJson(BASE + "/radiographs/" + number + "/reread", reread);
        JsonNode body = api.get(BASE + "/radiographs/" + number + "/history").json();
        assertThat(body.get("entries")).hasSize(1);
        JsonNode entry = body.get("entries").get(0);
        assertThat(entry.get("action").asText()).isEqualTo("changed");
        assertThat(entry.get("was").asText()).startsWith("ESI");
        assertThat(entry.get("reason").asText()).isEqualTo("client asked");
    }

    @Test
    void theHistoryOfAFilmThatDoesNotExistIsA404() {
        assertThat(api.get(BASE + "/radiographs/24-0001-R9999/history").status())
                .isEqualTo(404);
    }
}
