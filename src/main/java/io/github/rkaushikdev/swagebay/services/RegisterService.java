package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.calibration.Certificate;
import io.github.rkaushikdev.swagebay.calibration.Instrument;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.CalibrationRow;
import io.github.rkaushikdev.swagebay.persistence.models.FiledRow;
import io.github.rkaushikdev.swagebay.persistence.models.IncidentRow;
import io.github.rkaushikdev.swagebay.persistence.models.LotRow;
import io.github.rkaushikdev.swagebay.persistence.models.QualificationRow;
import io.github.rkaushikdev.swagebay.reporting.Filed;
import io.github.rkaushikdev.swagebay.reporting.Location;
import io.github.rkaushikdev.swagebay.safety.Incident;
import io.github.rkaushikdev.swagebay.stock.Issue;
import io.github.rkaushikdev.swagebay.stock.Lot;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Text;
import io.github.rkaushikdev.swagebay.welding.Qualification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Writing to the five registers, which is the same shape five times over.
 *
 * <p>Each of these takes a domain object, turns it into a row and flushes. There is no clever base
 * class doing it for them: one would need a mapper, a table and a uniqueness check that differ for
 * every register, at which point it is a worse way of writing five short functions than five short
 * functions.
 *
 * <p>What they do share is the refusal to overwrite. Every one of them checks for the key first and
 * refuses rather than updating, because a register is append only and a silent update in an append
 * only register is the defect that cannot be found afterwards.
 */
public final class RegisterService {

    private RegisterService() {}

    /**
     * Put an incident on the register.
     *
     * @throws ConflictError for a reference already used
     */
    public static void recordIncident(UnitOfWork work, Incident incident) {
        if (work.incidents().find(incident.reference()).isPresent()) {
            throw new ConflictError("that reference is already used", Context.of("reference", incident.reference()));
        }
        IncidentRow row = new IncidentRow();
        row.setReference(incident.reference());
        row.setOccurrence(incident.occurrence().value());
        row.setDiscoveredAt(incident.discoveredAt());
        row.setSite(incident.site());
        row.setReportedBy(incident.reportedBy());
        row.setAccount(incident.account());
        row.setNotifiedAt(incident.notifiedAt().orElse(null));
        work.entityManager().persist(row);
        work.flush();
    }

    /**
     * Record when an incident was reported to the regulator, which stops its clock.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a reference not on the register
     * @throws ConflictError when it is already reported
     * @throws ValidationError for a report dated before the incident was found
     */
    public static void notifyIncident(UnitOfWork work, String reference, LocalDateTime at) {
        IncidentRow row = work.incidents().get(reference);
        if (row.getNotifiedAt().isPresent()) {
            throw new ConflictError("that incident is already reported", Context.of("reference", reference));
        }
        if (at.isBefore(row.getDiscoveredAt())) {
            throw new ValidationError("an incident cannot be reported before it was found");
        }
        row.setNotifiedAt(at);
        work.flush();
    }

    /**
     * Put a calibration certificate on the register against an instrument.
     *
     * @throws ValidationError for a blank serial
     * @throws ConflictError when the instrument's latest certificate is from the same day
     */
    public static void recordCalibration(UnitOfWork work, String serial, Instrument kind, Certificate certificate) {
        if (Text.isBlank(serial)) {
            throw new ValidationError("an instrument has a serial number");
        }
        Optional<CalibrationRow> latest = work.calibrations().latestFor(serial);
        if (latest.isPresent() && latest.get().getCalibratedOn().equals(certificate.calibratedOn())) {
            throw new ConflictError("that calibration is already recorded", Context.of("serial", serial));
        }
        CalibrationRow row = new CalibrationRow();
        row.setSerial(serial);
        row.setInstrumentKind(kind.value());
        row.setCalibratedOn(certificate.calibratedOn());
        row.setDueOn(certificate.dueOn());
        row.setLaboratory(certificate.laboratory());
        row.setCertificateReference(certificate.reference());
        work.entityManager().persist(row);
        work.flush();
    }

    /**
     * Put a lot of film on the shelf.
     *
     * @throws ConflictError when that lot is already on the shelf
     */
    public static void receiveLot(UnitOfWork work, Lot lot) {
        List<LotRow> existing = work.stock().usableOn(lot.receivedOn(), lot.filmKey(), lot.size());
        if (existing.stream().anyMatch(row -> row.getLotNumber().equals(lot.lotNumber()))) {
            throw new ConflictError("that lot is already on the shelf", Context.of("lot", lot.lotNumber()));
        }
        LotRow row = new LotRow();
        row.setLotNumber(lot.lotNumber());
        row.setFilmKey(lot.filmKey());
        row.setSize(lot.size());
        row.setSheets(lot.sheets());
        row.setReceivedOn(lot.receivedOn());
        row.setExpiresOn(lot.expiresOn());
        work.entityManager().persist(row);
        work.flush();
    }

