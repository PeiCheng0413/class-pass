package io.github.peicheng0413.classpass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class RecordsTest {
    private val old = Record("g", "a", "甲", "701", 7, 2, 3, doneAt = 1000, scannedAt = 2000)

    @Test fun bestRule() {
        assertTrue(isBetter(LevelResult("a", "甲", 3, 3, 1500), old))   // 分數較高
        assertFalse(isBetter(LevelResult("a", "甲", 1, 3, 500), old))   // 分數較低
        assertTrue(isBetter(LevelResult("a", "甲", 2, 3, 900), old))    // 同分、較早完成
        assertFalse(isBetter(LevelResult("a", "甲", 2, 3, 1000), old))  // 同一筆再掃一次
        assertTrue(isBetter(LevelResult("a", "甲", 1, 5, 1500), old))   // 滿分改了，以新的為準
    }

    @Test fun csv() {
        val tpe = ZoneId.of("Asia/Taipei")
        val csv = toCsv(
            listOf(
                old.copy(title = "澆水, 施肥", doneAt = 1760000000, scannedAt = 1760000060, elapsed = 754),
                old.copy(cls = "=1+1", title = "說\"好\""),
            ),
            tpe,
        )
        val lines = csv.split("\r\n")
        assertEquals(
            "\uFEFF遊戲,關卡代號,關卡名稱,班級,座號,分數,滿分,完成時間,掃描時間,用時（秒）,用時,其他附加資料",
            lines[0],
        )
        assertEquals("g,a,\"澆水, 施肥\",701,7,2,3,2025-10-09 16:53:20,2025-10-09 16:54:20,754,12:34,", lines[1])
        assertTrue(lines[2].startsWith("g,a,\"說\"\"好\"\"\",'=1+1,7,"))
        assertTrue(lines[2].endsWith(",,,"))
        assertEquals("", lines.last())
    }

    @Test fun mergeExtrasKeepsSmallestElapsed() {
        val old = mapOf("elapsed" to "800", "hints" to "3")
        assertEquals(mapOf("elapsed" to "754", "hints" to "3"), mergeExtras(old, mapOf("elapsed" to "754")))
        assertEquals(mapOf("elapsed" to "800", "hints" to "1"), mergeExtras(old, mapOf("elapsed" to "900", "hints" to "1")))
        assertEquals(mapOf("elapsed" to "60"), mergeExtras(emptyMap(), mapOf("elapsed" to "60")))
        // 數字比較，不是字串比較："1000" 比 "800" 大
        assertEquals("800", mergeExtras(old, mapOf("elapsed" to "1000"))["elapsed"])
    }

    @Test fun extrasRoundTrip() {
        val extras = linkedMapOf("elapsed" to "60", "ver" to "1.2-b")
        assertEquals("elapsed=60,ver=1.2-b", encodeExtras(extras))
        assertEquals(extras, decodeExtras("elapsed=60,ver=1.2-b"))
        assertEquals(emptyMap<String, String>(), decodeExtras(""))
    }

    @Test fun elapsedFormat() {
        assertEquals("12:34", formatElapsed(754))
        assertEquals("0:05", formatElapsed(5))
        assertEquals("61:40", formatElapsed(3700))
    }
}
