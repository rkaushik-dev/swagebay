package io.github.rkaushikdev.swagebay.safety;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.imaging.Discrepancy;
import io.github.rkaushikdev.swagebay.imaging.ImageFormat;
import io.github.rkaushikdev.swagebay.imaging.ImageStore;
import io.github.rkaushikdev.swagebay.imaging.Manifest;
import io.github.rkaushikdev.swagebay.imaging.Purpose;
import io.github.rkaushikdev.swagebay.imaging.StoredImage;
import io.github.rkaushikdev.swagebay.nonconformance.Disposition;
import io.github.rkaushikdev.swagebay.nonconformance.Register;
import io.github.rkaushikdev.swagebay.nonconformance.Report;
import io.github.rkaushikdev.swagebay.testing.CollectionSamples;
import io.github.rkaushikdev.swagebay.testing.MoreSamples;
import io.github.rkaushikdev.swagebay.testing.Samples;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Properties of the registers, where the interesting bugs are set operations.
 *
 * <p>The manifest verify is the one worth generating for. It partitions two sets of file names into
 * three answers and the whole point of it is that the three do not overlap and nothing falls between
 * them. That is a property, not an example, and the examples all pass whether or not it holds.
 *
 * <p>The utilisation gap check is the same shape: a set of days a device was out minus a set of days
 * somebody wrote down. It reads as obviously right and it would have an off by one at each end of
 * every trip, which is exactly the kind of thing a generator finds and a hand written case does not.
 *
 * <p>Radiograph numbers are drawn from one to forty, digests from the hashes of zero to two hundred,
 * and days as offsets from the capture day: zero to thirty, and ten before to a hundred and twenty
 * after for the maintenance checks.
 */
class RegisterPropertiesTest {

    private static final LocalDate CAPTURED = LocalDate.of(2025, 7, 16);
    private static final List<Disposition> CLOSING =
            List.of(Disposition.REWORK, Disposition.USE_AS_IS, Disposition.REJECT);

    static Stream<Arguments> storesAndDisks() {
        return CollectionSamples.mapPairs(1, 40, 0, 200, 8, 200);
    }

    static Stream<Arguments> stores() {
        return CollectionSamples.maps(1, 40, 0, 200, 0, 8, 100);
    }

    static Stream<Arguments> storesWithSomethingInThem() {
        return CollectionSamples.maps(1, 40, 0, 200, 1, 8, 100);
    }

    /** The day the trip went out, how many days it ran on for, and the days written in the log. */
    static Stream<Arguments> tripsAndLogs() {
        return CollectionSamples.pairsWithASet(0, 30, 0, 10, 0, 30, 12, 200);
    }

    static Stream<Arguments> trips() {
        return Samples.longPairs(0, 30, 0, 10);
    }

    /** The day raised, the days until it closed, and the disposition it closed on. */
    static Stream<Arguments> closures() {
        return MoreSamples.longTriples(0, 30, 0, 90, 0, CLOSING.size() - 1)
                .map(Arguments::get)
                .map(values -> Arguments.of(values[0], values[1], CLOSING.get(((Long) values[2]).intValue())));
    }

    static Stream<Arguments> referenceSets() {
        return CollectionSamples.sets(1, 12, 1, 12, 100);
    }

    static Stream<Arguments> raisedAndAsked() {
        return Samples.longPairs(0, 30);
    }

    /**
     * The day stripped and the day asked about. The drawn pairs are joined by the pairs either side
     * of both ends of the quarterly, where an off by one would show.
     */
    static Stream<Arguments> strippedAndAsked() {
        Stream<Arguments> boundaries = Stream.of(0L, 1L, 28L).flatMap(stripped -> Stream.of(
                        stripped - 1,
                        stripped,
                        stripped + Inspection.QUARTERLY_DAYS - 1,
                        stripped + Inspection.QUARTERLY_DAYS)
                .filter(asked -> asked >= -10 && asked <= 120)
                .map(asked -> Arguments.of(stripped, asked)));
        return Stream.concat(boundaries, Samples.longPairs(0, 30, -10, 120));
    }

