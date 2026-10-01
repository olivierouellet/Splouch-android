package app.splouch.core.session

import app.splouch.core.strings.StringTable
import app.splouch.core.wire.PickerConfig

/**
 * P-06 and P-07, the two notices above the meet list. Each shows in full until the reader
 * folds it with its X to a pill, and the pill opens it again; neither ever goes away.
 *
 * The keys are the wire's (api.md §5.7 `strings`), and every word is the server's. The three
 * keys added with the fold — the two pill labels and the X's name — are missing from an older
 * server's reply; those come from the strings snapshot behind it, in the reader's language,
 * so a pill is never an empty button and never English beside a French notice.
 */
enum class PickerNotice(val textKey: String, val shortKey: String) {
    /** P-06: live, unofficial results pending validation. Always shown. */
    RESULTS_DISCLAIMER("results_disclaimer", "results_disclaimer_short"),

    /** P-07: attendance counting, shown only while the server reports it on. */
    PRIVACY_NOTE("privacy_note", "privacy_note_short"),
    ;

    companion object {
        const val COLLAPSE_KEY = "notice_collapse"
    }
}

/** One notice as the picker draws it: its words, its pill's label, and whether it starts folded. */
data class ShownNotice(val notice: PickerNotice, val text: String, val short: String, val folded: Boolean)

/**
 * Where a fold is remembered: per server origin (the same key `C-10`'s `vid` uses) and
 * per notice, holding **the exact text that was folded** rather than a flag. A notice
 * reworded, or the same notice in another language, no longer matches and shows in full.
 */
interface NoticeStore {
    /** The text folded for [notice] on [origin] (`ServerAddress.origin`), or null. */
    fun folded(origin: String, notice: PickerNotice): String?

    /** Remember [text] as folded, or forget the fold when [text] is null. */
    fun setFolded(origin: String, notice: PickerNotice, text: String?)
}

class InMemoryNoticeStore : NoticeStore {
    private val folds = HashMap<Pair<String, PickerNotice>, String>()

    override fun folded(origin: String, notice: PickerNotice): String? = synchronized(folds) { folds[origin to notice] }

    override fun setFolded(origin: String, notice: PickerNotice, text: String?) {
        synchronized(folds) { if (text == null) folds.remove(origin to notice) else folds[origin to notice] = text }
    }
}

/**
 * The picker's notices, in order, and the folds stored for this server. A notice starts
 * folded only when the stored text is the text about to be shown.
 *
 * A notice is shown only once this server has sent its text (`app.md` `P-06`): no picker
 * config yet — a first launch offline — means no notice, as on iOS. A notice is this
 * server's words about this server's results; the snapshot's copy would be another
 * server's, and an empty picker has no results to qualify. The pill and the X, on the
 * other hand, may come from the snapshot, for an older server that sends the text alone.
 */
fun pickerNotices(config: PickerConfig?, table: StringTable, folds: Map<PickerNotice, String>): List<ShownNotice> {
    val shown = buildList {
        add(PickerNotice.RESULTS_DISCLAIMER)
        if (config?.analyticsEnabled == true) add(PickerNotice.PRIVACY_NOTE)
    }
    return shown.mapNotNull { n ->
        val text = served(config, n.textKey) ?: return@mapNotNull null
        val short = served(config, n.shortKey) ?: table.mobile(n.shortKey)
        ShownNotice(n, text, short, folded = folds[n] == text)
    }
}

/** The X's accessible name: the server's word, else the snapshot's. */
fun noticeCollapseLabel(config: PickerConfig?, table: StringTable): String =
    served(config, PickerNotice.COLLAPSE_KEY) ?: table.mobile(PickerNotice.COLLAPSE_KEY)

/** A word from `GET /picker/config` → `strings`; empty counts as absent, as in [StringTable]. */
private fun served(config: PickerConfig?, key: String): String? = config?.strings?.get(key)?.takeIf(String::isNotEmpty)
