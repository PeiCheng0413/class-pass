package io.github.peicheng0413.classpass

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 一關存檔後的結果，用來在掃描畫面標示「新」「進步」。 */
enum class SaveOutcome { NEW, IMPROVED, UNCHANGED }

/** 紀錄存在手機內建的 SQLite。一個學生、一個遊戲、一關只有一列。 */
class Store(context: Context) : SQLiteOpenHelper(context, "classpass.db", null, 2) {

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
        createExtras(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createExtras(db)
    }

    /** 附加資料（CP2）以「學生＋遊戲」為單位存，data 是「key=value,key=value」 */
    private fun createExtras(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE extras (
                game TEXT NOT NULL,
                cls TEXT NOT NULL,
                seat INTEGER NOT NULL,
                data TEXT NOT NULL,
                PRIMARY KEY (game, cls, seat)
            )
            """.trimIndent(),
        )
    }

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
            if (pass.extras.isNotEmpty()) saveExtras(db, pass)
            db.setTransactionSuccessful()
            return outcomes
        } finally {
            db.endTransaction()
        }
    }

    private fun saveExtras(db: SQLiteDatabase, pass: Pass) {
        val old = db.rawQuery(
            "SELECT data FROM extras WHERE game = ? AND cls = ? AND seat = ?",
            arrayOf(pass.game, pass.cls, pass.seat.toString()),
        ).use { c -> if (c.moveToFirst()) decodeExtras(c.getString(0)) else emptyMap() }
        db.insertWithOnConflict(
            "extras", null,
            ContentValues().apply {
                put("game", pass.game)
                put("cls", pass.cls)
                put("seat", pass.seat)
                put("data", encodeExtras(mergeExtras(old, pass.extras)))
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /** 全部紀錄（含附加資料）；game 為 null 時不篩選。依遊戲、班級、座號、完成時間排序。 */
    fun all(game: String? = null): List<Record> {
        val where = if (game == null) "" else "WHERE r.game = ?"
        val args = if (game == null) null else arrayOf(game)
        return query(
            readableDatabase,
            "$COLUMNS, e.data FROM records r LEFT JOIN extras e " +
                "ON e.game = r.game AND e.cls = r.cls AND e.seat = r.seat " +
                "$where ORDER BY r.game, r.cls, r.seat, r.done_at",
            args,
        )
    }

    fun games(): List<String> =
        readableDatabase.rawQuery("SELECT DISTINCT game FROM records ORDER BY game", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    /** 刪除一位學生在一個遊戲的所有關卡 */
    fun deleteStudent(game: String, cls: String, seat: Int) {
        val args = arrayOf(game, cls, seat.toString())
        writableDatabase.delete("records", "game = ? AND cls = ? AND seat = ?", args)
        writableDatabase.delete("extras", "game = ? AND cls = ? AND seat = ?", args)
    }

    fun clear() {
        writableDatabase.delete("records", null, null)
        writableDatabase.delete("extras", null, null)
    }

    private fun find(db: SQLiteDatabase, game: String, cls: String, seat: Int, level: String): Record? =
        query(
            db, "$COLUMNS FROM records r WHERE game = ? AND cls = ? AND seat = ? AND level = ?",
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
                        ).withExtras(if (c.columnCount > 9 && !c.isNull(9)) decodeExtras(c.getString(9)) else emptyMap()),
                    )
                }
            }
        }

    private companion object {
        const val COLUMNS =
            "SELECT r.game, r.level, r.title, r.cls, r.seat, r.score, r.max, r.done_at, r.scanned_at"
    }
}

private fun Record.withExtras(extras: Map<String, String>) = copy(
    elapsed = extras["elapsed"]?.toLongOrNull(),
    otherExtras = encodeExtras(extras - "elapsed"),
)
