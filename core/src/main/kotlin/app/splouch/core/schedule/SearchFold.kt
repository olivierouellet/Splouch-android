package app.splouch.core.schedule

import java.text.Normalizer

/**
 * The search fold of app.md `S-09`, in four steps:
 *
 *     lowercase → NFD decompose → expand [EXPANSIONS] → drop every codepoint > U+007F
 *
 * Applied to *both* the query and every indexed name; that symmetry is what makes search
 * case- and accent-insensitive. Steps 2 and 4 do the accents: `é` decomposes to `e` plus a
 * combining acute, and the acute is then swept away.
 *
 * A name with no ASCII at all (`北島`) folds to empty and is unreachable by search. That is
 * a known limit of the contract, not a bug to patch here: changing it changes app.md first.
 */
object SearchFold {

    /**
     * The 17 letters with no canonical decomposition. NFD leaves them whole, so step 4 would
     * delete the letter itself and punch a hole in the word — `Île-des-Sœurs` would index as
     * `ile-des-surs`, which nobody will ever type.
     *
     * Expanded *after* decomposing, which covers the accented forms for free: `ǿ` decomposes
     * to `ø` plus an acute, and the `ø` row then handles it. The table is exactly these 17.
     */
    private val EXPANSIONS = mapOf(
        'ß' to "ss", 'æ' to "ae", 'ð' to "d", 'ø' to "o", 'þ' to "th", 'đ' to "d",
        'ħ' to "h", 'ı' to "i", 'ĳ' to "ij", 'ĸ' to "k", 'ŀ' to "l", 'ł' to "l",
        'ŉ' to "n", 'ŋ' to "n", 'œ' to "oe", 'ŧ' to "t", 'ſ' to "s",
    )

    fun fold(s: String): String = buildString(s.length) {
        for (c in Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)) {
            val e = EXPANSIONS[c]
            if (e != null) append(e) else if (c.code <= 0x7F) append(c)
        }
    }
}
