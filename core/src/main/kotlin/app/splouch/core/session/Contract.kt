package app.splouch.core.session

import app.splouch.core.wire.ContractVersions

/**
 * The contract versions this app was built against: the headers of `docs/api.md` and
 * `docs/app.md` in the Splouch repo. A test pins them to those files when the sibling
 * checkout is present.
 */
object Contract {
    val BUILT = ContractVersions(api = "v2", app = "v1")

    /**
     * P-14: a one-line notice naming both sides when either version differs; null when
     * they match. Never a refusal — the caller connects regardless.
     */
    fun mismatchNotice(server: ContractVersions): String? {
        if (server.api == BUILT.api && server.app == BUILT.app) return null
        return "Server contract api ${server.api.ifEmpty { "?" }} / app ${server.app.ifEmpty { "?" }}, this app was built for api ${BUILT.api} / app ${BUILT.app}"
    }
}
