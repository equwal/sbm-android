package com.equwal.sbm

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.Example
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide

class PreviewTest {
    // The part of a web address after the scheme: a host, then a path.
    @Provide
    fun rests(): Arbitrary<String> {
        val host = Arbitraries.strings().withCharRange('a', 'z').withChars('.', '-', '0', '9').ofMinLength(1).ofMaxLength(20)
        val path = Arbitraries.strings().withCharRange('!', '~').ofMaxLength(40)
            .map { if (it.isEmpty() || it[0] in "/?#") it else "/$it" }
        return Combinators.combine(host, path).`as` { h, p -> h + p }
    }

    @Provide
    fun schemes(): Arbitrary<String> = Arbitraries.of("http://", "https://", "HTTP://", "Https://")

    @Property
    fun `a web page shows at its address with https`(@ForAll("schemes") scheme: String, @ForAll("rests") rest: String): Boolean =
        Preview.address(scheme + rest) == "https://$rest"

    @Property
    fun `the preview only ever loads https addresses, and loads them as they are`(@ForAll text: String): Boolean {
        val a = Preview.address(text) ?: return true
        return a.startsWith("https://") && Preview.address(a) == a
    }

    @Example
    fun `addresses that are no web page have no preview`() {
        for (u in listOf("javascript:alert(1)", "data:text/html,x", "file:///etc/passwd", "intent://x#Intent;end",
            "market://details?id=x", "ftp://example.org/", "about:blank", "example.org", "", "https://", "http:///x")) {
            check(Preview.address(u) == null) { u }
        }
    }
}
