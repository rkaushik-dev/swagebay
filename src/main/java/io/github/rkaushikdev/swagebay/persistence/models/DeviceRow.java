package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One physical exposure device the shop owns, and the trips it has been out on.
 *
 * <p>The trips belong to their source, not to the device. The list here is there to be read, and a
 * device with trips on record cannot be deleted out from under them.
 */
@Entity
@Table(
        name = "device",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_device_serial",
                    columnNames = {"serial"})
        })
public class DeviceRow extends Keyed {

    @Column(name = "serial", length = 40, nullable = false)
    private String serial;

    @Column(name = "model_key", length = 20, nullable = false)
    private String modelKey;

    @OneToMany(mappedBy = "device")
    @OrderBy
    private final List<TripRow> trips = new ArrayList<>();

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getModelKey() {
        return modelKey;
    }

    public void setModelKey(String modelKey) {
        this.modelKey = modelKey;
    }

    public List<TripRow> getTrips() {
        return Collections.unmodifiableList(trips);
    }
}
