package com.timestamp.recorder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 事件模型：一个事件 = 一个可独立记录时间戳的分类 */
data class TimestampEvent(
    val id: Long,
    val name: String,
    val color: Int,
    val createdAt: Long
)

/** 分类染色色板（Material 风格 12 色） */
object EventColors {
    val palette = listOf(
        0xFFE53935.toInt(), // 红
        0xFFFB8C00.toInt(), // 橙
        0xFFF9A825.toInt(), // 琥珀
        0xFF43A047.toInt(), // 绿
        0xFF00897B.toInt(), // 青绿
        0xFF00ACC1.toInt(), // 青
        0xFF1E88E5.toInt(), // 蓝
        0xFF3949AB.toInt(), // 靛
        0xFF8E24AA.toInt(), // 紫
        0xFFD81B60.toInt(), // 粉
        0xFF6D4C41.toInt(), // 棕
        0xFF546E7A.toInt()  // 蓝灰
    )
    fun random(): Int = palette.random()
}

/** 时间线条目：一条记录 + 所属事件信息（事件名 / 事件色在记录时快照，避免查询时反复取事件表） */
data class TimelineRecord(
    val eventId: Long,
    val eventName: String,
    val eventColor: Int,
    val millis: Long
)

/** 数据层：SharedPreferences + JSON，零第三方依赖、纯本地存储 */
class EventRepository(context: Context) {

    private val prefs = context.getSharedPreferences("tsr_data", Context.MODE_PRIVATE)
    private val eventsKey = "events"
    private fun recordsKey(id: Long) = "records_$id"

    // ---------- 事件 CRUD ----------

