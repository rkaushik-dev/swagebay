package io.github.rkaushikdev.swagebay.imaging;

/**
 * The backscatter check: a lead letter B on the back of the cassette, as the reader found it.
 *
 * <p>Backscatter is the one nobody can calculate, so it is measured. If the letter's image shows
 * through as a lighter mark on the developed film, radiation is coming back off the floor and the
 * shot has to be repeated with a lead sheet behind it. A darker letter is not backscatter.
 *
 * @param letterVisible whether the letter shows on the film at all
 * @param lighterThanBackground whether it shows lighter than the film around it
 */
public record BackscatterTest(boolean letterVisible, boolean lighterThanBackground) {

    /** Only a lighter image is a failure. */
    public boolean failed() {
        return letterVisible && lighterThanBackground;
    }

    /** What the reader writes down. */
    public String verdict() {
        if (!letterVisible) {
            return "no backscatter";
        }
        if (!lighterThanBackground) {
            return "the letter shows darker, which is not backscatter";
        }
        return "backscatter, put lead behind the cassette and shoot it again";
    }
}