    /**
     * Take sheets off the shelf shortest dated first, and say from which lots.
     *
     * @throws ValidationError for an issue of no sheets
     * @throws ConflictError when there is not enough usable film, with what was wanted and what is on
     *     hand
     */
    public static List<Issue> issueFilm(UnitOfWork work, LocalDate day, String filmKey, String size, int sheets) {
        if (sheets <= 0) {
            throw new ValidationError("an issue is at least one sheet", Context.of("sheets", sheets));
        }
        List<LotRow> available = work.stock().usableOn(day, filmKey, size);
        int onHand = available.stream().mapToInt(LotRow::getSheets).sum();
        if (onHand < sheets) {
            throw new ConflictError(
                    "there is not enough film on the shelf",
                    Context.of("film", filmKey, "size", size, "wanted", sheets, "on_hand", onHand));
        }
        List<Issue> taken = new ArrayList<>();
        int remaining = sheets;
        for (LotRow row : available) {
            if (remaining == 0) {
                break;
            }
            int take = Math.min(row.getSheets(), remaining);
            row.setSheets(row.getSheets() - take);
            remaining -= take;
            taken.add(new Issue(row.getLotNumber(), take));
        }
        work.flush();
        return List.copyOf(taken);
    }

    /**
     * Put a radiograph in the filing cabinet, where it was put if anybody said.
     *
     * @throws ConflictError when it is already filed
     */
    public static void fileRadiograph(UnitOfWork work, Filed filed) {
        if (work.archive().find(filed.radiograph()).isPresent()) {
            throw new ConflictError("that radiograph is already filed", Context.of("radiograph", filed.radiograph()));
        }
        Optional<Location> where = filed.location();
        FiledRow row = new FiledRow();
        row.setRadiograph(filed.radiograph());
        row.setJob(filed.job());
        row.setFiledOn(filed.filedOn());
        row.setRetention(filed.retention().value());
        row.setSite(where.map(Location::site).orElse(null));
        row.setCabinet(where.map(Location::cabinet).orElse(null));
        row.setBox(where.map(Location::box).orElse(null));
        work.entityManager().persist(row);
        work.flush();
    }

    /**
     * Put a welder's qualification on the register, last used on the day of the test.
     *
     * @throws ConflictError when the welder already holds that process from that day
     */
    public static void recordQualification(UnitOfWork work, Qualification qualification) {
        String process = qualification.process().name();
        boolean held = work.qualifications().forWelder(qualification.welder()).stream()
                .anyMatch(row ->
                        row.getProcess().equals(process) && row.getQualifiedOn().equals(qualification.qualifiedOn()));
        if (held) {
            throw new ConflictError(
                    "that qualification is already recorded", Context.of("welder", qualification.welder()));
        }
        QualificationRow row = new QualificationRow();
        row.setWelder(qualification.welder());
        row.setProcess(process);
        row.setPosition(qualification.position().value());
        row.setCoupon(qualification.coupon());
        row.setMaterialGroup(qualification.materialGroup());
        row.setQualifiedOn(qualification.qualifiedOn());
        row.setLastUsedOn(qualification.qualifiedOn());
        work.entityManager().persist(row);
        work.flush();
    }

    /**
     * Record that a welder used a process on a day, which pushes out the lapse of every qualification
     * they hold for it. A day before the last use changes nothing.
     *
     * @param process the process by name, {@code SMAW}
     * @throws ConflictError when the welder holds no qualification for that process
     */
    public static void recordProcessUse(UnitOfWork work, String welder, String process, LocalDate on) {
        List<QualificationRow> rows = work.qualifications().forWelder(welder).stream()
                .filter(row -> row.getProcess().equals(process))
                .toList();
        if (rows.isEmpty()) {
            throw new ConflictError("that welder is not qualified for that process", Context.of("welder", welder));
        }
        for (QualificationRow row : rows) {
            if (on.isAfter(row.getLastUsedOn())) {
                row.setLastUsedOn(on);
            }
        }
        work.flush();
    }

    /** How many usable sheets of one film in one size are on the shelf that day. */
    public static int sheetsOnHand(UnitOfWork work, LocalDate day, String filmKey, String size) {
        return work.stock().onHand(day, filmKey, size);
    }

    /**
     * Whether the sheets on hand are down to the reorder floor.
     *
     * @throws ValidationError for a negative floor
     */
    public static boolean reorderNeeded(UnitOfWork work, LocalDate day, String filmKey, String size, int floor) {
        if (floor < 0) {
            throw new ValidationError("a reorder floor is not negative", Context.of("floor", floor));
        }
        return sheetsOnHand(work, day, filmKey, size) <= floor;
    }

    /**
     * A control strip speed reading handed in as text, exactly as written.
     *
     * @throws NumberFormatException for text that is not a decimal number
     */
    public static BigDecimal stripSpeed(String value) {
        return Decimals.of(value);
    }
}
