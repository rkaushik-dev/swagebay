package io.github.rkaushikdev.swagebay.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.Optional;

/**
 * A surrogate primary key, which every table here has.
 *
 * <p>That is deliberate. The domain identifiers, a job number or a weld identifier or a source
 * serial, are real and meaningful, and they also get corrected, and a foreign key onto a corrected
 * identifier is a bad afternoon. So each one is a unique column beside an integer the database
 * assigns.
 */
@MappedSuperclass
public abstract class Keyed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    /** The key the database assigned when the row was first written, and nothing before that. */
    public Optional<Integer> getId() {
        return Optional.ofNullable(id);
    }
}
