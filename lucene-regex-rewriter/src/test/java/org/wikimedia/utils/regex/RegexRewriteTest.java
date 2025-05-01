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
}
