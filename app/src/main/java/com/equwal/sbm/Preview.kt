package com.equwal.sbm

/**
 * The live preview: the page of a bookmark, shown under the search. This
 * file has no Android code, so the tests run on any JVM.
 */
object Preview {
    private val web = Regex("^(?i)https?://([^/?#\\s]+)(.*)$")

    /**
     * The address that the preview loads for the URL of a bookmark, or null
     * when the page cannot show. Only web pages show. An http page loads with
     * https, because the app allows no cleartext traffic.
     */
    fun address(url: String): String? {
        val m = web.matchEntire(url.trim()) ?: return null
        return "https://" + m.groupValues[1] + m.groupValues[2]
    }
}
