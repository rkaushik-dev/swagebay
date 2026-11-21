package io.github.rkaushikdev.swagebay.imaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BadpixelsTest {

    private static BadPixelMap panel(Set<Pixel> pixels, int columns, int rows) {
        return new BadPixelMap(columns, rows, pixels);
    }

    private static BadPixelMap panel(Set<Pixel> pixels) {
        return panel(pixels, 100, 100);
    }

    /** Every pixel in the rectangle, columns and rows both half open. */
    private static Set<Pixel> block(int fromColumn, int toColumn, int fromRow, int toRow) {
        Set<Pixel> pixels = new HashSet<>();
        for (int c = fromColumn; c < toColumn; c++) {
            for (int r = fromRow; r < toRow; r++) {
                pixels.add(new Pixel(c, r));
            }
        }
        return pixels;
    }

    private static Set<Pixel> scattered() {
        Set<Pixel> pixels = new HashSet<>();
        for (int c = 0; c < 10; c++) {
            for (int r = 0; r < 10; r++) {
                pixels.add(new Pixel(c * 3, r * 3));
            }
        }
        return pixels;
    }

    private static Set<Pixel> pixels(int... columnsAndRows) {
        Set<Pixel> pixels = new HashSet<>();
        for (int i = 0; i < columnsAndRows.length; i += 2) {
            pixels.add(new Pixel(columnsAndRows[i], columnsAndRows[i + 1]));
        }
        return pixels;
    }

    @Test
    void scatteredSinglePixelsAreAThousandClustersOfOne() {
        BadPixelMap board = panel(scattered());
        assertThat(board.total()).isEqualTo(100);
        List<Cluster> clusters = board.clusters();
        assertThat(clusters).hasSize(100);
        assertThat(clusters).allMatch(c -> c.size() == 1);
    }

    @Test
    void scatteredSinglePixelsPassHoweverManyThereAre() {
        assertThat(panel(scattered()).passes()).isTrue();
    }

    @Test
    void aThreeByThreeBlockIsOneClusterOfNine() {
        BadPixelMap board = panel(block(10, 13, 10, 13));
        assertThat(board.clusters()).hasSize(1);
        Cluster biggest = board.largestCluster().orElseThrow();
        assertThat(biggest.size()).isEqualTo(9);
        assertThat(biggest.width()).isEqualTo(3);
        assertThat(biggest.height()).isEqualTo(3);
        assertThat(biggest.isLine()).isFalse();
    }

    @Test
    void aBlockThatSizeFailsTheDefaultPolicy() {
        List<String> violations = panel(block(10, 13, 10, 13)).violations();
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("a cluster of 9 pixels");
    }

    @Test
    void diagonalNeighboursCountAsConnected() {
        BadPixelMap board = panel(pixels(5, 5, 6, 6, 7, 7));
        assertThat(board.clusters()).hasSize(1);
        assertThat(board.largestCluster().orElseThrow().size()).isEqualTo(3);
    }

    @Test
    void aGapOfTwoBreaksTheConnection() {
        assertThat(panel(pixels(5, 5, 8, 8)).clusters()).hasSize(2);
    }

    @Test
    void aDeadReadoutLineIsRecognisedAsALine() {
        BadPixelMap board = panel(block(20, 21, 30, 45));
        List<Cluster> found = board.lines();
        assertThat(found).hasSize(1);
        assertThat(found.get(0).length()).isEqualTo(15);
        assertThat(found.get(0).width()).isEqualTo(1);
        assertThat(found.get(0).isLine()).isTrue();
    }

    @Test
    void aLongLineFailsWhereACompactClusterOfTheSameAreaWouldNot() {
        BadPixelMap line = panel(block(20, 21, 30, 45));
        assertThat(line.passes()).isFalse();
        assertThat(line.violations().get(0)).contains("a bad line of 15 pixels");

        BadPixelMap compact = panel(block(10, 14, 10, 14));
        BadPixelPolicy loose = new BadPixelPolicy(new BigDecimal("0.02"), 20, 10);
        assertThat(compact.passes(loose)).isTrue();
    }

    @Test
    void aShortLineInsideTheLimitPasses() {
        BadPixelMap shortLine = panel(block(20, 21, 30, 38));
        assertThat(shortLine.lines().get(0).length()).isEqualTo(8);
        assertThat(shortLine.passes()).isTrue();
    }

    @Test
    void aSinglePixelIsNotALine() {
        BadPixelMap board = panel(pixels(5, 5));
        assertThat(board.lines()).isEmpty();
        assertThat(board.largestCluster().orElseThrow().size()).isEqualTo(1);
        assertThat(board.largestCluster().orElseThrow().isLine()).isFalse();
    }

    @Test
    void theFractionIsMeasuredAgainstTheWholePanel() {
        BadPixelMap board = panel(block(0, 50, 0, 1), 100, 100);
        assertThat(board.fraction()).isEqualByComparingTo("0.005");
        assertThat(board.total()).isEqualTo(50);
    }

    @Test
    void tooManyBadPixelsFailsOnTheFractionAlone() {
        Set<Pixel> many = new HashSet<>();
        for (int c = 0; c < 10; c++) {
            for (int r = 0; r < 60; r += 2) {
                many.add(new Pixel(c, r));
            }
        }
        BadPixelMap board = panel(many, 20, 60);
        assertThat(board.fraction()).isGreaterThan(new BigDecimal("0.02"));
        assertThat(board.violations()).anyMatch(v -> v.contains("of the panel"));
    }

    @Test
    void clustersComeBackLargestFirst() {
        Set<Pixel> pixels = pixels(1, 1, 5, 5, 5, 6, 5, 7, 9, 9, 9, 10);
        assertThat(panel(pixels).clusters()).extracting(Cluster::size).containsExactly(3, 2, 1);
    }

    @Test
    void anEmptyMapIsClean() {
        BadPixelMap board = panel(Set.of());
        assertThat(board.total()).isZero();
        assertThat(board.clusters()).isEmpty();
        assertThat(board.largestCluster()).isEmpty();
        assertThat(board.passes()).isTrue();
        assertThat(board.fraction()).isEqualByComparingTo("0");
    }

    @Test
    void aPixelOffThePanelIsRefused() {
        assertThatThrownBy(() -> panel(pixels(100, 5)))
                .isInstanceOfSatisfying(ValidationError.class, caught -> assertThat(caught.context())
                        .containsEntry("column", 100));
        assertThatThrownBy(() -> panel(pixels(-1, 5))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new BadPixelMap(0, 10, Set.of())).isInstanceOf(ValidationError.class);
    }

    @Test
    void aPolicyHasToMakeSense() {
        assertThatThrownBy(() -> new BadPixelPolicy(new BigDecimal("1.5"), 5, 10))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new BadPixelPolicy(new BigDecimal("0.02"), 0, 10))
                .isInstanceOf(ValidationError.class);
        assertThat(BadPixelPolicy.DEFAULT.maxCluster()).isEqualTo(5);
    }
}
