package io.github.peicheng0413.classpass

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 存在手機裡的一筆紀錄：一個學生、一個遊戲、一關。 */
data class Record(
    val game: String,
    val level: String,
    val title: String,
    val cls: String,
    val seat: Int,
    val score: Int,
    val max: Int,
    /** 完成時間，Unix 秒 */
    val doneAt: Long,
    /** 掃描時間，Unix 秒 */
    val scannedAt: Long,
    /** 這位學生在這個遊戲的用時（秒）；沒有就是 null */
    val elapsed: Long? = null,
    /** 其他（未定義的）附加資料，原樣的「key=value,key=value」 */
    val otherExtras: String = "",
)

/**
 * 同一個學生同一關只留最好的成績：分數高者勝；同分留完成時間較早的。
 * 滿分不同（遊戲改過計分方式）時以新的為準。
 */
fun isBetter(new: LevelResult, old: Record): Boolean = when {
    new.max != old.max -> true
    new.score != old.score -> new.score > old.score
    else -> new.doneAt < old.doneAt
}

/**
 * 合併同一學生同一遊戲的附加資料（PROTOCOL.md「附加資料」）：
 * elapsed 留最小的，其他 key 以新的為準。
 */
fun mergeExtras(old: Map<String, String>, new: Map<String, String>): Map<String, String> {
    val merged = LinkedHashMap(old)
    for ((key, value) in new) {
        val prev = merged[key]
        if (key == "elapsed" && prev != null && prev.toLong() <= value.toLong()) continue
        merged[key] = value
    }
    return merged
}

fun encodeExtras(extras: Map<String, String>): String =
    extras.entries.joinToString(",") { "${it.key}=${it.value}" }

fun decodeExtras(text: String): Map<String, String> =
    if (text.isEmpty()) emptyMap()
    else text.split(',').associate { it.substringBefore('=') to it.substringAfter('=') }

/** 754 → 「12:34」（分:秒，超過一小時就是 61:40 這樣） */
fun formatElapsed(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)

private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** 長表格式 CSV，開頭加 UTF-8 BOM，Excel 開中文才不會亂碼。 */
fun toCsv(records: List<Record>, zone: ZoneId = ZoneId.systemDefault()): String {
    fun time(sec: Long) = TIME.format(Instant.ofEpochSecond(sec).atZone(zone))
    val sb = StringBuilder("\uFEFF")
    val header = listOf(
        "遊戲", "關卡代號", "關卡名稱", "班級", "座號", "分數", "滿分", "完成時間", "掃描時間",
        "用時（秒）", "用時", "其他附加資料",
    )
    sb.append(header.joinToString(",")).append("\r\n")
    for (r in records) {
        val row = listOf(
            csvText(r.game), csvText(r.level), csvText(r.title), csvText(r.cls),
            r.seat.toString(), r.score.toString(), r.max.toString(),
            time(r.doneAt), time(r.scannedAt),
            r.elapsed?.toString() ?: "", r.elapsed?.let(::formatElapsed) ?: "", csvText(r.otherExtras),
        )
        sb.append(row.joinToString(",")).append("\r\n")
    }
    return sb.toString()
}

/** 文字欄位：開頭是 = + - @ 時前面加 '，避免 Excel 當成公式；有逗號或引號時加引號。 */
private fun csvText(s: String): String {
    val safe = if (s.isNotEmpty() && s[0] in "=+-@") "'$s" else s
    return if (safe.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
        "\"" + safe.replace("\"", "\"\"") + "\""
    } else {
        safe
    }
}
