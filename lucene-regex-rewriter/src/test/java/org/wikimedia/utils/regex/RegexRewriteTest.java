package org.wikimedia.utils.regex;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegexRewriteTest {

    private void assertCharClassReplacement(String expected, String regex) {
        assertThat(RegexRewriter.replaceCharClasses(regex)).isEqualTo(expected);
    }

    private void assertNoCharClassReplacement(String regex) {
        assertThat(RegexRewriter.replaceCharClasses(regex)).isEqualTo(regex);
    }

    @Test
    void testNoCharClassReplacement() {
        assertNoCharClassReplacement("[]");
        assertNoCharClassReplacement("[^]");
        assertNoCharClassReplacement("abc");
        assertNoCharClassReplacement("[abc]");
        assertNoCharClassReplacement("[\\]]");
        assertNoCharClassReplacement("[\\q]");
        assertNoCharClassReplacement("[[]");
        // escaped [ or ] should pass through
        assertNoCharClassReplacement("\\[[abc]");
        assertNoCharClassReplacement("[\\[abc]");
        assertNoCharClassReplacement("[abc\\]]");
        assertNoCharClassReplacement("[abc]\\]");
        // multiple backslashes
        assertNoCharClassReplacement("\\\\d");
        assertNoCharClassReplacement("\\\\\\\\d");
        assertNoCharClassReplacement("[\\\\d]");
        assertCharClassReplacement("[^\uFDD0\uFDD1\\\\d]", "[^\\\\d]");
        // nested or overlapping
        assertNoCharClassReplacement("[[abc]]");
        // Incomplete or broken regex
        assertNoCharClassReplacement("\\");
        assertNoCharClassReplacement("[\\");
        assertNoCharClassReplacement("abc\\");
        assertNoCharClassReplacement("[");
        assertNoCharClassReplacement("[^");
        assertNoCharClassReplacement("[a-");
        assertNoCharClassReplacement("[-z]");
        assertNoCharClassReplacement("[a-z");
        assertNoCharClassReplacement("[\\d");
        assertNoCharClassReplacement("[\\d\\");
        assertNoCharClassReplacement("[\\d\\]");
    }

    @Test
    void testSingleCharClassReplacement() {
        assertCharClassReplacement("[0-9]", "\\d");
        assertCharClassReplacement("[0-9]", "[\\d]");
        // negative char classes always exclude the anchors
        assertCharClassReplacement("[^\uFDD0\uFDD10-9]", "[^\\d]");
        assertCharClassReplacement("[0-9]+", "\\d+");
        assertCharClassReplacement("[0-9]+", "[\\d]+");
        assertCharClassReplacement("[^\uFDD0\uFDD10-9]+", "[^\\d]+");
        assertCharClassReplacement("[^\uFDD0\uFDD1[0-9]", "[^[\\d]");
        assertCharClassReplacement("[^\uFDD0\uFDD1[0-9]]", "[^[\\d]]");
        assertCharClassReplacement("[^\uFDD0\uFDD1[0-9\\]]", "[^[\\d\\]]");
        assertCharClassReplacement("[[^0-9]]", "[[^\\d]]");
        assertCharClassReplacement("[\\]0-9]", "[\\]\\d]");
        assertCharClassReplacement("[0-9\\]]", "[\\d\\]]");
        assertCharClassReplacement("\\[[0-9]", "\\[[\\d]");

        assertCharClassReplacement("[A-Za-z0-9_]", "\\w");
        assertCharClassReplacement("[A-Za-z0-9_]", "[\\w]");
        assertCharClassReplacement("[^\uFDD0\uFDD1A-Za-z0-9_]", "[^\\w]");

        assertCharClassReplacement("[\f\n\r\t\u0011\u0020\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]", "\\s");
        assertCharClassReplacement("[\f\n\r\t\u0011\u0020\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]", "[\\s]");
        assertCharClassReplacement("[^\uFDD0\uFDD1\f\n\r\t\u0011\u0020\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]", "[^\\s]");

        assertCharClassReplacement("[^\uFDD0\uFDD1]", ".");
    }

    @Test
    void testNegativeCharClassAnchors() {
        assertCharClassReplacement("[^\uFDD0\uFDD1q]", "[^q]");
        assertNoCharClassReplacement("[^]");
    }

    @Test
    void testPassthruUppercase() {
        // uppercase variants are not supported. It was too awkard to allow something like [\w\D], and thus for
        // consistency we don't allow them anywhere. Users can send [^\d] as the equiv of \D.
        assertNoCharClassReplacement("\\D");
        assertNoCharClassReplacement("[\\D]");
        // negative char classes always include the anchors
        assertCharClassReplacement("[^\uFDD0\uFDD1\\D]", "[^\\D]");
        assertNoCharClassReplacement("\\S");
        assertNoCharClassReplacement("[\\S]");
        assertCharClassReplacement("[^\uFDD0\uFDD1\\S]", "[^\\S]");
        assertNoCharClassReplacement("\\W");
        assertNoCharClassReplacement("[\\W]");
        assertCharClassReplacement("[^\uFDD0\uFDD1\\W]", "[^\\W]");
    }

    @Test
    void testQuotedLiterals() {
        // empty quoted literal passes through
        assertNoCharClassReplacement("\"\"");
        // Quotes define literals that should not be expanded
        assertNoCharClassReplacement("\".\"");
        assertCharClassReplacement("[^\uFDD0\uFDD1]\".\"[^\uFDD0\uFDD1]", ".\".\".");
        // source query is invalid with unpaired quotes. We passthru to let next stage fail
        assertNoCharClassReplacement("\"unclosed");
        // Quotes inside a char class do not start a literal
        assertCharClassReplacement("[^\uFDD0\uFDD1\"]", "[^\"]");
        // escaped quotes do not start a literal
        assertCharClassReplacement("\\\"[^\uFDD0\uFDD1]", "\\\".");
        // expands shorthands on each edge of the literal
        assertCharClassReplacement("[0-9]\"abc\"", "\\d\"abc\"");
        assertCharClassReplacement("\"abc\"[0-9]", "\"abc\"\\d");
        // no shorthand expansion inside the literal
        assertNoCharClassReplacement("\"\\s\\d\\w\"");
        // unquoted anchors are replaced when quotes are present
        assertAnchorReplacement("\uFDD0\"^$\"\uFDD1", "^\"^$\"$");
        // quoted anchors are literals, not to be expanded
        assertNoAnchorReplacement("\"^$\"");
        // anchors also passthru unclosed quotes to next layer
        assertAnchorReplacement("\uFDD0\"^$", "^\"^$");
        // Escaped backslash before quote (should start literal)
        assertNoCharClassReplacement("\\\\\"literal\"");
        // Double-escaped quote (should expand the dot)
        assertCharClassReplacement("\\\\\\\"[^\uFDD0\uFDD1]", "\\\\\\\".");
        // Quote escaped with multiple backslashes
        assertCharClassReplacement("\\\\\\\\\\\"[^\uFDD0\uFDD1]", "\\\\\\\\\\\".");
        // Quote at different positions in char class
        assertNoCharClassReplacement("[\".]");
        assertCharClassReplacement("[^\uFDD0\uFDD1.\"]", "[^.\"]");
        assertNoCharClassReplacement("[a\".z]");
        // Multiple quotes in char class
        assertNoCharClassReplacement("[\"\".]");
        // Quote at end of string
        assertNoCharClassReplacement("pattern\"");
        assertNoAnchorReplacement("pattern\"");
        // Only quote character
        assertNoCharClassReplacement("\"");
        assertNoAnchorReplacement("\"");
        // Quote after escape at end
        assertNoCharClassReplacement("pattern\\\"");
        // quote inside the literal cant be escaped, the escape is literal
        assertCharClassReplacement("\"abc\\\"[0-9]", "\"abc\\\"\\d");
        assertAnchorReplacement("\"abc\\\"\uFDD1", "\"abc\\\"$");
    }

    @Test
    void testUnknownEscapeSequences() {
        assertCharClassReplacement("[0-9]\\q", "\\d\\q");
        assertCharClassReplacement("[0-9\\q]", "[\\d\\q]");
        assertCharClassReplacement("[^\uFDD0\uFDD10-9\\q]", "[^\\d\\q]");
    }

    @Test
    void testMixedCharClassReplacement() {
        assertCharClassReplacement("[a0-9z]", "[a\\dz]");
        assertCharClassReplacement("[a0-9]", "[a\\d]");
        assertCharClassReplacement("[0-9z]", "[\\dz]");
        assertCharClassReplacement("[0-9A-Za-z0-9_]", "[\\d\\w]");
        assertCharClassReplacement("[^\uFDD0\uFDD10-9A-Za-z0-9_]", "[^\\d\\w]");
        assertCharClassReplacement("[0-9][0-9]", "\\d\\d");
        assertCharClassReplacement("[a-z0-9]", "[a-z\\d]");
        assertCharClassReplacement("[0-9a-z]", "[\\da-z]");
        assertCharClassReplacement("[0-9\\]]", "[\\d\\]]");
        // It was already invalid, it stays invalid but differently?
        assertCharClassReplacement("[A-0-9]", "[A-\\d]");
    }

    private void assertAnchorReplacement(String expected, String regex) {
        assertThat(RegexRewriter.replaceAnchors(regex)).isEqualTo(expected);
    }

    private void assertNoAnchorReplacement(String regex) {
        assertThat(RegexRewriter.replaceAnchors(regex)).isEqualTo(regex);
    }

    @Test
    void testBasicAnchors() {
        assertAnchorReplacement("\uFDD0abc\uFDD1", "^abc$");
    }

    @Test
    void testEscapedAnchors() {
        assertNoAnchorReplacement("\\^abc\\$");
        assertNoAnchorReplacement("foo\\^bar\\$");
    }

    @Test
    void testCharacterClass() {
        assertNoAnchorReplacement("[^a^b$]");
    }

    @Test
    void testMixedCases() {
        assertAnchorReplacement("Start\uFDD0Middle\uFDD1End", "Start^Middle$End");
        assertAnchorReplacement("[a-z]\uFDD0test\uFDD1", "[a-z]^test$");
    }

    @Test
    void testBackslashEscapeCounts() {
        // don't replace escaped ^
        assertNoAnchorReplacement("foo\\^bar");
        // unless it's doubled up
        assertAnchorReplacement("foo\\\\\uFDD0bar", "foo\\\\^bar");
        // back to no replace with triple
        assertNoAnchorReplacement("foo\\\\\\^bar");
    }

    private void assertEscapeExpansion(String expected, String regex) {
        assertThat(RegexRewriter.expandEscapeCodes(regex)).isEqualTo(expected);
    }

    private void assertNoEscapeExpansion(String regex) {
        assertThat(RegexRewriter.expandEscapeCodes(regex)).isEqualTo(regex);
    }

    @Test
    void testEscapeExpansionLiteralString() {
        // empty literal passthru
        assertNoEscapeExpansion("\"\"");
        // escape in a quoted literal should not expand
        assertNoEscapeExpansion("\"\\t\"");
        // multiple quoted literals should not expand
        assertNoEscapeExpansion("\"\\t\" \"\\t\"");
        // unclosed literal should not expand
        assertNoEscapeExpansion("\"\\t");
        // should expand before a literal
        assertEscapeExpansion("\n\"\\n\"", "\\n\"\\n\"");
        // should expand after a literal
        assertEscapeExpansion("\"\\n\"\n", "\"\\n\"\\n");
        // should expand between quoted literals
        assertEscapeExpansion("\"\\t\"\n\"\\t\"", "\"\\t\"\\n\"\\t\"");
    }

    @Test
    void testEscapeExpansionCharClasses() {
        // unclosed character class
        assertEscapeExpansion("[\t", "[\\t");
        // empty character class
        assertNoEscapeExpansion("[]");
        // escape in a character class should expand
        assertEscapeExpansion("[\r\n]", "[\\r\\n]");
        // nested char class (invalid but should handle)
        assertEscapeExpansion("[\t[\n]]", "[\\t[\\n]]");
    }

    @Test
    void testEscapeExpansionUnicodeEdgeCases() {
        // Unicode at end of string (incomplete)
        assertNoEscapeExpansion("\\u123");
        assertNoEscapeExpansion("\\u12");
        assertNoEscapeExpansion("\\u1");
        assertNoEscapeExpansion("\\u");

        // Unicode with uppercase hex
        assertEscapeExpansion("\\\u000C", "\\u000C");
        assertEscapeExpansion("\\\u00FF", "\\u00FF");

        // Unicode with mixed case
        assertEscapeExpansion("\\\u00Af", "\\u00Af");

        // Multiple unicode escapes
        assertEscapeExpansion("\\\u0009\\\n", "\\u0009\\u000A");

        // Unicode zero
        assertEscapeExpansion("\\\u0000", "\\u0000");

        // Unicode with non-hex after valid 4 chars
        assertEscapeExpansion("\\\u0009g", "\\u0009g");

        // Invalid hex characters
        assertNoEscapeExpansion("\\u000g");
        assertNoEscapeExpansion("\\u00g0");
        assertNoEscapeExpansion("\\ug000");
        assertNoEscapeExpansion("\\u000G"); // uppercase G invalid

        // invalid followed by valid
        assertEscapeExpansion("\\u00\\\u000e", "\\u00\\u000e");
    }

    @Test
    void testEscapeExpansionBackslashCount() {
        // only backslashes
        assertNoEscapeExpansion("\\");
        assertNoEscapeExpansion("\\\\");
        // single backslash
        assertEscapeExpansion("\n", "\\n");
        // double backslash
        assertNoEscapeExpansion("\\\\n");
        // triple backslash
        assertEscapeExpansion("\\\\\n", "\\\\\\n");
    }

    @Test
    void testEscapeExpansionStateTransitions() {
        // Transition from literal to character class
        assertEscapeExpansion("\"abc\"[\t]", "\"abc\"[\\t]");
        // Transition from character class to literal
        assertEscapeExpansion("[\t]\"\\n\"", "[\\t]\"\\n\"");
        // Multiple state changes in one regex
        assertEscapeExpansion("\\\\t\"\\n\"[\n]\\\\t", "\\\\t\"\\n\"[\\n]\\\\t");
    }

    @Test
    void testEscapeExpansionErrorRecovery() {
        // Malformed but should continue processing
        assertEscapeExpansion("\\uabcq\t", "\\uabcq\\t");
        // Mixed valid and invalid
        assertEscapeExpansion("\\\u0009\\uabcq\n", "\\u0009\\uabcq\\n");
    }

    @Test
    void testBasicEscapeSequenceExpansion() {
        // escape with nothing after should passthru
        assertNoEscapeExpansion("\\");
        // no defined expansion
        assertNoEscapeExpansion("\\q\\.");
        // simple expansion
        assertEscapeExpansion("\t\r\n", "\\t\\r\\n");
        // unicode escapes should expand
        assertEscapeExpansion("\\\u000c", "\\u000c");
        // unicode escapes with non-hex value should passthru
        assertNoEscapeExpansion("\\uabcq");
        // short unicode escape should passthru
        assertNoEscapeExpansion("\\u00");
    }


    @Test
    void testMultipleAnchors() {
        assertAnchorReplacement("\uFDD0abc\uFDD1|\uFDD0def\uFDD1", "^abc$|^def$");
        assertAnchorReplacement("\uFDD0(abc|def)\uFDD1", "^(abc|def)$");
    }

    @Test
    void testEdgeCases() {
        assertNoAnchorReplacement("");
        assertNoAnchorReplacement("\\");
        assertAnchorReplacement("\uFDD0\uFDD1", "^$");
    }

    @Test
    void testUnicodeSurrogatePairs() {
        // unicode escapes for characters beyond BMP
        // (joined with + because otherwise the compiler complains of illegal escape character)
        assertEscapeExpansion("\\\uD835" + "\\\uDC00", "\\uD835\\uDC00"); // Mathematical bold A
        assertEscapeExpansion("\\\uD83D" + "\\\uDE00", "\\uD83D\\uDE00"); // Grinning face emoji
        assertEscapeExpansion("\\\uD835" + "\\\uDFCF" + "\\\uD835" + "\\\uDFD0", "\\uD835\\uDFCF\\uD835\\uDFD0"); // Mathematical bold digits
        // incomplete surrogate pairs (they expand! maybe not ideal). In testing the
        // downstream regex engine will not match half a surrogate pair.
        assertEscapeExpansion("\\\uD83D", "\\uD83D"); // High surrogate without low
        assertEscapeExpansion("\\\uDE00", "\\uDE00"); // Low surrogate without high
        // invalid surrogate sequences (also expands! also won't match anything).
        assertEscapeExpansion("\\\uuD83D\n", "\\uD83D\\n"); // High surrogate + regular escape
        // surrogate pairs in character classes (see also RegexEquivalenceTest.testUnicode)
        assertEscapeExpansion("[\\\uD83D" + "\\\uDE00]", "[\\uD83D\\uDE00]");
        // surrogate pairs in quoted literals (should not expand)
        assertNoEscapeExpansion("\"\\uD83D\\uDE00\"");
    }

    @Test
    void testMixedEscapeTypes() {
        // Mix unicode and regular escapes for same character
        assertEscapeExpansion("\\\n\n", "\\u000A\\n");
        assertEscapeExpansion("\\\t\t", "\\u0009\\t");
        assertEscapeExpansion("\\\r\r", "\\u000D\\r");

        // Mix valid and invalid unicode escapes
        assertEscapeExpansion("\\u00\n", "\\u00\\n");
        assertEscapeExpansion("\n\\uabcg", "\\n\\uabcg");

        // Escaped backslash before unicode
        assertNoEscapeExpansion("\\\\u000A");
        assertEscapeExpansion("\\\\\n", "\\\\\\n");

        // Multiple mixed escapes in sequence
        assertEscapeExpansion("\t\\u00g0\r\n", "\\t\\u00g0\\r\\n");

        // Mixed escapes in character classes
        assertEscapeExpansion("[\\\t\n]", "[\\u0009\\n]");
        assertEscapeExpansion("[\t\\u00g0]", "[\\t\\u00g0]");
    }

    @Test
    void testComplexCharacterClassRanges() {
        // Invalid ranges with character class shortcuts
        assertCharClassReplacement("[A-0-9]", "[A-\\d]"); // Already covered but worth grouping
        assertCharClassReplacement("[0-9-z]", "[\\d-z]");
        assertCharClassReplacement("[a-0-9-Z]", "[a-\\d-Z]");

        // Shorthand at range boundaries (invalid but should handle gracefully)
        assertCharClassReplacement("[a-0-9z]", "[a-\\dz]");
        assertCharClassReplacement("[0-9-z]", "[\\d-z]");

        // Multiple ranges with shortcuts
        assertCharClassReplacement("[a-z0-9A-Z]", "[a-z\\dA-Z]");
        assertCharClassReplacement("[0-9a-zA-ZA-Za-z0-9_]", "[\\da-zA-Z\\w]");

        // Negated complex ranges
        assertCharClassReplacement("[^\uFDD0\uFDD1a-z0-9]", "[^a-z\\d]");
        assertCharClassReplacement("[^\uFDD0\uFDD1A-Za-z0-9_a-z]", "[^\\wa-z]");

        // Edge case: shorthand immediately after range dash
        assertCharClassReplacement("[a-0-9]", "[a-\\d]");
        assertCharClassReplacement("[z-A-Za-z0-9_]", "[z-\\w]");

        // Multiple shortcuts in ranges
        assertCharClassReplacement("[0-9A-Za-z0-9_\f\n\r\t\u0011\u0020\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]", "[\\d\\w\\s]");
    }

    @Test
    void testPathologicalBackslashes() {
        // Many consecutive backslashes with shortcuts
        String twoBackslashes = "\\\\d";
        String fourBackslashes = "\\\\\\\\d";
        String sixBackslashes = "\\\\\\\\\\\\d";

        // two backslashes
        assertNoCharClassReplacement("\\\\d");
        // four backslashes
        assertNoCharClassReplacement("\\\\\\\\d");  // \\\\d -> two literal backslashes + \d expansion
        // six backslashes
        assertNoCharClassReplacement("\\\\\\\\\\\\d");  // \\\\\\d -> three literal backslashes + d

        // Pathological backslashes with anchors
        assertNoAnchorReplacement("\\\\\\^");    // Escaped backslash + escaped anchor
        assertAnchorReplacement("\\\\\uFDD0", "\\\\^");  // Two backslashes + anchor
        assertNoAnchorReplacement("\\\\\\^");  // Three backslashes + escaped anchor

        // Pathological backslashes with unicode
        assertNoEscapeExpansion("\\\\u000A");      // 2 backslashes + unicode (no expansion)
        assertEscapeExpansion("\\\\\\\n", "\\\\\\u000A");  // 3 backslashes + unicode (expands)
        assertNoEscapeExpansion("\\\\\\\\u000A");  // 4 backslashes + unicode (no expansion)

        // Long sequences
        String manyBackslashes = "\\\\\\\\\\\\\\\\\\\\"; // 10 backslashes
        assertNoCharClassReplacement(manyBackslashes + "d");
        assertCharClassReplacement(manyBackslashes + "[0-9]", manyBackslashes + "\\d");

        // Backslashes at end of constructs
        assertNoCharClassReplacement("[abc\\\\]");
        assertNoAnchorReplacement("test\\\\");
        assertNoEscapeExpansion("pattern\\\\");
    }
}
