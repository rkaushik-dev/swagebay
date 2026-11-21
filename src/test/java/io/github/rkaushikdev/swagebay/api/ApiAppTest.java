package io.github.rkaushikdev.swagebay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.SwagebayError;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The application as a whole: started by its own factory on a port of its own, with the routes the
 * tests add beside the service's.
 */
class ApiAppTest {

    private static final String ANY_PORT = "--server.port=0";

    private static ConfigurableApplicationContext app;

    private Http client;

    @BeforeAll
    static void startTheApplication() {
        app = SwagebayApplication.run(TestDatabase.fresh(), List.of(ProbeRoutes.class), ANY_PORT);
    }

    @AfterAll
    static void stopTheApplication() {
        app.close();
    }

    @BeforeEach
    void emptyTheDatabase() {
        TestDatabase.fresh();
        client = clientOf(app);
    }

    private static Http clientOf(ConfigurableApplicationContext context) {
        return new Http(MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .build());
    }

    @Test
    void theHealthEndpointAnswersWithTheVersion() {
        Http.Answer response = client.get("/health");
        assertThat(response.status()).isEqualTo(200);
        assertThat(Http.asMap(response.json()))
                .isEqualTo(Map.of("status", "ok", "version", SwagebayApplication.VERSION));
    }

    @Test
    void eachErrorMapsToTheStatusItDeserves() {
        assertThat(Errors.statusFor(new NotFoundError("source", "IR-1"))).isEqualTo(404);
        assertThat(Errors.statusFor(new ConflictError("busy"))).isEqualTo(409);
        assertThat(Errors.statusFor(new ValidationError("bad"))).isEqualTo(422);
        assertThat(Errors.statusFor(new TechniqueError("impossible"))).isEqualTo(422);
        assertThat(Errors.statusFor(new ConfigurationError("ours"))).isEqualTo(500);
        assertThat(Errors.statusFor(new SwagebayError("unclassified"))).isEqualTo(500);
    }

    @Test
    void theMoreSpecificMappingWins() {
        List<Class<? extends SwagebayError>> kinds = Errors.STATUS_BY_ERROR.stream()
                .<Class<? extends SwagebayError>>map(Errors.Mapping::kind)
                .toList();
        assertThat(kinds.indexOf(NotFoundError.class)).isLessThan(kinds.indexOf(ValidationError.class));
        assertThat(Errors.statusFor(new NotFoundError("weld", "GW-1"))).isEqualTo(404);
    }

    @Test
    void theBodyIsTheErrorDictionaryUnchanged() {
        TechniqueError error = new TechniqueError("too close", Context.of("sfd_mm", 400, "minimum_mm", 610));
        assertThat(Errors.errorBody(error)).isEqualTo(error.asMap());
        assertThat(Errors.errorBody(error).get("code")).isEqualTo("qi.technique");
    }

    @Test
    void aDomainErrorFromAHandlerComesBackAsItsStatus() {
        Http.Answer response = client.get("/boom");
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.json().get("code").asText()).isEqualTo("qi.not_found");
        assertThat(response.json().get("context").get("identity").asText()).isEqualTo("IR-9999");
    }

    @Test
    void aConflictComesBackAsAConflict() {
        Http.Answer response = client.get("/busy");
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.json().get("context").get("serial").asText()).isEqualTo("IR-5512");
    }

    @Test
    void anErrorThatIsNotOursIsNotSwallowed() {
        assertThatThrownBy(() -> client.get("/other"))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("something else");
    }

    @Test
    void aHandlerThatReturnsCommitsItsUnitOfWork() {
        assertThat(client.post("/probe/write").status()).isEqualTo(200);
        assertThat(Http.asMap(client.get("/probe/count").json())).isEqualTo(Map.of("count", 1));
    }

    @Test
    void aHandlerThatRaisesRollsItsUnitOfWorkBack() {
        assertThat(client.post("/probe/bad").status()).isEqualTo(409);
        assertThat(Http.asMap(client.get("/probe/two").json())).isEqualTo(Map.of("count", 0));
    }

    @Test
    void twoApplicationsDoNotShareADatabase() {
        try (Database one = Database.inMemory();
                Database other = Database.inMemory()) {
            one.migrate();
            other.migrate();
            try (ConfigurableApplicationContext first = SwagebayApplication.run(one, ANY_PORT);
                    ConfigurableApplicationContext second = SwagebayApplication.run(other, ANY_PORT)) {
                assertThat(first.getBean(Database.class)).isNotSameAs(second.getBean(Database.class));
                assertThat(first.getBean(Work.class)).isNotSameAs(second.getBean(Work.class));
            }
        }
    }

    @Test
    void theOpenapiDocumentNamesTheService() {
        JsonNode document = client.get("/openapi.json").json();
        assertThat(document.get("info").get("title").asText()).isEqualTo("swagebay");
        assertThat(document.get("info").get("version").asText()).isEqualTo(SwagebayApplication.VERSION);
    }
}
