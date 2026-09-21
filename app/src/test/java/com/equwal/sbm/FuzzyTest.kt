package com.equwal.sbm

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Example
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide

class FuzzyTest {
    @Provide
    fun letters(): Arbitrary<String> = Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(30)

    @Provide
    fun early(): Arbitrary<String> = Arbitraries.strings().withCharRange('a', 'm').ofMinLength(1).ofMaxLength(30)

    @Provide
    fun late(): Arbitrary<String> = Arbitraries.strings().withCharRange('n', 'z').ofMinLength(1).ofMaxLength(5)

    @Provide
    fun lists(): Arbitrary<List<String>> = letters().list().ofMaxSize(10)

    @Property
    fun `letters of the text, in order, match`(@ForAll("letters") text: String, @ForAll mask: List<Boolean>): Boolean {
        val query = text.filterIndexed { i, _ -> mask.getOrElse(i) { false } }.ifEmpty { text.take(1) }
        return Fuzzy.score(query, text) != null
    }

    @Property
    fun `a letter that is not in the text does not match`(@ForAll("early") text: String, @ForAll("late") query: String): Boolean =
        Fuzzy.score(query, text) == null

    @Property
    fun `every word must match`(@ForAll("letters") a: String, @ForAll("letters") b: String, @ForAll("letters") text: String): Boolean =
        (Fuzzy.score("$a $b", text) != null) == (Fuzzy.score(a, text) != null && Fuzzy.score(b, text) != null)

    @Property
    fun `case does not matter`(@ForAll("letters") query: String, @ForAll("letters") text: String): Boolean =
        Fuzzy.score(query.uppercase(), text) == Fuzzy.score(query, text.uppercase())

    @Property
    fun `the filter keeps the matches and only them`(@ForAll("lists") items: List<String>, @ForAll("letters") query: String): Boolean =
        Fuzzy.filter(items, query) { it } .sorted() == items.filter { Fuzzy.score(query, it) != null }.sorted()

    @Example
    fun `a blank query keeps every item in its order`() {
        check(Fuzzy.filter(listOf("b", "a"), " ") { it } == listOf("b", "a"))
    }

    @Example
    fun `the word in one place ranks above scattered letters`() {
        check(Fuzzy.filter(listOf("s x b x m", "the sbm tool"), "sbm") { it } == listOf("the sbm tool", "s x b x m"))
    }

    @Example
    fun `the start of a word ranks above the inside of a word`() {
        check(Fuzzy.filter(listOf("absbm", "a sbm"), "sbm") { it } == listOf("a sbm", "absbm"))
    }
}
