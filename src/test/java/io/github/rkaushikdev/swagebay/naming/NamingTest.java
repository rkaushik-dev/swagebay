package io.github.rkaushikdev.swagebay.naming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.evaluation.Kind;
import io.github.rkaushikdev.swagebay.geometry.DistanceRule;
import io.github.rkaushikdev.swagebay.geometry.ShotType;
import io.github.rkaushikdev.swagebay.imaging.IndicatorSide;
import io.github.rkaushikdev.swagebay.imaging.TechniqueClass;
import io.github.rkaushikdev.swagebay.nonconformance.Disposition;
import io.github.rkaushikdev.swagebay.safety.InspectionItem;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class NamingTest {

    /** One enum, its lookup, and the value it publishes. */
    private record Lookup<E extends Enum<E>>(Class<E> kind, Function<String, E> call, Function<E, String> value) {}

    private static final List<Lookup<?>> LOOKUPS = List.of(
            new Lookup<>(Code.class, Naming::codeNamed, Code::value),
            new Lookup<>(ShotType.class, Naming::shotNamed, ShotType::value),
            new Lookup<>(IndicatorSide.class, Naming::sideNamed, IndicatorSide::value),
            new Lookup<>(TechniqueClass.class, Naming::techniqueClassNamed, TechniqueClass::value),
            new Lookup<>(DistanceRule.class, Naming::distanceRuleNamed, DistanceRule::value));

    private static <E extends Enum<E>> void roundTripsThroughItsValue(Lookup<E> lookup) {
        for (E member : lookup.kind().getEnumConstants()) {
            assertThat(lookup.call().apply(lookup.value().apply(member))).isSameAs(member);
        }
    }

    private static <E extends Enum<E>> void roundTripsThroughItsName(Lookup<E> lookup) {
        for (E member : lookup.kind().getEnumConstants()) {
            assertThat(lookup.call().apply(member.name())).isSameAs(member);
        }
    }

    @Test
    void aPublishedValueResolves() {
        assertThat(Naming.codeNamed("API 1104")).isSameAs(Code.API_1104);
        assertThat(Naming.shotNamed("double wall single image")).isSameAs(ShotType.DWSI);
        assertThat(Naming.sideNamed("film side")).isSameAs(IndicatorSide.FILM);
    }

    @Test
    void theMemberNameResolvesTooAndItDoesSoEverywhere() {
        assertThat(Naming.codeNamed("API_1104")).isSameAs(Code.API_1104);
        assertThat(Naming.shotNamed("dwsi")).isSameAs(ShotType.DWSI);
        assertThat(Naming.sideNamed("FILM")).isSameAs(IndicatorSide.FILM);
        assertThat(Naming.techniqueClassNamed("b")).isSameAs(TechniqueClass.B);
        assertThat(Naming.distanceRuleNamed("iso_17636_class_a")).isSameAs(DistanceRule.ISO_17636_CLASS_A);
    }

    @Test
    void surroundingSpaceIsIgnored() {
        assertThat(Naming.codeNamed("  API 1104  ")).isSameAs(Code.API_1104);
        assertThat(Naming.shotNamed(" panoramic ")).isSameAs(ShotType.PANORAMIC);
    }

    @Test
    void aNameNobodyPublishesIsRefusedWithTheList() {
        assertThatThrownBy(() -> Naming.codeNamed("BS 2600")).isInstanceOfSatisfying(ValidationError.class, refused -> {
            assertThat(refused.context()).containsEntry("given", "BS 2600");
            assertThat((String) refused.context().get("known")).contains("API 1104");
        });
    }

    @Test
    void everyLookupRefusesRatherThanDefaulting() {
        List<Function<String, ?>> calls = List.of(
                Naming::codeNamed,
                Naming::shotNamed,
                Naming::sideNamed,
                Naming::techniqueClassNamed,
                Naming::kindNamed);
        for (Function<String, ?> call : calls) {
            assertThatThrownBy(() -> call.apply("nonsense")).isInstanceOf(ValidationError.class);
        }
    }

    @Test
    void nothingMeansLetThePlannerPick() {
        assertThat(Naming.optionalShotNamed(Optional.empty())).isEmpty();
        assertThat(Naming.optionalShotNamed(Optional.of("panoramic"))).contains(ShotType.PANORAMIC);
        assertThatThrownBy(() -> Naming.optionalShotNamed(Optional.of("sideways")))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aDiscontinuityIsNamedByItsCodeAndTheCaseDoesNotMatter() {
        assertThat(Naming.kindNamed("ESI")).isSameAs(Kind.ESI);
        assertThat(Naming.kindNamed("esi")).isSameAs(Kind.ESI);
        assertThat(Naming.kindNamed(" c ")).isSameAs(Kind.C);
    }

    /**
     * A discontinuity used to be named by its code only. Accepting the description is what every
     * other enum here already did, and keeping one of them stricter than the rest was never a
     * decision anybody made.
     */
    @Test
    void aDiscontinuityIsAlsoNamedByItsDescriptionNow() {
        assertThat(Naming.kindNamed("crack")).isSameAs(Kind.C);
        assertThat(Naming.kindNamed("elongated slag inclusion")).isSameAs(Kind.ESI);
    }

    @Test
    void aDescriptionNobodyPublishesIsStillRefused() {
        assertThatThrownBy(() -> Naming.kindNamed("a nasty looking one"))
                .isInstanceOfSatisfying(ValidationError.class, refused -> assertThat(
                                (String) refused.context().get("known"))
                        .contains("crack"));
    }

    @Test
    void theGeneralLookupNamesWhatItWasLookingFor() {
        assertThatThrownBy(() -> Naming.named(Code.class, "nothing", "acceptance code"))
                .isInstanceOfSatisfying(ValidationError.class, refused -> assertThat(refused.message())
                        .contains("acceptance code"));
    }

    @Test
    void everyMemberOfEveryEnumRoundTripsThroughItsValue() {
        LOOKUPS.forEach(NamingTest::roundTripsThroughItsValue);
    }

    @Test
    void everyMemberRoundTripsThroughItsNameToo() {
        LOOKUPS.forEach(NamingTest::roundTripsThroughItsName);
    }

    @Test
    void everyDiscontinuityCodeRoundTrips() {
        for (Kind member : Kind.values()) {
            assertThat(Naming.kindNamed(member.name())).isSameAs(member);
        }
    }

    @Test
    void anUnderscoredMemberNameIsAcceptedBecauseThatIsWhatGetsTyped() {
        assertThat(Naming.dispositionNamed("use_as_is")).isSameAs(Disposition.USE_AS_IS);
        assertThat(Naming.dispositionNamed("use as is")).isSameAs(Disposition.USE_AS_IS);
        assertThat(Naming.dispositionNamed("USE_AS_IS")).isSameAs(Disposition.USE_AS_IS);
        assertThat(Naming.itemNamed("drive_cable")).isSameAs(InspectionItem.DRIVE_CABLE);
        assertThat(Naming.itemNamed("drive cable")).isSameAs(InspectionItem.DRIVE_CABLE);
        assertThat(Naming.itemNamed("drive-cable")).isSameAs(InspectionItem.DRIVE_CABLE);
    }

    @Test
    void anEnumWithALabelPublishesTheLabelAndNotTheTuple() {
        assertThat(Naming.published(InspectionItem.DRIVE_CABLE)).isEqualTo("drive cable");
        assertThatThrownBy(() -> Naming.itemNamed("flux capacitor"))
                .isInstanceOfSatisfying(ValidationError.class, refused -> {
                    String known = (String) refused.context().get("known");
                    assertThat(known).contains("drive cable");
                    assertThat(known).doesNotContain("Severity");
                });
    }

    @Test
    void aDispositionNobodyPublishesIsRefusedWithTheOnesThatExist() {
        assertThatThrownBy(() -> Naming.dispositionNamed("have a think"))
                .isInstanceOfSatisfying(ValidationError.class, refused -> assertThat(
                                (String) refused.context().get("known"))
                        .contains("rework"));
    }
}
