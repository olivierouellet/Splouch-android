package app.splouch.core.strings

import app.splouch.core.wire.I18nBundle

data class CachedBundle(val bundle: I18nBundle, val etag: String?)

/**
 * The on-disk layer of app.md T-10: the last `GET /i18n/{lang}` body per server and
 * language, plus its ETag for revalidation. Per server, because a Pi serves its own
 * custom wording (api.md §5.9).
 */
interface BundleCache {
    fun read(origin: String, lang: String): CachedBundle?
    fun write(origin: String, lang: String, text: String, etag: String?)
}

class InMemoryBundleCache : BundleCache {
    private val entries = HashMap<String, CachedBundle>()
    override fun read(origin: String, lang: String): CachedBundle? = entries["$origin|$lang"]
    override fun write(origin: String, lang: String, text: String, etag: String?) {
        I18nBundle.fromText(text)?.let { entries["$origin|$lang"] = CachedBundle(it, etag) }
    }
}
