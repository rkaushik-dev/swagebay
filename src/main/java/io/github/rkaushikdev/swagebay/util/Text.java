package io.github.rkaushikdev.swagebay.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Text handling where Unicode white space counts as white space.
 *
 * <p>Values arrive typed at a terminal, pasted from a spreadsheet or read off lead tape, and a
 * no-break space (U+00A0) or an ideographic space is as much padding as an ordinary one. Here
 * white space is every space separator, line and paragraph separator, the ASCII controls TAB, LF,
 * VT, FF, CR and the information separators U+001C to U+001F, and NEXT LINE (U+0085).
 */
public final class Text {

    private Text() {}

    /** Whether the code point is white space in the sense above. */
    public static boolean isSpace(int point) {
        return Character.isWhitespace(point) || Character.isSpaceChar(point) || point == 0x85;
    }

    /** The text without white space at either end. */
    public static String strip(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    /** Whether the text is empty or nothing but white space. */
    public static boolean isBlank(String text) {
        return strip(text).isEmpty();
    }

    /** The words of the text, split on runs of white space, none of them empty. */
    public static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        text.codePoints().forEach(point -> {
            if (isSpace(point)) {
                if (!word.isEmpty()) {
                    words.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.appendCodePoint(point);
            }
        });
        if (!word.isEmpty()) {
            words.add(word.toString());
        }
        return List.copyOf(words);
    }
}
