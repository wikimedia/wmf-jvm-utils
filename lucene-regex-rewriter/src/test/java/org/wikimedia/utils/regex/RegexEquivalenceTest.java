package org.wikimedia.utils.regex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.RegExp;
import org.junit.jupiter.api.Test;

class RegexEquivalenceTest {

    private CharacterRunAutomaton buildLuceneRegex(String input, boolean replaceAnchors) {
        CharSequence rewritten = RegexRewriter.rewrite(input, replaceAnchors);
        RegExp regex = new RegExp(".*(" + rewritten + ").*");
        Automaton automaton = regex.toAutomaton();
        return new CharacterRunAutomaton(automaton);
    }

    private void assertPatternMatch(Map<String, String> sources, String regex, String... expected) {
        // First verify the test case is correct by using java regex
        Pattern pattern = Pattern.compile(regex);
        // Then run our modified lucene regex to verify the same output
        boolean replaceAnchors = true;
        CharacterRunAutomaton charRun = buildLuceneRegex(regex, replaceAnchors);
        UnaryOperator<String> valueTransform = replaceAnchors ? RegexRewriter::anchorTransformation : s -> s;

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            boolean expectMatch = Arrays.stream(expected).anyMatch(docid -> docid.equals(entry.getKey()));

            Matcher javaMatch = pattern.matcher(entry.getValue());
            assertThat(javaMatch.find())
                .describedAs("java regex `%s` against `%s`:`%s`", regex, entry.getKey(), entry.getValue())
                .isEqualTo(expectMatch);

            boolean luceneMatch = charRun.run(valueTransform.apply(entry.getValue()));
            assertThat(luceneMatch)
                .describedAs("lucene regex `%s` against `%s`:`%s`", regex, entry.getKey(), entry.getValue())
                .isEqualTo(expectMatch);
        }
    }

    private void assertNoPatternMatch(Map<String, String> sources, String regex) {
        assertPatternMatch(sources, regex);
    }

    @Test
    void testPatternEquivalence() {
        Map<String, String> sources = new HashMap<>();
        sources.put("findme", "abcdef");
        sources.put("numbers", "12345");
        sources.put("edgecase1", "Start^Middle$End");
        sources.put("edgecase2", "^foo bar$");

        // Basic start anchor
        assertPatternMatch(sources, "^abc", "findme");
        // No match if it's not the start of the string
        assertNoPatternMatch(sources, "^bc");
        // Basic end anchor
        assertPatternMatch(sources, "ef$", "findme");
        // No match if it's not the end of the string
        assertNoPatternMatch(sources, "de$");
        // We can match the plain ^ character with proper regex escaping
        assertPatternMatch(sources, "Start\\^", "edgecase1");
        assertPatternMatch(sources, "Start[\\^]", "edgecase1");
        // The unescaped ^ is an anchor and fails to match
        assertNoPatternMatch(sources, "Start^");
        // Same for plain $
        assertPatternMatch(sources, "Middle\\$", "edgecase1");
        // And similarly no match when not escaped
        assertNoPatternMatch(sources, "Middle$");
        // Can match a starting ^ if escaped
        assertNoPatternMatch(sources, "^foo");
        assertPatternMatch(sources, "\\^foo", "edgecase2");
        // or in a character class
        assertPatternMatch(sources, "[a^]foo", "edgecase2");
        // Similarly for $
        assertNoPatternMatch(sources, "bar$");
        assertPatternMatch(sources, "bar\\$", "edgecase2");
        assertPatternMatch(sources, "bar\\$$", "edgecase2");
        assertPatternMatch(sources, "bar[$]", "edgecase2");
        assertPatternMatch(sources, "bar[$]$", "edgecase2");
        // anchors can be used in parens
        assertPatternMatch(sources, "(^|qqq)abc", "findme");
        // any match (.) does not match anchors
        assertNoPatternMatch(sources, ".findme");
        assertNoPatternMatch(sources, "findme.");
        // \d matches numbers
        assertPatternMatch(sources, "\\d", "numbers");
        // [^\d] matches not-numbers
        assertPatternMatch(sources, "[^\\d]", "findme", "edgecase1", "edgecase2");
        // \s matches spaces
        assertPatternMatch(sources, "\\s", "edgecase2");
        // [^\s] matches not-spaces
        assertPatternMatch(sources, "^[^\\s]+$", "findme", "numbers", "edgecase1");
        // \w matches word-like things, it does not match spaces or special chars
        assertPatternMatch(sources, "^\\w+$", "findme", "numbers");
        assertPatternMatch(sources, "[^\\w]", "edgecase1", "edgecase2");
    }
}
