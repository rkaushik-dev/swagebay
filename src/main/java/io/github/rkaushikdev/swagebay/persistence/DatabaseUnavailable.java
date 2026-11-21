package io.github.rkaushikdev.swagebay.persistence;

import java.sql.SQLException;

/**
 * The database would not answer. Not one of the domain's refusals: nothing about the request was
 * wrong and no record said no, the database simply was not there to ask.
 */
public class DatabaseUnavailable extends RuntimeException {

    /** The database at {@code url} failed with {@code cause}. */
    public DatabaseUnavailable(String url, SQLException cause) {
        super(url + ": " + cause.getMessage(), cause);
    }
}
