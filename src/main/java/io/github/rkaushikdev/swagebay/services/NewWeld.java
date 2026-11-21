package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A weld to add to a job.
 *
 * @param weld the weld, repair round included
 * @param joint the joint, which carries the material, the wall and the cap
 * @param weldLength the length of weld the films have to cover, above zero
 * @param welder who made it
 * @param weldedOn the day it was made
 */
public record NewWeld(WeldId weld, Joint joint, Length weldLength, String welder, LocalDate weldedOn) {

    /**
     * A weld to add.
     *
     * @throws ValidationError without a welder, or for a weld with no length
     */
    public NewWeld {
        Objects.requireNonNull(weld, "weld");
        Objects.requireNonNull(joint, "joint");
        Objects.requireNonNull(weldedOn, "weldedOn");
        if (Text.isBlank(welder)) {
            throw new ValidationError("a weld has a welder");
        }
        if (weldLength.um() <= 0) {
            throw new ValidationError("a weld has a length");
        }
    }
}
