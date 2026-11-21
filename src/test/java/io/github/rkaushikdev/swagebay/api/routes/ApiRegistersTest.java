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
class ApiRegistersTest {

    private static final String FOUND = "2025-04-09T23:30:00";

    @Autowired
    private MockMvc mvc;

    private Http api;

    @BeforeEach
    void freshDatabase() {
        TestDatabase.fresh();
        api = new Http(mvc);
    }

    private static Map<String, Object> anIncident() {
        return anIncident("INC-1", "OVEREXPOSURE");
    }

    private static Map<String, Object> anIncident(String reference, String kind) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reference", reference);
        body.put("occurrence", kind);
        body.put("discovered_at", FOUND);
        body.put("site", "Come By Chance");
        body.put("reported_by", "0142");
        body.put("account", "the source hung up");
        return body;
    }

    private static Map<String, Object> aCalibration() {
        return aCalibration("2026-04-09");
    }

    private static Map<String, Object> aCalibration(String due) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serial", "LU-1");
        body.put("instrument", "SURVEY_METER");
        body.put("calibrated_on", "2025-04-09");
        body.put("due_on", due);
        body.put("laboratory", "NRC");
        body.put("certificate", "C-1");
        return body;
    }

    private static List<String> texts(JsonNode array) {
        List<String> found = new ArrayList<>();
        array.forEach(item -> found.add(item.asText()));
        return found;
    }

    @Test
    void anIncidentComesBackWithTheDeadlineItComputed() {
        Http.Answer response = api.postJson("/registers/incidents", anIncident());
        assertThat(response.status()).isEqualTo(201);
        JsonNode body = response.json();
        assertThat(body.get("reference").asText()).isEqualTo("INC-1");
        assertThat(body.get("due_at").asText()).isEqualTo("2025-04-10T23:30:00");
        assertThat(body.get("notified_at").isNull()).isTrue();
    }

    @Test
    void aStuckSourceGetsTheHourDeadline() {
        JsonNode body = api.postJson("/registers/incidents", anIncident("INC-2", "SOURCE_DISCONNECTED"))
                .json();
        assertThat(body.get("due_at").asText()).isEqualTo("2025-04-10T00:30:00");
    }

    @Test
    void theOccurrenceMayBeNamedByItsWordsToo() {
        JsonNode body = api.postJson("/registers/incidents", anIncident("INC-3", "dose over a limit"))
                .json();
        assertThat(body.get("due_at").asText()).isEqualTo("2025-04-10T23:30:00");
    }

    @Test
    void anOccurrenceNobodyPublishesIsRefused() {
        Http.Answer response = api.postJson("/registers/incidents", anIncident("INC-4", "a bad day"));
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.json().get("context").get("known").asText()).contains("SOURCE_DISCONNECTED");
    }

    @Test
    void theSameReferenceTwiceIsAConflict() {
        api.postJson("/registers/incidents", anIncident());
        assertThat(api.postJson("/registers/incidents", anIncident()).status()).isEqualTo(409);
    }

    @Test
    void theUnreportedListEmptiesWhenOneIsNotified() {
        api.postJson("/registers/incidents", anIncident());
        assertThat(api.get("/registers/incidents").json()).hasSize(1);
        api.post("/registers/incidents/INC-1/notify", "at", FOUND);
        assertThat(api.get("/registers/incidents").json()).isEmpty();
    }

    @Test
    void reportingLateIsReportedAsLate() {
        api.postJson("/registers/incidents", anIncident());
        JsonNode body = api.post("/registers/incidents/INC-1/notify", "at", "2025-04-12T00:00:00")
                .json();
        assertThat(body.get("late").asBoolean()).isTrue();
        assertThat(body.get("notified_at").asText()).isEqualTo("2025-04-12T00:00:00");
    }

    @Test
    void reportingInTimeIsNotLate() {
        api.postJson("/registers/incidents", anIncident());
        JsonNode body = api.post("/registers/incidents/INC-1/notify", "at", "2025-04-10T09:00:00")
                .json();
        assertThat(body.get("late").asBoolean()).isFalse();
    }

    @Test
    void anIncidentThatIsNotThereIsAFourOhFour() {
        assertThat(api.get("/registers/incidents/INC-9").status()).isEqualTo(404);
        assertThat(api.post("/registers/incidents/INC-9/notify", "at", FOUND).status())
                .isEqualTo(404);
    }

    @Test
    void aCalibrationIsRecordedAndReadBack() {
        Http.Answer response = api.postJson("/registers/calibrations", aCalibration());
        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json().get("due_on").asText()).isEqualTo("2026-04-09");
        assertThat(api.get("/registers/calibrations/LU-1")
                        .json()
                        .get("laboratory")
                        .asText())
                .isEqualTo("NRC");
    }

    @Test
    void anInstrumentNeverCalibratedComesBackEmpty() {
        Http.Answer response = api.get("/registers/calibrations/LU-9");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().isNull()).isTrue();
    }

    @Test
    void anInstrumentNobodyPublishesIsRefused() {
        Map<String, Object> body = aCalibration();
        body.put("instrument", "divining rod");
        assertThat(api.postJson("/registers/calibrations", body).status()).isEqualTo(422);
    }

    @Test
    void aCertificateThatExpiresBeforeItWasIssuedIsRefused() {
        assertThat(api.postJson("/registers/calibrations", aCalibration("2025-04-01"))
                        .status())
                .isIn(409, 422, 500);
    }

    @Test
    void theExpiredListAnswersForADay() {
        api.postJson("/registers/calibrations", aCalibration());
        assertThat(api.get("/registers/calibrations/expired", "on", "2025-05-01")
                        .json())
                .isEmpty();
        assertThat(texts(api.get("/registers/calibrations/expired", "on", "2026-05-01")
                        .json()))
                .containsExactly("LU-1");
    }

    @Test
    void theOpenapiDocumentListsTheRegisterRoutes() {
        JsonNode paths = api.get("/openapi.json").json().get("paths");
        assertThat(paths.has("/registers/incidents")).isTrue();
        assertThat(paths.has("/registers/calibrations")).isTrue();
    }
}
