package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything on one truck.
 *
 * <p>A crew carrying two projectors to a job can easily be over the vehicle ceiling without either
 * package being over anything on its own, which is the failure that turns up on a roadside
 * inspection.
 *
 * @param packages the packages aboard
 */
public record Shipment(List<TransportPackage> packages) {

    /** A shipment; the packages are copied. */
    public Shipment {
        packages = List.copyOf(packages);
    }

    /** The sum of the transport indices aboard. */
    public BigDecimal totalIndex() {
        BigDecimal total = BigDecimal.ZERO;
        for (TransportPackage aboard : packages) {
            total = Decimals.add(total, aboard.transportIndex());
        }
        return total;
    }

    /** Whether the indices aboard add up to more than the vehicle may carry. */
    public boolean overVehicleCeiling() {
        return totalIndex().compareTo(Transport.VEHICLE_INDEX_CEILING) > 0;
    }

    /** Every package's faults, each led by its reference, then the vehicle ceiling if it is exceeded. */
    public List<String> faults() {
        List<String> out = new ArrayList<>();
        for (TransportPackage aboard : packages) {
            aboard.faults().forEach(fault -> out.add(aboard.reference() + ": " + fault));
        }
        if (overVehicleCeiling()) {
            out.add("the transport indices total " + totalIndex() + ", over the vehicle ceiling of "
                    + Transport.VEHICLE_INDEX_CEILING);
        }
        return List.copyOf(out);
    }
}