    @Synchronized
    fun getEvents(): List<TimestampEvent> {
        val raw = prefs.getString(eventsKey, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TimestampEvent(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    color = o.getInt("color"),
                    createdAt = o.getLong("createdAt")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun getEvent(id: Long): TimestampEvent? = getEvents().find { it.id == id }

    @Synchronized
    fun addEvent(name: String, color: Int): TimestampEvent {
        val events = getEvents().toMutableList()
        var id = System.currentTimeMillis()
        while (events.any { it.id == id }) id++
        val ev = TimestampEvent(id, name.trim(), color, System.currentTimeMillis())
        events.add(ev)
        saveEvents(events)
        return ev
    }

    @Synchronized
    fun updateEvent(id: Long, name: String, color: Int) {
        saveEvents(getEvents().map {
            if (it.id == id) it.copy(name = name.trim(), color = color) else it
        })
    }

    @Synchronized
    fun deleteEvent(id: Long) {
        saveEvents(getEvents().filterNot { it.id == id })
        prefs.edit().remove(recordsKey(id)).apply()
    }

    /**
     * 按「手动顺序」（即存储数组的顺序）返回事件列表。
     * 拖拽排序时直接调整存储顺序，无需额外的 order 字段。
     */
    fun getEventsManualOrder(): List<TimestampEvent> = getEvents()

    /** 按「最近记录时间」降序返回；无记录的事件排在最后。 */
    fun getEventsByRecent(): List<TimestampEvent> {
        val lastMap = getEvents().associateWith { lastRecord(it.id) ?: Long.MIN_VALUE }
        return getEvents().sortedByDescending { lastMap[it] }
    }

    /** 拖拽结束时持久化新的手动顺序（传入事件 id 的顺序即新顺序）。 */
    @Synchronized
    fun setEventsOrder(orderedIds: List<Long>) {
        val map = getEvents().associateBy { it.id }
        val reordered = orderedIds.mapNotNull { map[it] }
        val missing = map.values.filter { it.id !in orderedIds }
        saveEvents(reordered + missing)
    }

    /** 用备份数据整体替换（先清空旧的事件与记录，再写入备份内容）。 */
    @Synchronized
    fun replaceAllData(data: BackupHelper.BackupData) {
        // 清空所有记录键，避免残留旧数据
        prefs.all.keys.filter { it.startsWith("records_") }.forEach {
            prefs.edit().remove(it).apply()
        }
        saveEvents(data.events)
        data.records.forEach { (id, list) -> saveRecords(id, list) }
    }

    private fun saveEvents(events: List<TimestampEvent>) {
        val arr = JSONArray()
        events.forEach {
            arr.put(JSONObject()
                .put("id", it.id)
                .put("name", it.name)
                .put("color", it.color)
                .put("createdAt", it.createdAt))
        }
        prefs.edit().putString(eventsKey, arr.toString()).apply()
    }

    // ---------- 记录（时间戳，毫秒精度，最新在前） ----------

    @Synchronized
    fun getRecords(eventId: Long): List<Long> {
        val raw = prefs.getString(recordsKey(eventId), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getLong(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun addRecord(eventId: Long): Long {
        val millis = System.currentTimeMillis()
        val list = getRecords(eventId).toMutableList()
        list.add(0, millis)
        saveRecords(eventId, list)
        return millis
    }

    @Synchronized
    fun deleteRecord(eventId: Long, position: Int) {
        val list = getRecords(eventId).toMutableList()
        if (position in list.indices) {
            list.removeAt(position)
            saveRecords(eventId, list)
        }
    }

    /** 批量删除：按记录的时间戳值删除（批量勾选用） */
    @Synchronized
    fun deleteRecords(eventId: Long, toRemove: Set<Long>) {
        if (toRemove.isEmpty()) return
        val list = getRecords(eventId).toMutableList()
        list.removeAll(toRemove)
        saveRecords(eventId, list)
    }

    @Synchronized
    fun undoLast(eventId: Long): Boolean {
        val list = getRecords(eventId).toMutableList()
        if (list.isEmpty()) return false
        list.removeAt(0)
        saveRecords(eventId, list)
        return true
    }

    @Synchronized
    fun clearRecords(eventId: Long) {
        prefs.edit().remove(recordsKey(eventId)).apply()
    }

    fun recordCount(eventId: Long): Int = getRecords(eventId).size

    fun lastRecord(eventId: Long): Long? = getRecords(eventId).firstOrNull()

    /** 时间线：全部事件的所有记录合并，按时间倒序（最新在前）。事件被删时其记录一并删除，故无需过滤。 */
    @Synchronized
    fun getAllRecords(): List<TimelineRecord> {
        val out = mutableListOf<TimelineRecord>()
        for (ev in getEvents()) {
            for (m in getRecords(ev.id)) {
                out.add(TimelineRecord(ev.id, ev.name, ev.color, m))
            }
        }
        return out.sortedByDescending { it.millis }
    }

    private fun saveRecords(eventId: Long, records: List<Long>) {
        val arr = JSONArray()
        records.forEach { arr.put(it) }
        prefs.edit().putString(recordsKey(eventId), arr.toString()).apply()
    }
}

/** 时间格式化工具 */
object TimeFormat {
    private val full = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    private val hm = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    private val short = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    fun full(millis: Long): String = full.format(java.util.Date(millis))
    fun hm(millis: Long): String = hm.format(java.util.Date(millis))
    fun short(millis: Long): String = short.format(java.util.Date(millis))

    /**
     * 相对时间（与参考 App「Last Time」时间线一致的中文格式）：
     * 刚刚 / N 分钟前 / N 小时, M 分钟前 / N 天前
     */
    fun relative(context: android.content.Context, millis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - millis
        if (diff < 60_000) return context.getString(R.string.timeline_just_now)
        val min = diff / 60_000
        return when {
            min < 60 -> context.getString(R.string.timeline_minutes_ago, min)
            min < 1440 -> {
                val h = min / 60
                val m = min % 60
                if (m == 0L) context.getString(R.string.timeline_hours_ago, h)
                else context.getString(R.string.timeline_hours_minutes_ago, h, m)
            }
            else -> context.getString(R.string.timeline_days_ago, min / 1440)
        }
    }
}
