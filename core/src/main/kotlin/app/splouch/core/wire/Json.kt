package app.splouch.core.wire

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** The one JSON configuration every decoder shares: unknown keys are never an error (api.md §1). */
val WireJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Tolerant accessors. The contract's types are not uniform (api.md §5.1: event and heat are
 * strings in one payload and integers in another), so every scalar read here accepts the
 * other spelling and normalises. A number read as a string yields its digits; anything that
 * is not a scalar yields null.
 */
fun JsonElement?.asStringOrNull(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> content
    else -> null
}

fun JsonElement?.asBoolOrNull(): Boolean? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> if (isString) content.trim().toBooleanStrictOrNull() else booleanOrNull
    else -> null
}

fun JsonElement?.asIntOrNull(): Int? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> content.trim().let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
    else -> null
}

fun JsonElement?.asDoubleOrNull(): Double? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> content.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
    else -> null
}

fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject
fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

/** Every scalar-valued entry of an object, as strings. Nested values are dropped. */
fun JsonObject?.stringMap(): Map<String, String> =
    this?.entries?.mapNotNull { (k, v) -> v.asStringOrNull()?.let { k to it } }?.toMap() ?: emptyMap()

fun parseJsonOrNull(text: String): JsonElement? = try {
    WireJson.parseToJsonElement(text)
} catch (_: Exception) {
    null
}
