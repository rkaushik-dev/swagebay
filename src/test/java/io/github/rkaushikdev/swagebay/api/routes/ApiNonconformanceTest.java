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
class ApiNonconformanceTest {

    private static final String RAISED = "2025-07-02";
    private static final String LATER = "2025-07-08";
    private static final String MUCH_LATER = "2025-09-01";

    @Autowired
    private MockMvc mvc;

    private Http api;

    @BeforeEach
    void freshDatabase() {
        TestDatabase.fresh();
        api = new Http(mvc);
    }

    private static Map<String, Object> aReport() {
        return aReport("NCR-1", "24-0001");
    }

    private static Map<String, Object> aReport(String reference, String job) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reference", reference);
        body.put("job", job);
        body.put("item", "GW-104");
        body.put("requirement", "API 1104 9.3.8");
        body.put("account", "elongated slag 32 mm in a 300 mm length");
        body.put("raised_on", RAISED);
        body.put("raised_by", "0142");
        return body;
    }

    private static Map<String, Object> aClose(String by) {
        return Map.of("closed_on", LATER, "closed_by", by);
    }

    @Test
    void aReportIsRaisedAndReadBack() {
        Http.Answer response = api.postJson("/nonconformances", aReport());
        assertThat(response.status()).isEqualTo(201);
        JsonNode body = response.json();
        assertThat(body.get("disposition").isNull()).isTrue();
        assertThat(body.get("closed_on").isNull()).isTrue();
        assertThat(body.get("days_open").asInt()).isZero();
    }

    @Test
    void theSameReferenceTwiceIsA409() {
        api.postJson("/nonconformances", aReport());
        assertThat(api.postJson("/nonconformances", aReport()).status()).isEqualTo(409);
    }

    @Test
    void aReportWithNoAccountIsA422() {
        Map<String, Object> body = aReport();
        body.put("account", "");
        assertThat(api.postJson("/nonconformances", body).status()).isEqualTo(422);
    }

    @Test
    void aReferenceNobodyRaisedIsA404() {
        assertThat(api.get("/nonconformances/NCR-9").status()).isEqualTo(404);
    }

    @Test
    void aDecisionComesBackOnTheReport() {
        api.postJson("/nonconformances", aReport());
        JsonNode body = api.postJson("/nonconformances/NCR-1/decision", Map.of("disposition", "rework"))
                .json();
        assertThat(body.get("disposition").asText()).isEqualTo("rework");
    }

    @Test
    void useAsIsWithoutAConcessionIsA409() {
        api.postJson("/nonconformances", aReport());
        Http.Answer response = api.postJson("/nonconformances/NCR-1/decision", Map.of("disposition", "use_as_is"));
        assertThat(response.status()).isEqualTo(409);
    }

    @Test
    void useAsIsWithAConcessionIsAccepted() {
        api.postJson("/nonconformances", aReport());
        JsonNode body = api.postJson(
                        "/nonconformances/NCR-1/decision", Map.of("disposition", "use_as_is", "concession", "CON-88"))
                .json();
        assertThat(body.get("concession").asText()).isEqualTo("CON-88");
    }

    @Test
    void aDispositionNothingKnowsAboutIsA422() {
        api.postJson("/nonconformances", aReport());
        Http.Answer response = api.postJson("/nonconformances/NCR-1/decision", Map.of("disposition", "ponder"));
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.json().get("context").get("known").asText()).contains("rework");
    }

    @Test
    void closingWithoutADecisionIsA409() {
        api.postJson("/nonconformances", aReport());
        Http.Answer response = api.postJson("/nonconformances/NCR-1/closure", aClose("0303"));
        assertThat(response.status()).isEqualTo(409);
    }

    @Test
    void theAuthorClosingItIsA409() {
        api.postJson("/nonconformances", aReport());
        api.postJson("/nonconformances/NCR-1/decision", Map.of("disposition", "rework"));
        Http.Answer response = api.postJson("/nonconformances/NCR-1/closure", aClose("0142"));
        assertThat(response.status()).isEqualTo(409);
    }

    @Test
    void somebodyElseClosingItWorks() {
        api.postJson("/nonconformances", aReport());
        api.postJson("/nonconformances/NCR-1/decision", Map.of("disposition", "rework"));
        JsonNode body =
                api.postJson("/nonconformances/NCR-1/closure", aClose("0303")).json();
        assertThat(body.get("closed_by").asText()).isEqualTo("0303");
        assertThat(body.get("days_open").asInt()).isEqualTo(6);
    }

    @Test
    void theOpenListSpansJobsAndDropsTheClosedOnes() {
        api.postJson("/nonconformances", aReport("NCR-1", "24-0001"));
        api.postJson("/nonconformances", aReport("NCR-2", "24-0002"));
        api.postJson("/nonconformances/NCR-1/decision", Map.of("disposition", "rework"));
        api.postJson("/nonconformances/NCR-1/closure", aClose("0303"));
        List<String> references = new ArrayList<>();
        api.get("/nonconformances")
                .json()
                .forEach(report -> references.add(report.get("reference").asText()));
        assertThat(references).containsExactly("NCR-2");
    }

    @Test
    void overdueIsAnsweredForTheDayItIsAskedAbout() {
        api.postJson("/nonconformances", aReport());
        JsonNode soon = api.get("/nonconformances", "on", LATER).json().get(0);
        JsonNode later = api.get("/nonconformances", "on", MUCH_LATER).json().get(0);
        assertThat(soon.get("overdue").asBoolean()).isFalse();
        assertThat(later.get("overdue").asBoolean()).isTrue();
    }

    @Test
    void theOpenapiDocumentListsTheReportRoutes() {
        JsonNode paths = api.get("/openapi.json").json().get("paths");
        assertThat(paths.has("/nonconformances/{reference}/decision")).isTrue();
        assertThat(paths.has("/nonconformances/{reference}/closure")).isTrue();
    }
}
