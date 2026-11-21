package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.testing.TestDatabase.assertTheSchemaRefuses;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.persistence.models.FilmRow;
import io.github.rkaushikdev.swagebay.persistence.models.IsotopeRow;
import io.github.rkaushikdev.swagebay.persistence.models.MaterialRow;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Check;
import org.junit.jupiter.api.Test;

class ReferenceTablesTest {

    private static final LocalDate WHEN = LocalDate.of(2024, 1, 1);

    private static IsotopeRow isotopeRow(String key, int revision, int low, int high) {
        IsotopeRow row = new IsotopeRow();
        row.setKey(key);
        row.setRevision(revision);
        row.setSymbol("Ir-192");
        row.setHalfLifeDays(new BigDecimal("73.83"));
        row.setGammaConstant(new BigDecimal("0.48"));
        row.setEnergyKevLow(low);
        row.setEnergyKevHigh(high);
        row.setSteelMin(mm(12));
        row.setSteelMax(mm(63));
        row.setEffectiveOn(WHEN);
        return row;
    }

    private static IsotopeRow isotopeRow() {
        return isotopeRow("ir192", 0, 206, 612);
    }

    private static MaterialRow materialRow(int density) {
        MaterialRow row = new MaterialRow();
        row.setKey("carbon_steel");
        row.setName("carbon steel");
        row.setDensityKgM3(density);
        row.setFerrous(true);
        row.setEffectiveOn(WHEN);
        return row;
    }

    private static FilmRow filmRow(BigDecimal speed) {
        FilmRow row = new FilmRow();
        row.setKey("d7");
        row.setName("Structurix D7");
        row.setManufacturer("Agfa");
        row.setFilmClass("II");
        row.setSpeedIndex(speed);
        row.setGranularity(new BigDecimal("0.024"));
        row.setEffectiveOn(WHEN);
        return row;
    }

    @Test
    void theModelsPackagePutsEveryTableOnTheMetadata() {
        assertThat(Schema.tableNames()).contains("material", "isotope", "film");
    }

    @Test
    void anIsotopeRoundTripsWithItsLengthsIntact() {
        try (TestSession db = TestSession.open()) {
            db.add(isotopeRow());
            db.commit();
            IsotopeRow found = db.one(IsotopeRow.class);
            assertThat(found.getSteelMin()).isEqualTo(mm(12));
            assertThat(found.getHalfLifeDays()).isEqualByComparingTo("73.83");
        }
    }

    @Test
    void aRevisionIsANewRowAndNotAnUpdate() {
        try (TestSession db = TestSession.open()) {
            db.add(isotopeRow("ir192", 0, 206, 612));
            db.add(isotopeRow("ir192", 1, 206, 612));
            db.commit();
            assertThat(db.all(IsotopeRow.class)).hasSize(2);
        }
    }

    @Test
    void theSameKeyAndRevisionTwiceIsRefused() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(isotopeRow("ir192", 0, 206, 612));
                db.add(isotopeRow("ir192", 0, 206, 612));
                db.commit();
            });
        }
    }

    @Test
    void anEnergyBandTheWrongWayRoundIsRefused() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(isotopeRow("ir192", 0, 600, 200));
                db.commit();
            });
        }
    }

    @Test
    void aHalfLifeOfNothingIsRefusedByTheSchema() {
        try (TestSession db = TestSession.open()) {
            IsotopeRow row = isotopeRow();
            row.setHalfLifeDays(BigDecimal.ZERO);
            assertTheSchemaRefuses(() -> {
                db.add(row);
                db.commit();
            });
        }
    }

    @Test
    void aMaterialNeedsAPositiveDensity() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(materialRow(0));
                db.commit();
            });
        }
    }

    @Test
    void aMaterialRoundTrips() {
        try (TestSession db = TestSession.open()) {
            db.add(materialRow(7850));
            db.commit();
            MaterialRow found = db.one(MaterialRow.class);
            assertThat(found.getDensityKgM3()).isEqualTo(7850);
            assertThat(found.isFerrous()).isTrue();
            assertThat(found.getRevision()).isZero();
        }
    }

    @Test
    void aFilmKeepsItsSpeedAsADecimal() {
        try (TestSession db = TestSession.open()) {
            db.add(filmRow(BigDecimal.valueOf(100)));
            db.commit();
            FilmRow found = db.one(FilmRow.class);
            assertThat(found.getSpeedIndex()).isEqualByComparingTo("100");
            assertThat(found.getGranularity()).isEqualByComparingTo("0.024");
        }
    }

    @Test
    void aFilmWithNoSpeedIsRefused() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(filmRow(BigDecimal.ZERO));
                db.commit();
            });
        }
    }

    /**
     * Every unique key, check and foreign key the mapping declares carries a name; none is left for
     * the database to make one up. The primary keys are named in the migrations, which the migration
     * tests hold to the same rule.
     */
    @Test
    void everyConstraintCarriesANameFromTheConvention() {
        int seen = 0;
        for (Class<?> entity : Mapping.entities()) {
            String table = Mapping.table(entity);
            for (UniqueConstraint unique : Mapping.uniques(entity)) {
                assertThat(unique.name()).as(table).isNotBlank().startsWith("uq_" + table + "_");
                seen++;
            }
            for (Check check : Mapping.checks(entity)) {
                assertThat(check.name()).as(table).isNotBlank().startsWith("ck_" + table + "_");
                seen++;
            }
            for (Mapping.ForeignKey key : Mapping.foreignKeys(entity)) {
                assertThat(key.name()).as(table).isNotBlank().startsWith("fk_" + table + "_");
                seen++;
            }
        }
        assertThat(seen).isPositive();
    }
}
