package io.github.rkaushikdev.swagebay.testing;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;

import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.LeakTest;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** The sealed source the technique tests and everything downstream build on. */
public final class SourceFixtures {

    /** When the fixture source was assayed, and wiped. */
    public static final LocalDateTime ASSAY = LocalDateTime.of(2024, 5, 1, 0, 0);

    /** The day the technique tests shoot on, twenty days after the assay. */
    public static final LocalDateTime SHOT_DAY = ASSAY.plusDays(20);

    private SourceFixtures() {}

    /** Ninety curies of iridium in a three millimetre pellet, serial {@code IR-5512}, with a clean wipe on record. */
    public static SealedSource iridiumSource() {
        SourceCertificate certificate =
                new SourceCertificate("IR-5512", Isotopes.IR192, curie(90), ASSAY, "QSA", "A424-9", mm(3), mm(3));
        SealedSource source = new SealedSource(certificate);
        source.recordLeakTest(new LeakTest(ASSAY, BigDecimal.ZERO, "rp"));
        return source;
    }
}
