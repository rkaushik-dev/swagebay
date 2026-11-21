package io.github.rkaushikdev.swagebay.api;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.quantities.Units.seconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.swagebay.api.Schemas.ActivityOut;
import io.github.rkaushikdev.swagebay.api.Schemas.DurationOut;
import io.github.rkaushikdev.swagebay.api.Schemas.JointIn;
import io.github.rkaushikdev.swagebay.api.Schemas.LengthIn;
import io.github.rkaushikdev.swagebay.api.Schemas.LengthOut;
import io.github.rkaushikdev.swagebay.api.Schemas.NewJobIn;
import io.github.rkaushikdev.swagebay.api.Schemas.NewWeldIn;
import io.github.rkaushikdev.swagebay.api.Schemas.OutstandingOut;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiSchemasTest {

    private static final LocalDate DAY = LocalDate.of(2024, 9, 26);

    @Test
    void aLengthGoesOutInBothSystems() {
        LengthOut out = LengthOut.of(inch("0.375"));
        assertThat(out.um()).isEqualTo(9525);
        assertThat(out.mm()).isEqualByComparingTo("9.525");
        assertThat(out.inch()).isEqualByComparingTo("0.375");
    }

    @Test
    void anActivityGoesOutInBothToo() {
        ActivityOut out = ActivityOut.of(curie(90));
        assertThat(out.bq()).isEqualTo(3_330_000_000_000L);
        assertThat(out.curie()).isEqualByComparingTo("90");
        assertThat(out.tbq()).isEqualByComparingTo("3.33");
    }

    @Test
    void aDurationCarriesTheTimerStringTheCrewReads() {
        DurationOut out = DurationOut.of(seconds(93));
        assertThat(out.ms()).isEqualTo(93000);
        assertThat(out.timer()).isEqualTo("1:33");
    }

    @Test
    void aLengthComesInTheWayAProcedureWritesIt() {
        assertThat(new LengthIn("9.53 mm").toLength()).isEqualTo(mm("9.53"));
        assertThat(new LengthIn("0.375 in").toLength()).isEqualTo(inch("0.375"));
        assertThat(new LengthIn("40 thou").toLength().um()).isEqualTo(1016);
    }

    @Test
    void aLengthThatWillNotParseIsRefusedAtTheEdge() {
        for (String bad : List.of("", "nine mm", "9.53", "9.53 kg")) {
            assertThatThrownBy(() -> Requests.validated(new LengthIn(bad)))
                    .as(bad)
                    .isInstanceOf(RequestRejected.class);
        }
    }

    @Test
    void aJobRequestNeedsEveryFieldFilledIn() {
        NewJobIn good = Requests.validated(new NewJobIn("c", "s", "API 1104", "RT-001", DAY));
        assertThat(good.client()).isEqualTo("c");
        assertThatThrownBy(() -> Requests.validated(new NewJobIn("", "s", "API 1104", "RT-001", DAY)))
                .isInstanceOf(RequestRejected.class);
    }

    @Test
    void aJointMayHaveNoDiameterBecausePlateHasNone() {
        JointIn plate = new JointIn("carbon_steel", new LengthIn("12 mm"));
        assertThat(plate.outsideDiameter()).isEmpty();
        assertThat(plate.reinforcement()).isEmpty();
    }

    @Test
    void aPipeJointCarriesItsDiameter() {
        JointIn pipe = new JointIn(
                "carbon_steel",
                new LengthIn("10.97 mm"),
                Optional.of(new LengthIn("168.3 mm")),
                Optional.of(new LengthIn("2 mm")));
        assertThat(pipe.outsideDiameter().orElseThrow().toLength()).isEqualTo(mm("168.3"));
        assertThat(pipe.reinforcement().orElseThrow().toLength()).isEqualTo(mm(2));
    }

    @Test
    void aWeldRequestCarriesItsJoint() {
        NewWeldIn request = Requests.validated(new NewWeldIn(
                "GW-104", new JointIn("carbon_steel", new LengthIn("10.97 mm")), new LengthIn("529 mm"), "w-19", DAY));
        assertThat(request.weld()).isEqualTo("GW-104");
        assertThat(request.weldLength().toLength()).isEqualTo(mm(529));
    }

    /**
     * A request is a record: every field is final and nothing sets one, so a handler that tries to
     * edit a request does not compile.
     */
    @Test
    void theSchemasAreFrozenSoAHandlerCannotEditARequest() {
        assertThat(LengthIn.class.isRecord()).isTrue();
        Field[] fields = LengthIn.class.getDeclaredFields();
        assertThat(fields).isNotEmpty();
        assertThat(fields).allMatch(field -> Modifier.isFinal(field.getModifiers()));
        assertThat(Arrays.stream(LengthIn.class.getMethods()).map(method -> method.getName()))
                .noneMatch(name -> name.startsWith("set"));
    }

    @Test
    void anOutstandingListKnowsWhetherItIsClear() {
        assertThat(new OutstandingOut("24-0001", List.of()).isClear()).isTrue();
        assertThat(new OutstandingOut("24-0001", List.of("R-001 unread")).isClear())
                .isFalse();
    }

    @Test
    void aLengthOutSerialisesToJsonWithoutLosingTheDecimal() {
        JsonNode body = Json.mapper().valueToTree(LengthOut.of(mm("9.53")));
        assertThat(body.get("mm").isTextual()).isTrue();
        assertThat(body.get("mm").asText()).isEqualTo("9.53");
        assertThat(body.get("um").isIntegralNumber()).isTrue();
        assertThat(body.get("um").asLong()).isEqualTo(9530);
    }

    @Test
    void aWelderAndAWeldIdentifierCannotBeBlank() {
        assertThatThrownBy(() -> Requests.validated(new NewWeldIn(
                        "", new JointIn("carbon_steel", new LengthIn("10 mm")), new LengthIn("529 mm"), "w-19", DAY)))
                .isInstanceOf(RequestRejected.class);
        assertThatThrownBy(() -> Requests.validated(new NewWeldIn(
                        "GW-104", new JointIn("carbon_steel", new LengthIn("10 mm")), new LengthIn("529 mm"), "", DAY)))
                .isInstanceOf(RequestRejected.class);
    }
}
