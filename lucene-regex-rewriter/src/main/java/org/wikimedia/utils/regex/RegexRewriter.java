package org.wikimedia.utils.regex;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public final class RegexRewriter {
    public static final char START_ANCHOR_MARKER = '\uFDD0';
    public static final char END_ANCHOR_MARKER = '\uFDD1';

    private static final Map<Character, String> CHAR_CLASSES;
    private static final Map<Character, Character> ESCAPE_CODES;

    static {
        Map<Character, String> charClasses = new HashMap<>();
        charClasses.put('d', "0-9");
        charClasses.put('w', "A-Za-z0-9_");
        charClasses.put('s', "\f\n\r\t\u0011\u0020\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff");
        CHAR_CLASSES = Collections.unmodifiableMap(charClasses);

        Map<Character, Character> escapeCodes = new HashMap<>();
        escapeCodes.put('r', '\r');
        escapeCodes.put('n', '\n');
        escapeCodes.put('t', '\t');
        ESCAPE_CODES = Collections.unmodifiableMap(escapeCodes);
    }

    private RegexRewriter() {
    }

    /**
     * Applies the necessary transformation to string inputs when using replaceAnchors=true.
     */
    public static String anchorTransformation(String input) {
        return START_ANCHOR_MARKER + input + END_ANCHOR_MARKER;
    }

    /**
     * Rewrites the provided regex to support character classes and optionally anchors.
     * If anchor support is enabled then RegexRewriter.anchorTransformation must be applied
     * to strings to be checked.
     * It's perhaps inefficient that this rewrites the regex multiple times, but the
     * implementation is easier to reason about as separate transformations.
     */
    public static CharSequence rewrite(CharSequence regex, boolean replaceAnchors) {
        CharSequence result = replaceCharClasses(regex);
        if (replaceAnchors) {
            result = replaceAnchors(result);
        }
        // expandEscapeCodes must transform last so unicode expansions stay
        // literals and can't be interpreted as char classes.
        return expandEscapeCodes(result);
    }

    /**
     * Replaces anchors, unsupported by lucene regex, with reserved UTF8 characters.
     * By replacing the anchors in the regex and adding the anchor markers both in the rechecker and at
     * index time via a pattern_replace char_filter, we can offer full support for start and end anchors.
     */
    @SuppressWarnings({"CyclomaticComplexity", "NPathComplexity"})
    static CharSequence replaceAnchors(CharSequence input) {
        StringBuilder result = new StringBuilder();
        RegexParseState parse = new RegexParseState(input);

        for (int i = 0; i < input.length(); i++) {
            char c = parse.next(i);

            if (parse.inLiteral) {
                result.append(c);
            } else if (!parse.inCharClass && !parse.escaped && c == '^') {
                result.append(START_ANCHOR_MARKER);
            } else if (!parse.inCharClass && !parse.escaped && c == '$') {
                result.append(END_ANCHOR_MARKER);
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private static boolean isValidHexSubSequence(CharSequence input, int i, int len) {
        if (i + len > input.length()) {
            return false;
        }
        CharSequence maybeHex = input.subSequence(i, i + len);
        return  maybeHex.chars().allMatch((hexChar) -> Character.digit(hexChar, 16) >= 0);
    }

    @SuppressWarnings({"ModifiedControlVariable"})
    @SuppressFBWarnings(value = "MUI_CONTAINSKEY_BEFORE_GET", justification = "More obviously correct this way")
    static CharSequence expandEscapeCodes(CharSequence input) {
        StringBuilder result = new StringBuilder();
        RegexParseState parse = new RegexParseState(input);

        for (int i = 0; i < input.length(); i++) {
            char c = parse.next(i);

            if (parse.inLiteral) {
                result.append(c);
            } else if (parse.escaped && ESCAPE_CODES.containsKey(c)) {
                result.setLength(result.length() - 1);
                result.append(ESCAPE_CODES.get(c));
            } else if (parse.escaped && c == 'u' && isValidHexSubSequence(input, i + 1, 4)) {
                String hex = input.subSequence(i + 1, i + 5).toString();
                int cp = Integer.parseInt(hex, 16);
                result.setLength(result.length() - 1);
                // prepending \ treats it as a literal value.
                result.append('\\');
                result.append((char) cp);
                i += 4;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static String expandCharClass(CharSequence charClass) {
        if (charClass.length() == 0) {
            return "[]";
        }
        StringBuilder result = new StringBuilder("[");
        boolean negated = charClass.charAt(0) == '^';
        if (negated) {
            if (charClass.length() == 1) {
                return "[^]";
            }
            // negated must not match the anchors
            result.append('^').append(START_ANCHOR_MARKER).append(END_ANCHOR_MARKER);
        }

        int backslashCount = 0;
        for (int i = negated ? 1 : 0; i < charClass.length(); i++) {
            char c = charClass.charAt(i);

            boolean escaped = (backslashCount % 2) != 0;
            if (c == '\\') {
                backslashCount += 1;
            } else {
                backslashCount = 0;
            }

            String expandedCharClass = CHAR_CLASSES.get(c);
            if (escaped && expandedCharClass != null) {
                result.setLength(result.length() - 1);
                result.append(expandedCharClass);
            } else {
                result.append(c);
            }
        }
        return result.append(']').toString();
    }

    @SuppressWarnings({"CyclomaticComplexity", "NPathComplexity"})
    @SuppressFBWarnings(value = "MUI_CONTAINSKEY_BEFORE_GET", justification = "More obviously correct this way")
     static CharSequence replaceCharClasses(CharSequence input) {
        StringBuilder result = new StringBuilder();
        RegexParseState parse = new RegexParseState(input);
        int charClassStart = -1;

        for (int i = 0; i < input.length(); i++) {
            char c = parse.next(i);

            if (parse.inLiteral) {
                result.append(c);
            } else if (parse.inCharClass && charClassStart == -1) {
                charClassStart = i + 1;
            } else if (!parse.inCharClass && charClassStart != -1) {
                // While expansion could be mixed into this function, it does a similar walk, it
                // seemed less confusing to separate into a dedicated routine.
                result.append(expandCharClass(input.subSequence(charClassStart, i)));
                charClassStart = -1;
            } else if (!parse.inCharClass && !parse.escaped && c == '.') {
                // . must not match the anchors
                result.append("[^").append(START_ANCHOR_MARKER).append(END_ANCHOR_MARKER).append(']');
            } else if (!parse.inCharClass && parse.escaped && CHAR_CLASSES.containsKey(c)) {
                result.setLength(result.length() - 1);
                result.append('[').append(CHAR_CLASSES.get(c)).append(']');
            } else if (!parse.inCharClass) {
                result.append(c);
            }
        }
        if (parse.inCharClass) {
            // unclosed char class
            result.append('[').append(input.subSequence(charClassStart, input.length()));
        }

        return result.toString();
    }

    private static class RegexParseState {
        final CharSequence input;
        boolean escaped;
        boolean inCharClass;
        boolean inLiteral;
        int backslashCount;

        RegexParseState(CharSequence input) {
            this.input = input;
        }

        @SuppressWarnings({"CyclomaticComplexity"})
        char next(int i) {
            char c = input.charAt(i);

            // Count the number of backslashes preceding this character
            escaped = (backslashCount % 2) != 0;
            if (c == '\\') {
                backslashCount += 1;
            } else {
                backslashCount = 0;
            }

            if (!inLiteral && !inCharClass && !escaped && c == '"') {
                inLiteral = true;
            } else if (inLiteral && c == '"') {
                inLiteral = false;
            }

            if (!inLiteral && !escaped) {
                if (c == '[') {
                    inCharClass = true;
                } else if (c == ']' && inCharClass) {
                    inCharClass = false;
                }
            }

            return c;
        }
    }
}