    private static LocalDate day(long offset) {
        return CAPTURED.plusDays(offset);
    }

    private static String number(long n) {
        return String.format(Locale.ROOT, "24-0001-R%04d", n);
    }

    private static String digest(long n) {
        return ImageStore.digestOf(Long.toString(n).getBytes(StandardCharsets.UTF_8));
    }

    private static StoredImage anImage(String number, String digest) {
        return new StoredImage(
                number,
                ImageStore.expectedName(number, ImageFormat.DICONDE),
                ImageFormat.DICONDE,
                16,
                2048,
                512,
                512,
                digest,
                LocalDateTime.of(2025, 7, 16, 8, 0),
                Purpose.RECORD);
    }

    private static Manifest aManifest(Map<Long, Long> held) {
        Manifest manifest = new Manifest("24-0001");
        held.forEach((n, d) -> manifest.add(anImage(number(n), digest(d))));
        return manifest;
    }

    /** What is on the disk: each file name, the radiograph number with its suffix, to its digest. */
    private static Map<String, String> onDisk(Map<Long, Long> seen) {
        Map<String, String> files = new LinkedHashMap<>();
        seen.forEach((n, d) -> files.put(number(n) + ".dcm", digest(d)));
        return files;
    }

    private static Use aUse(LocalDate on) {
        return new Use(on, "D-77", "IR-5512", "site", List.of("0142"));
    }

    private static Report aReport(String reference, LocalDate raisedOn) {
        return new Report(reference, "24-0001", "GW-104", "API 1104", "slag", raisedOn, "0142");
    }

    private static Trip aTrip(LocalDate went, long span) {
        return new Trip("IR-5512", "D-77", "0142", "site", went, went.plusDays(span));
    }

    /** The names on both lists. */
    private static Set<String> common(List<String> left, List<String> right) {
        Set<String> both = new HashSet<>(left);
        both.retainAll(right);
        return both;
    }

