package com.equwal.sbm

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.Example
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide

class TsvTest {
    @Provide
    fun bookmarks(): Arbitrary<Bookmark> {
        val url = Arbitraries.strings().withCharRange('!', '~').ofMinLength(1).ofMaxLength(40)
            .filter { !it.startsWith("#") }
        val desc = Arbitraries.strings().ofMaxLength(40)
            .filter { d -> d.none { it == '\t' || it == '\r' || it == '\n' } }
        val tag = Arbitraries.strings().withCharRange('a', 'z').withChars('-', '0', '9')
            .ofMinLength(1).ofMaxLength(10)
        return Combinators.combine(url, desc, tag.list().ofMaxSize(5)).`as` { u, d, t -> Bookmark(u, d, t) }
    }

    @Provide
    fun files(): Arbitrary<List<Bookmark>> = bookmarks().list().ofMaxSize(20)

    @Property
    fun `a written bookmark reads back the same`(@ForAll("bookmarks") b: Bookmark): Boolean =
        Tsv.parse(Tsv.format(b) + "\n") == listOf(b)

    @Property
    fun `a written file reads back the same`(@ForAll("files") bs: List<Bookmark>): Boolean =
        Tsv.parse(bs.joinToString("") { Tsv.format(it) + "\n" }) == bs

    @Property
    fun `a file with CRLF line ends reads the same`(@ForAll("files") bs: List<Bookmark>): Boolean {
        val text = bs.joinToString("") { Tsv.format(it) + "\n" }
        return Tsv.parse(text.replace("\n", "\r\n")) == Tsv.parse(text)
    }

    // Web addresses as people bookmark them: scheme, host, path.
    @Provide
    fun hosts(): Arbitrary<String> {
        val label = Arbitraries.strings().withCharRange('a', 'z').withCharRange('0', '9')
            .ofMinLength(1).ofMaxLength(10)
        val tld = Arbitraries.strings().withCharRange('a', 'z').ofMinLength(2).ofMaxLength(4)
        return Combinators.combine(label.list().ofMinSize(1).ofMaxSize(3), tld)
            .`as` { labels, t -> labels.joinToString(".") + "." + t }
            .filter { !it.startsWith("www.") }
    }

    @Provide
    fun paths(): Arbitrary<String> =
        Arbitraries.strings().withCharRange('a', 'z').withCharRange('A', 'Z').ofMinLength(1).ofMaxLength(6)
            .list().ofMaxSize(3).map { segments -> segments.joinToString("") { "/$it" } }

    @Property
    fun `the scheme does not matter`(@ForAll("hosts") h: String, @ForAll("paths") p: String): Boolean =
        Tsv.norm("http://$h$p") == Tsv.norm("https://$h$p")

    @Property
    fun `a leading www does not matter`(@ForAll("hosts") h: String, @ForAll("paths") p: String): Boolean =
        Tsv.norm("https://www.$h$p") == Tsv.norm("https://$h$p")

    @Property
    fun `trailing slashes do not matter`(@ForAll("hosts") h: String, @ForAll("paths") p: String): Boolean =
        Tsv.norm("https://$h$p/") == Tsv.norm("https://$h$p") &&
            Tsv.norm("https://$h$p//") == Tsv.norm("https://$h$p")

    @Property
    fun `the case of the host does not matter`(@ForAll("hosts") h: String, @ForAll("paths") p: String): Boolean =
        Tsv.norm("https://${h.uppercase()}$p") == Tsv.norm("https://$h$p")

    @Property
    fun `the path counts`(@ForAll("hosts") h: String, @ForAll("paths") p: String): Boolean =
        Tsv.norm("https://$h$p") == h + p

    @Example
    fun `comments, blank lines and old lines`() {
        val text = "# notes\n\nhttps://a.example\tA\tcode sec\nhttps://b.example B site | tag\n"
        check(Tsv.parse(text) == listOf(
            Bookmark("https://a.example", "A", listOf("code", "sec")),
            Bookmark("https://b.example", "B site | tag", emptyList())))
    }

    @Example
    fun `a line without tags`() {
        check(Tsv.parse("https://a.example\tA\t\n") == listOf(Bookmark("https://a.example", "A", emptyList())))
    }

    @Example
    fun `writing cleans each field`() {
        val b = Bookmark(" https://a.example/x \n", "one\ttwo\nthree", listOf("a b", "", "c"))
        check(Tsv.format(b) == "https://a.example/x\tone two three\ta b c")
    }

    @Example
    fun `the same page, as bm finds it`() {
        val known = listOf(Bookmark("https://www.Example.com/a/", "A", emptyList()))
        check(Tsv.find(known, "http://example.com/a") == known[0])
        check(Tsv.find(known, "https://example.com/b") == null)
    }

    @Example
    fun `a new line always starts on a line of its own`() {
        check(Tsv.appendix("", "x") == "x\n")
        check(Tsv.appendix("a\n", "x") == "x\n")
        check(Tsv.appendix("a", "x") == "\nx\n")
    }

    @Example
    fun `text that is no bookmark goes where bm sends it`() {
        check(Tsv.target("suckless.org/dwm") == "https://suckless.org/dwm")
        check(Tsv.target("gopher://x") == "gopher://x")
        check(Tsv.target("posix sh printf") == "https://duckduckgo.com/?q=posix+sh+printf")
    }

    @Example
    fun `host and first address`() {
        check(Tsv.host("https://www.a.example/x?y#z") == "www.a.example")
        check(Tsv.host("a.example") == "a.example")
        check(Tsv.firstUrl("Look at https://a.example/x now") == "https://a.example/x")
        check(Tsv.firstUrl("no address") == null)
    }
}
