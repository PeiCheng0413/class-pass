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

private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** 長表格式 CSV，開頭加 UTF-8 BOM，Excel 開中文才不會亂碼。 */
fun toCsv(records: List<Record>, zone: ZoneId = ZoneId.systemDefault()): String {
    fun time(sec: Long) = TIME.format(Instant.ofEpochSecond(sec).atZone(zone))
    val sb = StringBuilder("﻿")
    val header = listOf("遊戲", "關卡代號", "關卡名稱", "班級", "座號", "分數", "滿分", "完成時間", "掃描時間")
    sb.append(header.joinToString(",")).append("\r\n")
    for (r in records) {
        val row = listOf(
            csvText(r.game), csvText(r.level), csvText(r.title), csvText(r.cls),
            r.seat.toString(), r.score.toString(), r.max.toString(),
            time(r.doneAt), time(r.scannedAt),
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
