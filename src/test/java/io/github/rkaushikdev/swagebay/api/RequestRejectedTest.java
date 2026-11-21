package io.github.rkaushikdev.swagebay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.swagebay.api.Schemas.LengthIn;
import io.github.rkaushikdev.swagebay.api.Schemas.ShotIn;
import io.github.rkaushikdev.swagebay.api.Schemas.SurveyIn;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The shape of a refused request: a 422 whose body is {@code {"detail": [...]}}, one entry per
 * problem with its type, location, sentence, the input refused and the limits it broke. The routes
 * only check the status; this pins what every route answers with.
 */
@RouteTest
class RequestRejectedTest {

    @Autowired
    private MockMvc mvc;

    private Http api;

    @BeforeEach
    void freshDatabase() {
        TestDatabase.fresh();
        api = new Http(mvc);
    }

    private static Map<String, Object> aReport() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reference", "NCR-1");
        body.put("job", "24-0001");
        body.put("item", "GW-104");
        body.put("requirement", "API 1104 9.3.8");
        body.put("account", "slag");
        body.put("raised_on", "2025-07-02");
        body.put("raised_by", "0142");
        return body;
    }

    private static JsonNode only(Http.Answer response) {
        assertThat(response.status()).isEqualTo(422);
        JsonNode body = response.json();
        assertThat(Http.asMap(body)).containsOnlyKeys("detail");
        assertThat(body.get("detail")).hasSize(1);
        return body.get("detail").get(0);
    }

    private static List<String> types(JsonNode detail) {
        List<String> types = new ArrayList<>();
        detail.forEach(problem -> types.add(problem.get("type").asText()));
        return types;
    }

    private Http.Answer postRaw(String text) {
        return api.send(MockMvcRequestBuilders.post("/nonconformances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(text));
    }

    @Test
    void everyProblemOnABodyIsListedInTheOrderItsFieldsAreDeclared() {
        Map<String, Object> body = aReport();
        body.put("job", "");
        body.put("requirement", "x".repeat(130));
        body.remove("account");
        Http.Answer response = api.postJson("/nonconformances", body);
        assertThat(response.status()).isEqualTo(422);
        JsonNode detail = response.json().get("detail");
        assertThat(types(detail)).containsExactly("string_too_short", "string_too_long", "missing");
        assertThat(Http.asMap(detail.get(0)))
                .containsEntry("loc", List.of("body", "job"))
                .containsEntry("msg", "String should have at least 1 character")
                .containsEntry("input", "")
                .containsEntry("ctx", Map.of("min_length", 1));
        assertThat(Http.asMap(detail.get(1)))
                .containsEntry("loc", List.of("body", "requirement"))
                .containsEntry("msg", "String should have at most 120 characters")
                .containsEntry("ctx", Map.of("max_length", 120));
        assertThat(Http.asMap(detail.get(2)))
                .containsEntry("loc", List.of("body", "account"))
                .containsEntry("msg", "Field required")
                .doesNotContainKey("ctx");
    }

    @Test
    void aMissingFieldEchoesTheObjectItIsMissingFrom() {
        Map<String, Object> body = aReport();
        body.remove("account");
        JsonNode problem = only(api.postJson("/nonconformances", body));
        assertThat(Http.asMap(problem.get("input")))
                .containsEntry("reference", "NCR-1")
                .doesNotContainKey("account");
    }

    @Test
    void aRouteSentNoBodyIsToldTheBodyIsRequired() {
        for (String nothing : List.of("", "null")) {
            JsonNode problem = only(postRaw(nothing));
            assertThat(problem.get("type").asText()).isEqualTo("missing");
            assertThat(Http.asMap(problem)).containsEntry("loc", List.of("body"));
            assertThat(problem.get("input").isNull()).isTrue();
        }
    }

    @Test
    void aBodyThatIsNotJsonIsADecodeError() {
        JsonNode problem = only(postRaw("{nope"));
        assertThat(problem.get("type").asText()).isEqualTo("json_invalid");
        assertThat(problem.get("msg").asText()).isEqualTo("JSON decode error");
        assertThat(problem.get("loc").get(0).asText()).isEqualTo("body");
        assertThat(problem.get("loc").get(1).isIntegralNumber()).isTrue();
        assertThat(Http.asMap(problem.get("input"))).isEmpty();
        assertThat(problem.get("ctx").get("error").asText()).isNotBlank();
    }

    @Test
    void aNumberWhereTextBelongsIsRefusedRatherThanRead() {
        Map<String, Object> body = aReport();
        body.put("job", 24);
        JsonNode problem = only(api.postJson("/nonconformances", body));
        assertThat(Http.asMap(problem))
                .containsEntry("type", "string_type")
                .containsEntry("loc", List.of("body", "job"))
                .containsEntry("msg", "Input should be a valid string")
                .containsEntry("input", 24);
    }

    @Test
    void aDayThatIsNoDayIsRefusedWithTheReason() {
        Map<String, Object> body = aReport();
        body.put("raised_on", "2025-13-45");
        JsonNode problem = only(api.postJson("/nonconformances", body));
        assertThat(problem.get("type").asText()).isEqualTo("date_from_datetime_parsing");
        assertThat(Http.asMap(problem)).containsEntry("loc", List.of("body", "raised_on"));
        String reason = problem.get("ctx").get("error").asText();
        assertThat(reason).isNotBlank();
        assertThat(problem.get("msg").asText()).isEqualTo("Input should be a valid date or datetime, " + reason);
    }

    @Test
    void aQueryValueOfTheWrongKindIsLocatedInTheQuery() {
        JsonNode problem = only(api.get("/nonconformances", "on", "nope"));
        assertThat(Http.asMap(problem))
                .containsEntry("type", "date_from_datetime_parsing")
                .containsEntry("loc", List.of("query", "on"))
                .containsEntry("input", "nope");
    }

    @Test
    void aQueryValueTheRouteNeedsIsRequired() {
        JsonNode problem = only(api.get("/devices/880D-221/clearance"));
        assertThat(Http.asMap(problem))
                .containsEntry("type", "missing")
                .containsEntry("loc", List.of("query", "source"))
                .containsEntry("msg", "Field required");
        assertThat(problem.get("input").isNull()).isTrue();
    }

    @Test
    void aListWhereTheBodyObjectBelongsIsRefusedAtTheBody() {
        JsonNode problem = only(postRaw("[1]"));
        assertThat(Http.asMap(problem))
                .containsEntry("type", "model_attributes_type")
                .containsEntry("loc", List.of("body"));
    }

    @Test
    void aLengthIsHeldToItsBoundsBeforeItIsParsed() {
        assertThat(rejected(new LengthIn("")).problems())
                .singleElement()
                .extracting(Problem::type)
                .isEqualTo("string_too_short");
        assertThat(rejected(new LengthIn("x".repeat(41))).problems())
                .singleElement()
                .extracting(Problem::type)
                .isEqualTo("string_too_long");
        Problem unparsed = rejected(new LengthIn("nine mm")).problems().get(0);
        assertThat(unparsed.type()).isEqualTo("value_error");
        assertThat(unparsed.loc()).containsExactly("text");
        assertThat(unparsed.msg()).isEqualTo("Value error, not a length (text='nine mm')");
        assertThat(unparsed.ctx())
                .contains(Map.of("error", Map.of("message", "not a length", "context", Map.of("text", "nine mm"))));
    }

    @Test
    void problemsInsideListsAndOptionalShapesAreLocatedThere() {
        Map<String, Object> shot = new LinkedHashMap<>();
        shot.put("exposure_index", 0);
        shot.put("exposed_at", "2025-03-06T23:30:00");
        shot.put("technique_reference", "RT-001 rev 1");
        shot.put("penetrated", Map.of("text", "nine mm"));
        shot.put("indicator", Map.of("hole_identity", 30));
        shot.put("densities", List.of(Map.of("label", "w", "density", "2.5"), Map.of("label", "", "density", "0")));
        shot.put(
                "calibration",
                Map.of("checked_at", "2025-03-06 18:00", "step_nominal", "-1", "step_read", "2.01", "instrument", "X"));
        List<Problem> problems =
                rejected(Http.JSON.convertValue(shot, ShotIn.class)).problems();
        assertThat(problems)
                .extracting(Problem::loc)
                .containsExactly(
                        List.of("exposure_index"),
                        List.of("penetrated", "text"),
                        List.of("densities", 1, "label"),
                        List.of("densities", 1, "density"),
                        List.of("calibration", "step_nominal"));
        assertThat(problems)
                .extracting(Problem::type)
                .containsExactly(
                        "greater_than_equal", "value_error", "string_too_short", "greater_than", "greater_than");
        assertThat(problems.get(0).ctx()).contains(Map.of("ge", 1L));
        assertThat(problems.get(3).ctx()).contains(Map.of("gt", 0L));
    }

    @Test
    void aPatternMismatchQuotesThePattern() {
        Map<String, Object> survey = new LinkedHashMap<>();
        survey.put("stage", "during");
        survey.put("performed_at", "2025-03-06T23:00:00");
        survey.put("performed_by", "0142");
        survey.put("instrument", "RO-20");
        survey.put("calibration_due", "2025-06-01T00:00:00");
        Problem problem = rejected(Http.JSON.convertValue(survey, SurveyIn.class))
                .problems()
                .get(0);
        assertThat(problem.type()).isEqualTo("string_pattern_mismatch");
        assertThat(problem.msg()).isEqualTo("String should match pattern '^(before|after)$'");
        assertThat(problem.ctx()).contains(Map.of("pattern", "^(before|after)$"));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "exposure_index      | 2.5        | int_from_float",
                "exposure_index      | \"two\"    | int_parsing",
                "technique_reference | 5          | string_type",
                "gamma               | \"maybe\"  | bool_parsing",
                "exposed_at          | \"noon\"   | datetime_from_date_parsing",
                "exposed_at          | 5          | datetime_type",
                "densities           | \"none\"   | tuple_type",
                "penetrated          | 5          | model_attributes_type",
            })
    void aValueOfTheWrongKindIsNamedForTheKindItShouldHaveBeen(String field, String json, String type) {
        String body = "{\"" + field + "\": " + json + "}";
        JsonMappingException unreadable =
                catchThrowableOfType(JsonMappingException.class, () -> Http.JSON.readValue(body, ShotIn.class));
        Problem problem = Problems.unreadable(unreadable);
        assertThat(problem.type()).isEqualTo(type);
        assertThat(problem.loc()).containsExactly(field);
    }

    @Test
    void theRefusalSaysWhereEachProblemIs() {
        assertThatThrownBy(() -> Requests.validated(new LengthIn("")))
                .isInstanceOf(RequestRejected.class)
                .hasMessage("the request was refused: text: String should have at least 1 character");
    }

    private static RequestRejected rejected(Object request) {
        return catchThrowableOfType(RequestRejected.class, () -> Requests.validated(request));
    }
}
