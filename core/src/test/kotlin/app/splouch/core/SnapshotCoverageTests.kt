package app.splouch.core

import app.splouch.core.strings.BuiltInStrings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * app.md T-05 and T-10: every `[mobile]` key the app asks for must exist in the captured
 * English snapshot, so the app cannot drift ahead of the server and render a key name
 * on screen.
 *
 * A failure here is a **server-side** gap: the word belongs in the server's
 * `shared/locales` table, and the fix is to add it there and re-run
 * `scripts/update-strings.sh` (task C in this repo's README). Never satisfy
 * this test by adding a word to the app — a word the app owns is a native string
 * resource and does not go through [app.splouch.core.strings.StringTable.mobile] at all.
 */
class SnapshotCoverageTests {

    /** Matches `mobile("key")`, including the receiver-qualified call sites. */
    private val call = Regex("""\bmobile\(\s*"([a-z0-9_]+)"\s*\)""")

    /** Both source sets: the core module's own, and the app module's beside it. */
    private fun sources(): List<File> =
        listOf(File("src/main/kotlin"), File("../app/src/main/kotlin"))
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    @Test fun `every mobile key the app uses is in the English snapshot`() {
        val files = sources()
        assertTrue(files.isNotEmpty(), "no Kotlin sources found from ${File(".").absolutePath}")

        val used = files.flatMap { f -> call.findAll(f.readText()).map { it.groupValues[1] to f.path } }
        // The regex is the whole test: if it stops matching the call sites, the check is
        // passing vacuously and would not catch the next missing key.
        assertTrue(used.isNotEmpty(), "no mobile(...) calls found — the regex no longer matches the call sites")

        val served = BuiltInStrings.english()?.mobile?.keys.orEmpty()
        assertTrue(served.isNotEmpty(), "resources/i18n/en.json carries no mobile section")

        val missing = used.filter { it.first !in served }.groupBy({ it.first }, { it.second })
        assertTrue(
            missing.isEmpty(),
            "keys the app renders that the server does not serve — add them to shared/locales " +
                "and recapture, do not add a word here: " +
                missing.entries.sortedBy { it.key }.joinToString("; ") { (key, where) ->
                    "$key (${where.distinct().joinToString()})"
                },
        )
    }

    @Test fun `the snapshot covers every language the snapshot's own locale list names`() {
        val locales = BuiltInStrings.locales()
        assertTrue(locales.isNotEmpty(), "resources/i18n/locales.json is empty")
        val missing = locales.filter { BuiltInStrings.load(it.code) == null }
        assertTrue(missing.isEmpty(), "locales.json names languages with no snapshot file: ${missing.map { it.code }}")
    }
}
