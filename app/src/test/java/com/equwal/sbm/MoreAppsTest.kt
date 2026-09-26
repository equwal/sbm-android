package com.equwal.sbm

import net.jqwik.api.Example

class MoreAppsTest {
    @Example
    fun `the list leaves out this app`() {
        check(MORE_APPS.none { "equwal/sbm-android" in it.url })
    }

    @Example
    fun `each link opens an https page`() {
        for (app in MORE_APPS) check(app.url.startsWith("https://")) { app.url }
    }

    @Example
    fun `the list keeps the order of the catalog`() {
        val sites = listOf("https://subread.space/", "https://booksimulator.com/", "https://honjimaku.com/", "https://sbmsync.com/")
        check(MORE_APPS.take(4).map { it.url } == sites)
        check(MORE_APPS.last().url == "https://recentlywritten.com/projects.html")
        // The catalog has 15 entries. This app is the one that is not in the list.
        check(MORE_APPS.size == 14)
    }
}
