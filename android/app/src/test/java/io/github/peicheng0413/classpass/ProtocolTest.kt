package io.github.peicheng0413.classpass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 測試向量來自 docs/PROTOCOL.md，是網頁端（Web Crypto）與 Python 算出來的。 */
class ProtocolTest {
    private val farm = "CP1|robot-farm-practice|701|7|check-dry~澆水~3~3~1760000000;" +
        "harvest~收成~2~3~1760000300;sort-seeds~分種子~3~3~1760000900;" +
        "night-watch~守夜~1~3~1760001500|SjdxLZjvH956zyUB"
    private val demo = "CP1|demo|8年3班|23|level-2~level-2~85~100~1760003600|627Pc4prOkVRT51j"

    private fun valid(text: String): Pass {
        val r = Protocol.parse(text)
        assertTrue("應該有效：$text → $r", r is ParseResult.Valid)
        return (r as ParseResult.Valid).pass
    }

    private fun assertInvalid(text: String) {
        val r = Protocol.parse(text)
        assertTrue("應該無效：$text", r is ParseResult.Invalid)
    }

    /** 用金鑰簽出「簽章正確但內容違規」的資料 */
    private fun signed(body: String) = body + Protocol.sign(body)

    @Test fun jsVectorsAreValid() {
        val p = valid(farm)
        assertEquals("robot-farm-practice", p.game)
        assertEquals("701", p.cls)
        assertEquals(7, p.seat)
        assertEquals(listOf("check-dry", "harvest", "sort-seeds", "night-watch"), p.levels.map { it.level })
        assertEquals(LevelResult("harvest", "收成", 2, 3, 1760000300), p.levels[1])

        val d = valid(demo)
        assertEquals("8年3班", d.cls)
        assertEquals(LevelResult("level-2", "level-2", 85, 100, 1760003600), d.levels.single())
    }

    @Test fun signMatchesJs() {
        assertEquals("627Pc4prOkVRT51j", Protocol.sign(demo.substringBeforeLast('|') + "|"))
    }

    @Test fun tamperedVectorsAreInvalid() {
        assertInvalid(demo.dropLast(1) + "X")
        assertInvalid(demo.replace("~85~", "~95~"))
        assertInvalid(demo.substringBeforeLast('|'))
        assertInvalid(demo.replaceFirst("CP1", "CP2"))
        assertInvalid("https://example.com/")
        assertInvalid("")
    }

    @Test fun signedButBreakingRulesIsInvalid() {
        // 對照組：同樣方式簽出來的正常資料要有效
        valid(signed("CP1|demo|701|7|a~甲~1~3~1760000000|"))

        assertInvalid(signed("CP1|demo|701|7|a~甲~1~3~1760000000;a~乙~2~3~1760000001|")) // 關卡重複
        assertInvalid(signed("CP1|demo|701|7|a~甲~4~3~1760000000|"))                      // 分數 > 滿分
        assertInvalid(signed("CP1|demo|701|7|a~甲~0~0~1760000000|"))                      // 滿分 0
        assertInvalid(signed("CP1|demo|701|07|a~甲~1~3~1760000000|"))                     // 座號前導零
        assertInvalid(signed("CP1|demo|701|1000|a~甲~1~3~1760000000|"))                   // 座號太大
        assertInvalid(signed("CP1|demo|701|7|a~一二三四五六七八九十一~1~3~1760000000|"))     // 名稱 11 字
        assertInvalid(signed("CP1|demo| 701|7|a~甲~1~3~1760000000|"))                     // 班級頭尾空白
        assertInvalid(signed("CP1|demo||7|a~甲~1~3~1760000000|"))                         // 班級空白
        assertInvalid(signed("CP1|demo|701|7|a~甲~1~3|"))                                 // 少一個子欄位
        assertInvalid(signed("CP1|demo|701|7||"))                                         // 沒有關卡
        assertInvalid(signed("CP1|demo_x|701|7|a~甲~1~3~1760000000|"))                    // 遊戲代號有底線
        assertInvalid(signed("CP1|demo|701|7|a~甲~01~3~1760000000|"))                     // 分數前導零
    }

    @Test fun tenCharTitleAndThirtyLevelsAreValid() {
        valid(signed("CP1|demo|701|7|a~一二三四五六七八九十~1~3~1760000000|"))
        val thirty = (1..30).joinToString(";") { "l$it~第${it}關~1~1~1760000000" }
        assertEquals(30, valid(signed("CP1|demo|701|7|$thirty|")).levels.size)
        val thirtyOne = (1..31).joinToString(";") { "l$it~第${it}關~1~1~1760000000" }
        assertInvalid(signed("CP1|demo|701|7|$thirtyOne|"))
    }
}
