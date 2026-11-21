package io.github.rkaushikdev.swagebay.api.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.swagebay.api.Http;
import io.github.rkaushikdev.swagebay.api.RouteTest;
import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.services.SourceService;
import io.github.rkaushikdev.swagebay.services.WipeService;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** The source routes, and the device routes that decide whether a source may go out in one. */
@RouteTest
class ApiSourcesTest {

    private static final String ASSAY = "2024-10-01T00:00:00";
    private static final String DEVICE = "880D-221";

    @Autowired
    private MockMvc mvc;

    private Database database;
    private Http api;

    @BeforeEach
    void aDeviceOnTheInventory() {
        database = TestDatabase.fresh();
        database.transaction(work -> SourceService.registerDevice(work, DEVICE, "880d"));
        api = new Http(mvc);
    }

    private static Map<String, Object> aSource() {
        return aSource("IR-5512", "ir192");
    }

    private static Map<String, Object> aSource(String serial, String isotope) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serial", serial);
        body.put("isotope", isotope);
        body.put("assay_curies", "90");
        body.put("assay_at", ASSAY);
        body.put("manufacturer", "QSA");
        body.put("model", "A424-9");
        body.put("active_diameter", Map.of("text", "3 mm"));
        body.put("active_length", Map.of("text", "3.5 mm"));
        return body;
    }

    /** Both checks, so the dispatch gate lets the load out. */
    private void inspected() {
        api.postJson(
                "/devices/" + DEVICE + "/inspections",
                Map.of("performed_on", "2024-09-20", "performed_by", "0303", "is_quarterly", true));
        api.postJson(
                "/devices/" + DEVICE + "/inspections", Map.of("performed_on", "2024-10-08", "performed_by", "0142"));
    }

    /** A current wipe test, written straight to the register because no route records one. */
    private void wiped() {
        database.transaction(work ->
                WipeService.recordWipe(work, "IR-5512", LocalDateTime.of(2024, 10, 1, 0, 0), BigDecimal.ZERO, "rp"));
    }

    private static Map<String, Object> aBooking() {
        return aBooking("Come By Chance");
    }

    private static Map<String, Object> aBooking(String site) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("device_serial", DEVICE);
        body.put("crew_badge", "0142");
        body.put("site", site);
        body.put("went_out", "2024-10-08");
        return body;
    }

    private static List<String> texts(JsonNode array) {
        List<String> found = new ArrayList<>();
        array.forEach(item -> found.add(item.asText()));
        return found;
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> found = new ArrayList<>();
        array.forEach(item -> found.add(item.get(field).asText()));
        return found;
    }

    @Test
    void aSourceIsRegisteredAndReadBack() {
        Http.Answer response = api.postJson("/sources", aSource());
        assertThat(response.status()).isEqualTo(201);
        JsonNode body = response.json();
        assertThat(body.get("serial").asText()).isEqualTo("IR-5512");
        assertThat(body.get("assay_activity").get("curie").asText()).isEqualTo("90");
        assertThat(body.get("state").asText()).isEqualTo("in service");
        assertThat(body.get("location").asText()).isEqualTo("shop");
    }

    @Test
    void theFocalSizeIsTheLargerPelletDimension() {
        api.postJson("/sources", aSource());
        assertThat(api.get("/sources/IR-5512")
                        .json()
                        .get("focal_size")
                        .get("mm")
                        .asText())
                .isEqualTo("3.5");
    }

    @Test
    void theSameSerialTwiceIsAConflict() {
        api.postJson("/sources", aSource());
        assertThat(api.postJson("/sources", aSource()).status()).isEqualTo(409);
    }

    @Test
    void anIsotopeNothingKnowsAboutIsRefused() {
        Http.Answer response = api.postJson("/sources", aSource("IR-5512", "ra226"));
        assertThat(response.status()).isEqualTo(500);
        assertThat(response.json().get("code").asText()).isEqualTo("qi.configuration");
    }

    @Test
    void anActivityOfNothingIsRefusedByTheSchema() {
        Map<String, Object> body = aSource();
        body.put("assay_curies", "0");
        assertThat(api.postJson("/sources", body).status()).isEqualTo(422);
    }

    @Test
    void aSourceThatIsNotThereIsAFourOhFour() {
        assertThat(api.get("/sources/IR-9999").status()).isEqualTo(404);
    }

    @Test
    void theSourcesInServiceAreListed() {
        api.postJson("/sources", aSource("IR-5512", "ir192"));
        api.postJson("/sources", aSource("IR-6000", "ir192"));
        assertThat(texts(api.get("/sources").json(), "serial")).containsExactly("IR-5512", "IR-6000");
    }

    @Test
    void aSourceBookedOutReadsAsTheSite() {
        api.postJson("/sources", aSource());
        wiped();
        inspected();
        Http.Answer response = api.postJson("/sources/IR-5512/send-out", aBooking());
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("location").asText()).isEqualTo("Come By Chance");
    }

    @Test
    void aSourceCannotGoOutTwice() {
        api.postJson("/sources", aSource());
        api.postJson("/sources/IR-5512/send-out", aBooking());
        assertThat(api.postJson("/sources/IR-5512/send-out", aBooking("Elsewhere"))
                        .status())
                .isEqualTo(409);
    }

    @Test
    void aDeviceThatIsNotOnTheInventoryIsAFourOhFour() {
        api.postJson("/sources", aSource());
        Map<String, Object> booking = aBooking();
        booking.put("device_serial", "880D-999");
        assertThat(api.postJson("/sources/IR-5512/send-out", booking).status()).isEqualTo(404);
    }

    @Test
    void bringingASourceBackPutsItInTheShop() {
        api.postJson("/sources", aSource());
        wiped();
        inspected();
        api.postJson("/sources/IR-5512/send-out", aBooking());
        Http.Answer response =
                api.postJson("/sources/IR-5512/bring-back", Map.of("came_back", "2024-10-10", "exposures", 24));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("location").asText()).isEqualTo("shop");
    }

    @Test
    void aReturnBeforeTheDepartureIsRefused() {
        api.postJson("/sources", aSource());
        wiped();
        inspected();
        api.postJson("/sources/IR-5512/send-out", aBooking());
        Http.Answer response =
                api.postJson("/sources/IR-5512/bring-back", Map.of("came_back", "2024-10-01", "exposures", 0));
        assertThat(response.status()).isEqualTo(422);
    }

    @Test
    void fitnessIsAnsweredForTheMomentItIsAskedAbout() {
        api.postJson("/sources", aSource());
        JsonNode soon =
                api.get("/sources/IR-5512/fitness", "at", "2024-10-08T22:00:00").json();
        JsonNode later =
                api.get("/sources/IR-5512/fitness", "at", "2026-10-08T22:00:00").json();
        assertThat(texts(soon.get("reasons"))).containsExactly("leak test is not current");
        assertThat(texts(later.get("reasons"))).contains("activity is below the working floor");
        assertThat(later.get("reasons").size())
                .isGreaterThan(soon.get("reasons").size());
    }

    @Test
    void anUnwipedSourceIsReportedUnfitWithTheReason() {
        api.postJson("/sources", aSource());
        JsonNode body =
                api.get("/sources/IR-5512/fitness", "at", "2024-10-08T22:00:00").json();
        assertThat(body.get("fit").asBoolean()).isFalse();
        assertThat(texts(body.get("reasons"))).contains("leak test is not current");
    }

    @Test
    void aRetiredSourceSaysSoAndStopsGoingOut() {
        api.postJson("/sources", aSource());
        assertThat(api.post("/sources/IR-5512/retire").json().get("state").asText())
                .isEqualTo("awaiting disposal");
        assertThat(api.postJson("/sources/IR-5512/send-out", aBooking()).status())
                .isEqualTo(409);
    }

    @Test
    void aSourceOutOnAJobCannotBeRetired() {
        api.postJson("/sources", aSource());
        wiped();
        inspected();
        api.postJson("/sources/IR-5512/send-out", aBooking());
        assertThat(api.post("/sources/IR-5512/retire").status()).isEqualTo(409);
    }

    @Test
    void theOpenapiDocumentListsTheSourceRoutes() {
        JsonNode paths = api.get("/openapi.json").json().get("paths");
        assertThat(paths.has("/sources/{serial}/fitness")).isTrue();
        assertThat(paths.has("/sources/{serial}/send-out")).isTrue();
    }

    @Test
    void anInspectionIsRecordedAndReadBack() {
        Http.Answer response = api.postJson(
                "/devices/" + DEVICE + "/inspections",
                Map.of("performed_on", "2024-09-20", "performed_by", "0303", "is_quarterly", true));
        assertThat(response.status()).isEqualTo(201);
        JsonNode body = response.json();
        assertThat(body.get("is_quarterly").asBoolean()).isTrue();
        assertThat(body.get("passed").asBoolean()).isTrue();
        assertThat(body.get("expires_on").asText()).isEqualTo("2024-12-21");
    }

    @Test
    void aPreUseCheckDoesNotExpire() {
        JsonNode body = api.postJson(
                        "/devices/" + DEVICE + "/inspections",
                        Map.of("performed_on", "2024-10-08", "performed_by", "0142"))
                .json();
        assertThat(body.get("expires_on").isNull()).isTrue();
    }

    @Test
    void aSafetyDefectComesBackApartFromANotedOne() {
        Map<String, Object> inspection = new LinkedHashMap<>();
        inspection.put("performed_on", "2024-09-20");
        inspection.put("performed_by", "0303");
        inspection.put("is_quarterly", true);
        inspection.put(
                "defects",
                List.of(
                        Map.of("item", "drive_cable", "account", "three strands broken"),
                        Map.of("item", "wheels", "account", "one castor seized")));
        JsonNode body =
                api.postJson("/devices/" + DEVICE + "/inspections", inspection).json();
        assertThat(body.get("passed").asBoolean()).isFalse();
        assertThat(texts(body.get("blocking"))).containsExactly("drive cable: three strands broken");
        assertThat(texts(body.get("noted"))).containsExactly("wheels: one castor seized");
    }

    @Test
    void anItemNothingInspectsIsA422() {
        Map<String, Object> inspection = new LinkedHashMap<>();
        inspection.put("performed_on", "2024-09-20");
        inspection.put("performed_by", "0303");
        inspection.put("defects", List.of(Map.of("item", "flux_capacitor", "account", "humming")));
        Http.Answer response = api.postJson("/devices/" + DEVICE + "/inspections", inspection);
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.json().get("context").get("known").asText()).contains("drive cable");
    }

    @Test
    void aDevicesInspectionsReadInDateOrder() {
        inspected();
        assertThat(texts(api.get("/devices/" + DEVICE + "/inspections").json(), "performed_on"))
                .containsExactly("2024-09-20", "2024-10-08");
    }

    @Test
    void aDeviceWithNothingOnRecordListsNothing() {
        assertThat(api.get("/devices/" + DEVICE + "/inspections").json()).isEmpty();
    }

    @Test
    void theClearanceEndpointSaysNoAndWhy() {
        api.postJson("/sources", aSource());
        JsonNode body = api.get(
                        "/devices/" + DEVICE + "/clearance", "source", "IR-5512", "on", "2024-10-08", "crew", "0142")
                .json();
        assertThat(body.get("cleared").asBoolean()).isFalse();
        assertThat(body.get("source_reasons")).isNotEmpty();
        assertThat(body.get("device_reasons")).isNotEmpty();
        assertThat(body.get("crew_reasons")).isEmpty();
    }

    @Test
    void theClearanceEndpointSaysYesWhenEverythingIsInOrder() {
        api.postJson("/sources", aSource());
        wiped();
        inspected();
        JsonNode body = api.get(
                        "/devices/" + DEVICE + "/clearance", "source", "IR-5512", "on", "2024-10-08", "crew", "0142")
                .json();
        assertThat(body.get("cleared").asBoolean()).isTrue();
        assertThat(body.get("source_reasons")).isEmpty();
    }

    @Test
    void theOpenapiDocumentListsTheDeviceRoutes() {
        JsonNode paths = api.get("/openapi.json").json().get("paths");
        assertThat(paths.has("/devices/{serial}/inspections")).isTrue();
        assertThat(paths.has("/devices/{serial}/clearance")).isTrue();
    }
}
