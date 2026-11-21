package io.github.rkaushikdev.swagebay.safety;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * A device on a day, which is what the utilisation log has to account for.
 *
 * <p>Device days order by device serial, then by day.
 *
 * @param deviceSerial the device
 * @param day the day
 */
public record DeviceDay(String deviceSerial, LocalDate day) implements Comparable<DeviceDay> {

    private static final Comparator<DeviceDay> ORDER =
            Comparator.comparing(DeviceDay::deviceSerial).thenComparing(DeviceDay::day);

    @Override
    public int compareTo(DeviceDay other) {
        return ORDER.compare(this, other);
    }
}
