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
class ApiJobsTest {

    private static final String OPENED = "2024-09-30";

    @Autowired
    private MockMvc mvc;

    private Http api;

    @BeforeEach
    void freshDatabase() {
        TestDatabase.fresh();
        api = new Http(mvc);
    }

    private static Map<String, Object> aJob() {
        return aJob("Maritime Pipelines", "API 1104");
    }

    private static Map<String, Object> aJob(String client, String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client", client);
        body.put("site", "Come By Chance");
        body.put("acceptance_code", code);
        body.put("procedure_number", "RT-001");
        body.put("opened_on", OPENED);
        return body;
    }

    private static Map<String, Object> aJoint() {
        Map<String, Object> joint = new LinkedHashMap<>();
        joint.put("material", "carbon_steel");
        joint.put("wall", Map.of("text", "10.97 mm"));
        joint.put("outside_diameter", Map.of("text", "168.3 mm"));
        joint.put("reinforcement", Map.of("text", "2 mm"));
        return joint;
    }

    private static Map<String, Object> aWeld(String weld) {
        return aWeld(weld, aJoint());
    }

    private static Map<String, Object> aWeld(String weld, Map<String, Object> joint) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("weld", weld);
        body.put("joint", joint);
        body.put("weld_length", Map.of("text", "529 mm"));
        body.put("welder", "w-19");
        body.put("welded_on", OPENED);
        return body;
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> found = new ArrayList<>();
        array.forEach(item -> found.add(item.get(field).asText()));
        return found;
    }

    @Test
    void aJobIsCreatedAndNumbered() {
        Http.Answer response = api.postJson("/jobs", aJob());
        assertThat(response.status()).isEqualTo(201);
        JsonNode body = response.json();
        assertThat(body.get("number").asText()).isEqualTo("24-0001");
        assertThat(body.get("welds").asInt()).isZero();
        assertThat(body.get("closed_on").isNull()).isTrue();
    }

    @Test
    void theNumbersRunOn() {
        JsonNode first = api.postJson("/jobs", aJob()).json();
        JsonNode second = api.postJson("/jobs", aJob("Other", "API 1104")).json();
        assertThat(first.get("number").asText()).isEqualTo("24-0001");
        assertThat(second.get("number").asText()).isEqualTo("24-0002");
    }

    @Test
    void anAcceptanceCodeNothingImplementsIsRefused() {
        Http.Answer response = api.postJson("/jobs", aJob("Maritime Pipelines", "BS 2600"));
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.json().get("code").asText()).isEqualTo("qi.validation");
        assertThat(response.json().get("context").get("given").asText()).isEqualTo("BS 2600");
    }

    @Test
    void aJobIsReadBackByItsNumber() {
        api.postJson("/jobs", aJob());
        Http.Answer response = api.get("/jobs/24-0001");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("client").asText()).isEqualTo("Maritime Pipelines");
    }

    @Test
    void aJobThatIsNotThereIsAFourOhFour() {
        Http.Answer response = api.get("/jobs/24-0099");
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.json().get("code").asText()).isEqualTo("qi.not_found");
    }

    @Test
    void aMalformedJobNumberIsRefusedWithTheGrammar() {
        Http.Answer response = api.get("/jobs/nonsense");
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.json().get("code").asText()).isEqualTo("qi.validation");
        assertThat(response.json().get("context").get("text").asText()).isEqualTo("nonsense");
    }

    @Test
    void theOpenJobsAreListedForADay() {
        api.postJson("/jobs", aJob());
        JsonNode open = api.get("/jobs", "on", "2024-10-01").json();
        assertThat(texts(open, "number")).containsExactly("24-0001");
        assertThat(api.get("/jobs", "on", "2024-01-01").json()).isEmpty();
    }

    @Test
    void aWeldIsAddedToAJob() {
        api.postJson("/jobs", aJob());
        Http.Answer response = api.postJson("/jobs/24-0001/welds", aWeld("GW-104"));
        assertThat(response.status()).isEqualTo(201);
        JsonNode body = response.json();
        assertThat(body.get("weld").asText()).isEqualTo("GW-104");
        assertThat(body.get("wall").get("mm").asText()).isEqualTo("10.97");
        assertThat(body.get("outside_diameter").get("mm").asText()).isEqualTo("168.3");
        assertThat(body.get("radiographs").asInt()).isZero();
    }

    @Test
    void aPlateWeldComesBackWithNoDiameter() {
        api.postJson("/jobs", aJob());
        Map<String, Object> joint = aJoint();
        joint.put("outside_diameter", null);
        Http.Answer response = api.postJson("/jobs/24-0001/welds", aWeld("GW-200", joint));
        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json().get("outside_diameter").isNull()).isTrue();
    }

    @Test
    void theSameWeldTwiceIsAConflict() {
        api.postJson("/jobs", aJob());
        api.postJson("/jobs/24-0001/welds", aWeld("GW-104"));
        Http.Answer response = api.postJson("/jobs/24-0001/welds", aWeld("GW-104"));
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.json().get("code").asText()).isEqualTo("qi.conflict");
    }

    @Test
    void aWallThatWillNotParseIsRefusedByTheSchema() {
        api.postJson("/jobs", aJob());
        Map<String, Object> joint = aJoint();
        joint.put("wall", Map.of("text", "ten mm"));
        assertThat(api.postJson("/jobs/24-0001/welds", aWeld("GW-104", joint)).status())
                .isEqualTo(422);
    }

    @Test
    void theWeldsOnAJobAreListedInOrder() {
        api.postJson("/jobs", aJob());
        for (String weld : List.of("GW-110", "GW-104", "GW-104R1")) {
            api.postJson("/jobs/24-0001/welds", aWeld(weld));
        }
        assertThat(texts(api.get("/jobs/24-0001/welds").json(), "weld"))
                .containsExactly("GW-104", "GW-104R1", "GW-110");
    }

    @Test
    void theOutstandingListSaysWhatIsMissing() {
        api.postJson("/jobs", aJob());
        JsonNode body = api.get("/jobs/24-0001/outstanding").json();
        assertThat(body.get("job").asText()).isEqualTo("24-0001");
        List<String> items = new ArrayList<>();
        body.get("items").forEach(item -> items.add(item.asText()));
        assertThat(items).contains("the job has no welds on it");
    }

    @Test
    void aJobWithNothingDoneRefusesToClose() {
        api.postJson("/jobs", aJob());
        Http.Answer response = api.post("/jobs/24-0001/close", "on", "2024-10-05");
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.json().get("context").get("outstanding").asInt()).isEqualTo(1);
    }

    @Test
    void theJobCountOfWeldsFollowsWhatWasAdded() {
        api.postJson("/jobs", aJob());
        api.postJson("/jobs/24-0001/welds", aWeld("GW-104"));
        assertThat(api.get("/jobs/24-0001").json().get("welds").asInt()).isEqualTo(1);
    }

    @Test
    void theOpenapiDocumentListsTheJobRoutes() {
        JsonNode paths = api.get("/openapi.json").json().get("paths");
        assertThat(paths.has("/jobs")).isTrue();
        assertThat(paths.has("/jobs/{number}/welds")).isTrue();
        assertThat(paths.get("/jobs").has("post")).isTrue();
    }
}
