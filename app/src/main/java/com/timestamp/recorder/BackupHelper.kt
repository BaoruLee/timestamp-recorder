package com.timestamp.recorder

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON 全量备份 / 恢复（零权限、走系统 SAF）。
 *
 * 备份结构：
 * {
 *   "app": "timestamp-recorder",
 *   "format": 1,
 *   "exportedAt": <millis>,
 *   "events": [ {id,name,color,createdAt}, ... ],
 *   "records": { "<eventId>": [<millis>, ...], ... }
 * }
 */
object BackupHelper {
    private const val MAGIC = "timestamp-recorder"
    private const val FORMAT = 1

    data class BackupData(
        val events: List<TimestampEvent>,
        val records: Map<Long, List<Long>>
    )

    fun exportJson(repo: EventRepository): String {
        val root = JSONObject()
        root.put("app", MAGIC)
        root.put("format", FORMAT)
        root.put("exportedAt", System.currentTimeMillis())

        val evArr = JSONArray()
        repo.getEvents().forEach { e ->
            evArr.put(JSONObject().apply {
                put("id", e.id)
                put("name", e.name)
                put("color", e.color)
                put("createdAt", e.createdAt)
            })
        }
        root.put("events", evArr)

        val recObj = JSONObject()
        repo.getEvents().forEach { e ->
            val arr = JSONArray()
            repo.getRecords(e.id).forEach { arr.put(it) }
            recObj.put(e.id.toString(), arr)
        }
        root.put("records", recObj)

        return root.toString(2)
    }

    /** 解析备份 JSON；格式不符返回 null */
    fun parse(json: String): BackupData? {
        return try {
            val root = JSONObject(json)
            if (root.optString("app") != MAGIC) return null
            val evArr = root.getJSONArray("events")
            val events = (0 until evArr.length()).map { i ->
                val o = evArr.getJSONObject(i)
                TimestampEvent(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    color = o.getInt("color"),
                    createdAt = o.getLong("createdAt")
                )
            }
            val recObj = root.getJSONObject("records")
            val records = mutableMapOf<Long, List<Long>>()
            recObj.keys().forEach { k ->
                val arr = recObj.getJSONArray(k)
                records[k.toLong()] = (0 until arr.length()).map { arr.getLong(it) }
            }
            BackupData(events, records)
        } catch (_: Exception) {
            null
        }
    }
}
