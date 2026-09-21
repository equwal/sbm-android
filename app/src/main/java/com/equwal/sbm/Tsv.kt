package com.equwal.sbm

import java.net.URLEncoder

/** One line of an sbm bookmark file: URL<tab>description<tab>tags. */
data class Bookmark(val url: String, val desc: String, val tags: List<String>)

/**
 * The bookmark file of sbm, as bm reads and writes it. This file has no
 * Android code, so the tests run on any JVM.
 */
object Tsv {
    private val control = Regex("[\t\r\n]")
    private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    private val space = Regex("\\s+")
    private val web = Regex("""https?://\S+""")

    /** The bookmarks in the text of a file. Blank lines and lines that start with # are not bookmarks. */
    fun parse(text: String): List<Bookmark> =
        text.split('\n').mapNotNull { raw ->
            val line = raw.removeSuffix("\r")
            if (line.isBlank() || line.startsWith("#")) null else parseLine(line)
        }

    private fun parseLine(line: String): Bookmark? {
        val fields = line.split('\t')
        if (fields.size == 1) {
            // A line of the old format: "URL description | tags". bm-migrate
            // converts it. Until then the first word is the URL.
            val words = line.trim().split(space, limit = 2)
            return Bookmark(words[0], words.getOrElse(1) { "" }, emptyList())
        }
        val url = fields[0].trim()
        if (url.isEmpty()) return null
        val tags = fields.getOrElse(2) { "" }.split(' ').filter { it.isNotEmpty() }
        return Bookmark(url, fields[1], tags)
    }

    /** The line of a bookmark, without the newline. */
    fun format(b: Bookmark): String =
        b.url.filterNot { it.isWhitespace() } + "\t" + clean(b.desc) + "\t" +
            b.tags.flatMap { it.split(space) }.filter { it.isNotEmpty() }.joinToString(" ")

    /** Text for one field: tabs and line breaks become spaces. */
    fun clean(text: String): String = control.replace(text, " ")

    /**
     * The same page for bm: two URLs match when they differ only in the
     * scheme, a leading www., trailing slashes or the case of the host.
     */
    fun norm(url: String): String {
        val u = scheme.replace(url, "")
        val slash = u.indexOf('/')
        val host = (if (slash >= 0) u.substring(0, slash) else u).lowercase().removePrefix("www.")
        val rest = if (slash >= 0) u.substring(slash).trimEnd('/') else ""
        return host + rest
    }

    /** The bookmark for the same page as [url], if there is one. */
    fun find(bookmarks: List<Bookmark>, url: String): Bookmark? {
        val n = norm(url)
        return bookmarks.firstOrNull { norm(it.url) == n }
    }

    /** The text to add to a file that holds [text], so that [line] is a line of its own. */
    fun appendix(text: String, line: String): String =
        if (text.isEmpty() || text.endsWith("\n")) "$line\n" else "\n$line\n"

    /** The host of a URL, for display. */
    fun host(url: String): String = scheme.replace(url, "").substringBefore('/').substringBefore('?').substringBefore('#')

    /** The first web address in shared text. */
    fun firstUrl(text: String): String? = web.find(text)?.value

    /**
     * Where text that is no bookmark goes, as in bm: one word with "://" or
     * a dot is an address. Other text is a web search.
     */
    fun target(text: String): String {
        val t = text.trim()
        if (!t.contains(' ')) {
            if (t.contains("://")) return t
            if (t.contains('.')) return "https://$t"
        }
        return "https://duckduckgo.com/?q=" + URLEncoder.encode(t, "UTF-8")
    }
}
