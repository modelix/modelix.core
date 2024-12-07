package org.modelix.model.server.handlers

import org.json.JSONArray
import org.json.JSONObject

fun Iterable<Any?>.toJsonArray(): JSONArray {
    val json = JSONArray()
    for (id in this) {
        json.put(id)
    }
    return json
}

fun JSONObject.entries(): Map<String, Any?> {
    return keySet().associateWith { jsonNullToJavaNull(opt(it)) }
}
fun JSONObject.stringEntries(): Map<String, String?> {
    return keySet().associateWith { optString(it, null) }
}
fun JSONObject.longEntries(): Map<String, Long?> {
    return keySet().associateWith { optNumber(it, null)?.toLong() }
}
fun JSONObject.arrayEntries(): Map<String, JSONArray> {
    return keySet().associateWith { optJSONArray(it) ?: JSONArray() }
}
fun jsonNullToJavaNull(value: Any?): Any? = if (value == JSONObject.NULL) null else value

fun JSONArray.asLongList(): List<Long> {
    return (0 until this.length()).map { getLong(it) }
}
fun JSONArray.asObjectList(): List<JSONObject> {
    return (0 until this.length()).map { getJSONObject(it) }
}

fun buildJSONArray(vararg elements: Any?): JSONArray = elements.toList().toJsonArray()

fun buildJSONObject(body: JSONObject.() -> Unit): JSONObject {
    val json = JSONObject()
    body(json)
    return json
}
