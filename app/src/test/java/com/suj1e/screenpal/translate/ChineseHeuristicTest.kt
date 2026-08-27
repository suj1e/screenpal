package com.suj1e.screenpal.translate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 四用例：纯中文 / 纯英文 / 混合 / 数字符号；另含空文本边界。
 * 规则：CJK 表意字符（U+4E00–U+9FFF）占非空白字符比例 >= 0.5 → 中文；
 * 空文本、纯数字/符号 → 视为中文直读路径。
 */
class ChineseHeuristicTest {

    @Test
    fun pureChineseText_isMostlyChinese() {
        assertTrue(ChineseHeuristic.isMostlyChinese("今天天气真不错"))
    }

    @Test
    fun pureEnglishText_isNotMostlyChinese() {
        assertFalse(ChineseHeuristic.isMostlyChinese("Hello, world!"))
    }

    @Test
    fun mixedText_atHalfCjkRatio_isMostlyChinese() {
        // 6 个 CJK + 6 个非空白拉丁字符 = 比例恰为 0.5 → 中文
        assertTrue(ChineseHeuristic.isMostlyChinese("你好世界 abcd"))
        // 4 个 CJK + 6 个非空白拉丁字符 = 比例 0.4 → 非中文
        assertFalse(ChineseHeuristic.isMostlyChinese("你好世界 abcdef"))
    }

    @Test
    fun digitsAndSymbolsOnly_treatedAsChineseDirectPath() {
        assertTrue(ChineseHeuristic.isMostlyChinese("12345 + = 67.8%"))
        assertTrue(ChineseHeuristic.isMostlyChinese("*** ### ***"))
    }

    @Test
    fun emptyOrBlankText_treatedAsChineseDirectPath() {
        assertTrue(ChineseHeuristic.isMostlyChinese(""))
        assertTrue(ChineseHeuristic.isMostlyChinese("   \n\t "))
    }
}
