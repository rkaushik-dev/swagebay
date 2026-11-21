package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What has to be known before a job can be opened.
 *
 * @param client who the work is for
 * @param site where it is done
 * @param code the acceptance code the welds are judged to
 * @param procedureNumber the written procedure the work follows
 * @param openedOn the day the job opens, whose year numbers it
 */
public record NewJob(String client, String site, Code code, String procedureNumber, LocalDate openedOn) {

    /**
     * A job to open.
     *
     * @throws ValidationError naming the first of the client, the site and the procedure number that
     *     is blank
     */
    public NewJob {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(openedOn, "openedOn");
        required("client", client);
        required("site", site);
        required("procedure_number", procedureNumber);
    }

    private static void required(String name, String value) {
        if (Text.isBlank(value)) {
            throw new ValidationError("a job needs this filled in", Context.of("field", name));
        }
    }
}
