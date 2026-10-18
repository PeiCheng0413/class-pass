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
                old.copy(title = "澆水, 施肥", doneAt = 1760000000, scannedAt = 1760000060),
                old.copy(cls = "=1+1", title = "說\"好\""),
            ),
            tpe,
        )
        val lines = csv.split("\r\n")
        assertTrue(csv.startsWith("﻿遊戲,關卡代號,關卡名稱,班級,座號,分數,滿分,完成時間,掃描時間"))
        assertEquals("g,a,\"澆水, 施肥\",701,7,2,3,2025-10-09 16:53:20,2025-10-09 16:54:20", lines[1])
        assertTrue(lines[2].startsWith("g,a,\"說\"\"好\"\"\",'=1+1,7,"))
        assertEquals("", lines.last())
    }
}
