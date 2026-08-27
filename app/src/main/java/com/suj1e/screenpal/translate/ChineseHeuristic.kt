package com.suj1e.screenpal.translate

/**
 * Lightweight language gate for the broadcast pipeline: decides whether OCR
 * text can be read aloud as-is (Chinese) or needs translation first.
 *
 * Rule: CJK ideographs (U+4E00–U+9FFF) accounting for >= half of the
 * non-whitespace characters means Chinese. Empty text and text made only of
 * digits/symbols carry no foreign language to translate, so they take the
 * Chinese direct path too (zero network).
 */
object ChineseHeuristic {

    /** Ratio threshold: cjk / non-whitespace >= 0.5 counts as Chinese. */
    internal const val CJK_RATIO_THRESHOLD = 0.5

    fun isMostlyChinese(text: String): Boolean {
        var cjk = 0
        var letters = 0
        var nonWhitespace = 0
        for (c in text) {
            if (c.isWhitespace()) continue
            nonWhitespace++
            if (c.code in CJK_IDEOGRAPH_START..CJK_IDEOGRAPH_END) cjk++
            if (c.isLetter()) letters++
        }
        // Empty (or whitespace-only) text: nothing to translate, read directly.
        if (nonWhitespace == 0) return true
        // Pure digits/symbols: no translatable letters, treat as Chinese path.
        if (letters == 0) return true
        return cjk.toDouble() / nonWhitespace >= CJK_RATIO_THRESHOLD
    }

    private const val CJK_IDEOGRAPH_START = 0x4E00
    private const val CJK_IDEOGRAPH_END = 0x9FFF
}
