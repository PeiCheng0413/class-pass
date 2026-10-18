package io.github.peicheng0413.classpass

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 一張 QR code 裡的一關。 */
data class LevelResult(
    val level: String,
    val title: String,
    val score: Int,
    val max: Int,
    /** 完成時間，Unix 秒 */
    val doneAt: Long,
)

/** 一張驗證通過的 QR code：一個學生在一個遊戲的所有已過關關卡，加上遊戲層級的附加資料。 */
data class Pass(
    val game: String,
    val cls: String,
    val seat: Int,
    val levels: List<LevelResult>,
    /** 附加資料（CP2），依 QR 裡的順序；CP1 一律是空的 */
    val extras: Map<String, String> = emptyMap(),
)

sealed interface ParseResult {
    data class Valid(val pass: Pass) : ParseResult
    data class Invalid(val reason: String) : ParseResult
}

/** CP2（與舊版 CP1）協定，規則見 docs/PROTOCOL.md。這裡不依賴 Android，JVM 單元測試可直接跑。 */
object Protocol {
    /** 版本 → 用 | 切開的段數 */
    private val VERSIONS = mapOf("CP1" to 6, "CP2" to 7)
    private const val EXTRAS_MAX = 10
    private const val KEY = "7wtYq6EDHQJxEa6gbWgOLRac-2bzsmJv"
    private const val CLASS_MAX = 20
    private const val TITLE_MAX = 10
    private const val LEVELS_MAX = 30

    private val CODE = Regex("[A-Za-z0-9-]{1,40}")
    private val UINT = Regex("0|[1-9][0-9]{0,17}")
    private val SEAT = Regex("[1-9][0-9]{0,2}")
    private val SIG = Regex("[A-Za-z0-9_-]{16}")
    private val EXTRA_KEY = Regex("[a-z0-9-]{1,20}")
    private val EXTRA_VALUE = Regex("[A-Za-z0-9.-]{1,40}")
    private val FORBIDDEN = Regex("[|;~\\u0000-\\u001f\\u007f]")

    fun sign(body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(KEY.toByteArray(Charsets.US_ASCII), "HmacSHA256"))
        val digest = mac.doFinal(body.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16)
    }

    fun parse(text: String): ParseResult {
        val parts = text.split('|')
        if (!parts[0].startsWith("CP") || parts.size < 2) return invalid("不是過關用的 QR code")
        val expected = VERSIONS[parts[0]] ?: return invalid("不認得的版本 ${parts[0].take(8)}，app 可能需要更新")
        if (parts.size != expected) return invalid("格式不對（欄位數 ${parts.size}）")

        val sig = parts.last()
        val body = text.substring(0, text.length - sig.length)
        if (!SIG.matches(sig) ||
            !MessageDigest.isEqual(sign(body).toByteArray(), sig.toByteArray())
        ) {
            return invalid("簽章不對，可能被修改過")
        }

        val (_, game, cls, seat, list) = parts
        val extras = if (parts.size == 7) parseExtras(parts[5]) ?: return invalid("附加資料格式不對") else emptyMap()
        if (!CODE.matches(game)) return invalid("遊戲代號格式不對")
        if (!isCleanText(cls, CLASS_MAX)) return invalid("班級格式不對")
        if (!SEAT.matches(seat)) return invalid("座號格式不對")

        val items = list.split(';')
        if (items.size > LEVELS_MAX) return invalid("關卡太多")
        val levels = ArrayList<LevelResult>(items.size)
        val seen = HashSet<String>()
        for (item in items) {
            val f = item.split('~')
            if (f.size != 5) return invalid("關卡格式不對")
            val (level, title, score, max, doneAt) = f
            if (!CODE.matches(level)) return invalid("關卡代號格式不對")
            if (!seen.add(level)) return invalid("關卡重複：$level")
            if (!isCleanText(title, TITLE_MAX)) return invalid("關卡名稱格式不對")
            if (!UINT.matches(score) || !UINT.matches(max) || !UINT.matches(doneAt)) {
                return invalid("分數或時間格式不對")
            }
            val s = score.toIntOrNull() ?: return invalid("分數太大")
            val m = max.toIntOrNull() ?: return invalid("滿分太大")
            if (m < 1 || s > m) return invalid("分數超過滿分")
            levels += LevelResult(level, title, s, m, doneAt.toLong())
        }
        return ParseResult.Valid(Pass(game, cls, seat.toInt(), levels, extras))
    }

    /** 「key=value,key=value」→ 有序 Map；空字串是沒有附加資料；不合規則回傳 null */
    private fun parseExtras(text: String): Map<String, String>? {
        if (text.isEmpty()) return emptyMap()
        val items = text.split(',')
        if (items.size > EXTRAS_MAX) return null
        val map = LinkedHashMap<String, String>()
        for (item in items) {
            val eq = item.indexOf('=')
            if (eq < 0) return null
            val key = item.substring(0, eq)
            val value = item.substring(eq + 1)
            if (!EXTRA_KEY.matches(key) || !EXTRA_VALUE.matches(value)) return null
            if (map.put(key, value) != null) return null
        }
        // 已定義的 key 有自己的值規則
        map["elapsed"]?.let { if (!UINT.matches(it)) return null }
        return map
    }

    private fun isCleanText(s: String, max: Int): Boolean =
        s.isNotEmpty() && s == s.trim() && !FORBIDDEN.containsMatchIn(s) &&
            s.codePointCount(0, s.length) <= max

    private fun invalid(reason: String) = ParseResult.Invalid(reason)
}
