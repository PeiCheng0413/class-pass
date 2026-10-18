package io.github.peicheng0413.classpass

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 一關存檔後的結果，用來在掃描畫面標示「新」「進步」。 */
enum class SaveOutcome { NEW, IMPROVED, UNCHANGED }

/** 紀錄存在手機內建的 SQLite。一個學生、一個遊戲、一關只有一列。 */
class Store(context: Context) : SQLiteOpenHelper(context, "classpass.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE records (
                game TEXT NOT NULL,
                cls TEXT NOT NULL,
                seat INTEGER NOT NULL,
                level TEXT NOT NULL,
                title TEXT NOT NULL,
                score INTEGER NOT NULL,
                max INTEGER NOT NULL,
                done_at INTEGER NOT NULL,
                scanned_at INTEGER NOT NULL,
                PRIMARY KEY (game, cls, seat, level)
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** 存一張 QR code 的所有關卡，每關只留最好的成績。回傳每關的結果（與 pass.levels 同順序）。 */
    fun save(pass: Pass, scannedAt: Long): List<SaveOutcome> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val outcomes = pass.levels.map { lv ->
                val old = find(db, pass.game, pass.cls, pass.seat, lv.level)
                val outcome = when {
                    old == null -> SaveOutcome.NEW
                    isBetter(lv, old) -> SaveOutcome.IMPROVED
                    else -> SaveOutcome.UNCHANGED
                }
                if (outcome != SaveOutcome.UNCHANGED) {
                    db.insertWithOnConflict(
                        "records", null,
                        ContentValues().apply {
                            put("game", pass.game)
                            put("cls", pass.cls)
                            put("seat", pass.seat)
                            put("level", lv.level)
                            put("title", lv.title)
                            put("score", lv.score)
                            put("max", lv.max)
                            put("done_at", lv.doneAt)
                            put("scanned_at", scannedAt)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
                outcome
            }
            db.setTransactionSuccessful()
            return outcomes
        } finally {
            db.endTransaction()
        }
    }

    /** 全部紀錄；game 為 null 時不篩選。依遊戲、班級、座號、完成時間排序。 */
    fun all(game: String? = null): List<Record> {
        val where = if (game == null) "" else "WHERE game = ?"
        val args = if (game == null) null else arrayOf(game)
        return query(readableDatabase, "$COLUMNS FROM records $where ORDER BY game, cls, seat, done_at", args)
    }

    fun games(): List<String> =
        readableDatabase.rawQuery("SELECT DISTINCT game FROM records ORDER BY game", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    fun clear() {
        writableDatabase.delete("records", null, null)
    }

    private fun find(db: SQLiteDatabase, game: String, cls: String, seat: Int, level: String): Record? =
        query(
            db, "$COLUMNS FROM records WHERE game = ? AND cls = ? AND seat = ? AND level = ?",
            arrayOf(game, cls, seat.toString(), level),
        ).firstOrNull()

    private fun query(db: SQLiteDatabase, sql: String, args: Array<String>?): List<Record> =
        db.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Record(
                            game = c.getString(0), level = c.getString(1), title = c.getString(2),
                            cls = c.getString(3), seat = c.getInt(4), score = c.getInt(5),
                            max = c.getInt(6), doneAt = c.getLong(7), scannedAt = c.getLong(8),
                        ),
                    )
                }
            }
        }

    private companion object {
        const val COLUMNS = "SELECT game, level, title, cls, seat, score, max, done_at, scanned_at"
    }
}
