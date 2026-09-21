package com.equwal.sbm

/**
 * Search as in fzf. Each word of the query must occur in the text, with its
 * letters in order but not always together. Case does not matter.
 */
object Fuzzy {
    /** The score of [query] in [text], or null when a word does not occur. Higher is better. */
    fun score(query: String, text: String): Int? {
        val hay = text.lowercase()
        var total = 0
        for (word in query.lowercase().split(' ', '\t')) {
            if (word.isEmpty()) continue
            total += scoreWord(word, hay) ?: return null
        }
        return total
    }

    private fun scoreWord(word: String, hay: String): Int? {
        // The whole word in one place scores highest, most at the start of a word.
        val at = hay.indexOf(word)
        if (at >= 0) return 100 + word.length + (if (startsWord(hay, at)) 20 else 0)
        var score = 0
        var previous = -2
        var from = 0
        for (c in word) {
            val i = hay.indexOf(c, from)
            if (i < 0) return null
            score += 1
            if (i == previous + 1) score += 5
            if (startsWord(hay, i)) score += 3
            previous = i
            from = i + 1
        }
        return score
    }

    private fun startsWord(hay: String, i: Int) = i == 0 || !hay[i - 1].isLetterOrDigit()

    /** The items that match [query], best first. Items with the same score keep their order. */
    fun <T> filter(items: List<T>, query: String, text: (T) -> String): List<T> {
        if (query.isBlank()) return items
        return items.mapIndexedNotNull { i, item -> score(query, text(item))?.let { Triple(it, i, item) } }
            .sortedWith(compareByDescending<Triple<Int, Int, T>> { it.first }.thenBy { it.second })
            .map { it.third }
    }
}
