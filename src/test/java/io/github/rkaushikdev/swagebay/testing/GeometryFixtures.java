package io.github.rkaushikdev.swagebay.testing;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;

import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.geometry.PipeSchedule;
import io.github.rkaushikdev.swagebay.imaging.Films;
import io.github.rkaushikdev.swagebay.imaging.ImagingSystem;
import io.github.rkaushikdev.swagebay.materials.Materials;

/** The two girth welds and the film the technique tests and everything downstream build on. */
public final class GeometryFixtures {

    private GeometryFixtures() {}

    /** Six inch schedule 80 carbon steel with a 2.0 mm cap. */
    public static Joint sixInchWeld() {
        return PipeSchedule.girthWeld("6", "80", Materials.STEEL, mm("2.0"));
    }

    /** Twelve inch schedule 80 carbon steel with a 2.5 mm cap. */
    public static Joint twelveInchWeld() {
        return PipeSchedule.girthWeld("12", "80", Materials.STEEL, mm("2.5"));
    }

    /** Structurix D7 film. */
    public static ImagingSystem onD7() {
        return ImagingSystem.fromFilm(Films.film("d7"));
    }
}
