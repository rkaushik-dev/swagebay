package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;

/**
 * One operation, applied by somebody with a setting.
 *
 * @param operation what was done
 * @param setting the setting it was done at, which may be blank
 * @param appliedBy who did it
 */
public record Applied(Operation operation, String setting, String appliedBy) {

    /**
     * An applied operation.
     *
     * @throws ValidationError when nobody is named as having applied it
     */
    public Applied {
        if (Text.isBlank(appliedBy)) {
            throw new ValidationError("an operation is applied by somebody");
        }
    }

    /** {@code window and level 1200/400 by 0142}, the setting left out when there is none. */
    public String describe() {
        String detail = Text.isBlank(setting) ? "" : " " + setting;
        return operation + detail + " by " + appliedBy;
    }
}
