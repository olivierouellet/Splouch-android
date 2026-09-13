package app.splouch.core.session

import app.splouch.core.wire.I18nBundle
import app.splouch.core.wire.LocaleEntry
import app.splouch.core.wire.MeetConfig
import app.splouch.core.wire.MeetSummary
import app.splouch.core.wire.PickerConfig
import app.splouch.core.wire.ScheduleHeat
import app.splouch.core.wire.ServerEntry
import app.splouch.core.wire.ServerInfo
import app.splouch.core.wire.ServerKind
import app.splouch.core.wire.parseJsonOrNull
import kotlinx.serialization.json.JsonElement

/** A REST call's outcome. `NotFound` is a real answer (app.md A-09), everything else a network fault (C-03). */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data object NotFound : ApiResult<Nothing>
    data class Failure(val reason: String) : ApiResult<Nothing>
}

sealed interface I18nResult {
    data class Ok(val bundle: I18nBundle, val etag: String?, val text: String) : I18nResult
    data object NotModified : I18nResult
    data class Failure(val reason: String) : I18nResult
}

/** The JSON endpoints of api.md §4, for one server. */
class SplouchApi(private val http: HttpClient, val server: ServerAddress) {

    /** The handshake (P-13, api.md §5.10). `NotFound` here means "not a Splouch server". */
    suspend fun serverInfo(): ApiResult<ServerInfo> = getJson(server.httpUrl("/server")) { ServerInfo.fromJson(it) }

    suspend fun servers(): ApiResult<List<ServerEntry>> = getJson(server.httpUrl("/servers")) { ServerEntry.listFromJson(it) }

    suspend fun meets(): ApiResult<List<MeetSummary>> = getJson(server.httpUrl("/meets")) { MeetSummary.listFromJson(it) }

    suspend fun pickerConfig(lang: String?): ApiResult<PickerConfig> {
        val q = lang?.takeIf { it.isNotBlank() }?.let { "?lang=${MeetContext.enc(it)}" } ?: ""
        return getJson(server.httpUrl("/picker/config$q")) { PickerConfig.fromJson(it) }
    }

    /** A 404 on the cloud is the meet-gone signal (A-09); on a Pi the route always answers. */
    suspend fun meetConfig(context: MeetContext): ApiResult<MeetConfig> = getJson(context.configUrl) {
        if (context.kind == ServerKind.PI) MeetConfig.fromPiJson(it) else MeetConfig.fromCloudJson(it)
    }

    suspend fun schedule(context: MeetContext): ApiResult<List<ScheduleHeat>> =
        getJson(context.scheduleUrl) { ScheduleHeat.listFromJson(it) }

    suspend fun locales(): ApiResult<List<LocaleEntry>> = getJson(server.httpUrl("/locales")) { LocaleEntry.listFromJson(it) }

    /** T-10: revalidates with the ETag the last fetch returned. */
    suspend fun i18n(lang: String, etag: String?): I18nResult {
        val headers = if (etag.isNullOrBlank()) emptyMap() else mapOf("If-None-Match" to etag)
        val r = try {
            http.get(server.httpUrl("/i18n/${MeetContext.enc(lang)}"), headers)
        } catch (e: Exception) {
            return I18nResult.Failure(e.message ?: e.javaClass.simpleName)
        }
        if (r.status == 304) return I18nResult.NotModified
        if (!r.isSuccess) return I18nResult.Failure("HTTP ${r.status}")
        val bundle = I18nBundle.fromText(r.body) ?: return I18nResult.Failure("not a strings bundle")
        return I18nResult.Ok(bundle, r.header("ETag"), r.body)
    }

    private suspend fun <T> getJson(url: String, decode: (JsonElement?) -> T?): ApiResult<T> {
        val r = try {
            http.get(url)
        } catch (e: Exception) {
            return ApiResult.Failure(e.message ?: e.javaClass.simpleName)
        }
        if (r.status == 404) return ApiResult.NotFound
        if (!r.isSuccess) return ApiResult.Failure("HTTP ${r.status}")
        val json = parseJsonOrNull(r.body) ?: return ApiResult.Failure("not JSON")
        val value = decode(json) ?: return ApiResult.Failure("unexpected body")
        return ApiResult.Ok(value)
    }
}
