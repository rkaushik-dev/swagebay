package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.sources.DeviceModel;
import io.github.rkaushikdev.swagebay.util.Text;

/**
 * One physical exposure device the shop owns.
 *
 * @param serial the serial number on the device
 * @param model the type approved model it is
 */
public record DeviceUnit(String serial, DeviceModel model) {

    /**
     * A device.
     *
     * @throws ValidationError without a serial number
     */
    public DeviceUnit {
        if (Text.strip(serial).isEmpty()) {
            throw new ValidationError("a device has a serial number");
        }
    }

    /** The model name and the serial: {@code 880 Delta 880D-221}. */
    @Override
    public String toString() {
        return model.name() + " " + serial;
    }
}
