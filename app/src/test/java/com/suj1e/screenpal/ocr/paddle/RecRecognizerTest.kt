package com.suj1e.screenpal.ocr.paddle

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Greedy CTC decoding tests: built logits -> expected text / confidence.
 * Fake layout with a 4-char dictionary ["你","好","世","界"]:
 * class 0 = blank, classes 1..4 = dictionary, class 5 = space.
 */
class RecRecognizerTest {

    private val dict = listOf("你", "好", "世", "界")
    private val numClasses = dict.size + 2 // blank + dict + space

    private fun recognizer() = RecRecognizer(sessionProvider = { null }, dictionary = dict)

    /** Builds [T][C] logits where every step is one-hot on the given class. */
    private fun oneHot(steps: List<Int>, confidence: Float = 0.9f): FloatArray {
        val logits = FloatArray(steps.size * numClasses)
        steps.forEachIndexed { t, cls ->
            logits[t * numClasses + cls] = confidence
        }
        return logits
    }

    private fun text(recognizer: RecRecognizer, steps: List<Int>): String =
        recognizer.decodeLogits(oneHot(steps), steps.size, numClasses).text

    @Test
    fun decodes_normal_sentence() {
        val rec = recognizer()
        assertEquals("你好世界", text(rec, listOf(1, 2, 3, 4)))
    }

    @Test
    fun collapses_consecutive_duplicates() {
        val rec = recognizer()
        assertEquals("你好", text(rec, listOf(1, 1, 2, 2)))
    }

    @Test
    fun blank_separates_identical_characters() {
        val rec = recognizer()
        // blank between two identical classes keeps both characters.
        assertEquals("你你", text(rec, listOf(1, 0, 1)))
        // blank at the edges and between distinct classes is skipped.
        assertEquals("你好", text(rec, listOf(0, 1, 0, 2, 0)))
    }

    @Test
    fun space_class_decodes_to_space() {
        val rec = recognizer()
        val spaceClass = dict.size + 1
        assertEquals("你 好", text(rec, listOf(1, spaceClass, 2)))
    }

    @Test
    fun all_blank_decodes_to_empty() {
        val rec = recognizer()
        assertEquals("", text(rec, listOf(0, 0, 0)))
    }

    @Test
    fun confidence_is_mean_of_selected_time_steps() {
        val rec = recognizer()
        // Manually crafted distribution: step 0 max at class 1 (p=0.8),
        // step 1 max at class 2 (p=0.6). Mean over selected steps = 0.7.
        val logits = floatArrayOf(
            // class0=blank, class1=你, class2=好, class3=世, class4=界, class5=space
            0.1f, 0.8f, 0.1f, 0.0f, 0.0f, 0.0f,
            0.2f, 0.1f, 0.6f, 0.1f, 0.0f, 0.0f
        )
        val decoded = rec.decodeLogits(logits, timeSteps = 2, numClasses = numClasses)
        assertEquals("你好", decoded.text)
        assertEquals(0.7f, decoded.confidence, 1e-3f)
    }

    @Test
    fun dictionary_index_mapping_matches_lines() {
        // class i (1-based) must map to dictionary line i-1.
        val rec = recognizer()
        assertEquals("世界", text(rec, listOf(3, 4)))
    }

    @Test
    fun dictionary_loader_keeps_every_line_without_trailing_blank() {
        val dict = RecRecognizer.loadDictionary("你\n好\n世界".byteInputStream())
        assertEquals(listOf("你", "好", "世界"), dict)
    }
}