    @ParameterizedTest
    @MethodSource("storesAndDisks")
    void theThreeAnswersNeverOverlap(Map<Long, Long> held, Map<Long, Long> seen) {
        Discrepancy found = aManifest(held).verify(onDisk(seen));
        assertThat(common(found.missing(), found.unexpected())).isEmpty();
        assertThat(common(found.missing(), found.altered())).isEmpty();
        assertThat(common(found.unexpected(), found.altered())).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("storesAndDisks")
    void everyNameOnEitherSideIsAccountedFor(Map<Long, Long> held, Map<Long, Long> seen) {
        Manifest manifest = aManifest(held);
        Map<String, String> disk = onDisk(seen);
        Discrepancy found = manifest.verify(disk);
        Set<String> every = new HashSet<>(manifest.entries().keySet());
        every.addAll(disk.keySet());
        Set<String> matched = manifest.entries().keySet().stream()
                .filter(name -> disk.containsKey(name)
                        && disk.get(name).equals(manifest.entries().get(name).digest()))
                .collect(Collectors.toSet());
        Set<String> accounted = new HashSet<>(matched);
        accounted.addAll(found.missing());
        accounted.addAll(found.unexpected());
        accounted.addAll(found.altered());
        assertThat(accounted).isEqualTo(every);
    }

    @ParameterizedTest
    @MethodSource("storesWithSomethingInThem")
    void aStoreThatMatchesItselfIsAlwaysClean(Map<Long, Long> held) {
        Manifest manifest = aManifest(held);
        Map<String, String> disk = new LinkedHashMap<>();
        manifest.entries().forEach((name, image) -> disk.put(name, image.digest()));
        assertThat(manifest.verify(disk).isClean()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("stores")
    void theByteTotalIsTheSumOfWhatIsInIt(Map<Long, Long> held) {
        Manifest manifest = aManifest(held);
        assertThat(manifest.totalBytes()).isEqualTo(512L * manifest.size());
    }

    @ParameterizedTest
    @MethodSource("tripsAndLogs")
    void aGapIsADayOnTheTripThatIsNotInTheLog(long went, long span, Set<Long> logged) {
        UtilisationLog log = new UtilisationLog();
        logged.forEach(offset -> log.record(aUse(day(offset))));
        Set<LocalDate> found = log.unloggedDays(List.of(aTrip(day(went), span))).stream()
                .map(DeviceDay::day)
                .collect(Collectors.toSet());
        Set<LocalDate> uncovered = LongStream.rangeClosed(went, went + span)
                .filter(offset -> !logged.contains(offset))
                .mapToObj(RegisterPropertiesTest::day)
                .collect(Collectors.toSet());
        assertThat(found).isEqualTo(uncovered);
    }

    @ParameterizedTest
    @MethodSource("trips")
    void aFullyLoggedTripNeverHasAGap(long went, long span) {
        UtilisationLog log = new UtilisationLog();
        LongStream.rangeClosed(went, went + span).forEach(offset -> log.record(aUse(day(offset))));
        assertThat(log.unloggedDays(List.of(aTrip(day(went), span)))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("closures")
    void aClosedReportNeverReadsAsOverdueAfterwards(long raised, long closedAfter, Disposition disposition) {
        Report report = aReport("NCR-1", day(raised));
        report.decide(disposition, "CON-1", "SCRAP-1");
        LocalDate closed = day(raised).plusDays(closedAfter);
        report.close(closed, "0303");
        for (long extra : List.of(0L, 1L, 400L)) {
            assertThat(report.isOverdue(closed.plusDays(extra))).isFalse();
            assertThat(report.daysOpen(closed.plusDays(extra))).isEqualTo(closedAfter);
        }
    }

    @ParameterizedTest
    @MethodSource("referenceSets")
    void theOpenListAndTheClosedOnesAddUpToTheRegister(Set<Long> references) {
        Register register = new Register();
        int closed = 0;
        for (long index : references.stream().sorted().toList()) {
            String reference = "NCR-" + index;
            register.raiseReport(aReport(reference, CAPTURED));
            if (index % 2 == 0) {
                Report report = register.get(reference);
                report.decide(Disposition.REWORK);
                report.close(CAPTURED.plusDays(1), "0303");
                closed++;
            }
        }
        assertThat(register.openReports().size() + closed).isEqualTo(register.size());
    }

    @ParameterizedTest
    @MethodSource("raisedAndAsked")
    void daysOpenIsNeverNegative(long raised, long asked) {
        assertThat(aReport("NCR-1", day(raised)).daysOpen(day(asked))).isNotNegative();
    }

    @ParameterizedTest
    @MethodSource("strippedAndAsked")
    void aDeviceGoesOutExactlyWhileTheQuarterlyStillRuns(long stripped, long asked) {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(Inspection.quarterly("D-77", day(stripped), "0303"));
        register.record(Inspection.preUse("D-77", day(asked), "0142"));
        boolean inside = stripped <= asked && asked < stripped + Inspection.QUARTERLY_DAYS;
        assertThat(register.mayGoOut("D-77", day(asked))).isEqualTo(inside);
    }

    @ParameterizedTest
    @MethodSource("strippedAndAsked")
    void aDeviceNeverGoesOutWithoutACheckThatMorning(long stripped, long asked) {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(Inspection.quarterly("D-77", day(stripped), "0303"));
        assertThat(register.mayGoOut("D-77", day(asked))).isFalse();
    }

    @ParameterizedTest
    @MethodSource("strippedAndAsked")
    void aBrokenSafetyItemThatMorningAlwaysStopsIt(long stripped, long asked) {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(Inspection.quarterly("D-77", day(stripped), "0303"));
        register.record(
                Inspection.preUse("D-77", day(asked), "0142", List.of(new Defect(InspectionItem.GUIDE_TUBE, "split"))));
        assertThat(register.mayGoOut("D-77", day(asked))).isFalse();
    }
}
