package com.suj1e.screenpal.ocr.paddle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic probability-map tests for the DBNet post-processing: thresholding,
 * connected components, axis-aligned boxes, and mapping back to original image
 * coordinates.
 */
class DbPostProcessorTest {

    private val processor = DbPostProcessor()

    private val mapWidth = 100
    private val mapHeight = 40

    /** Fills rows top..bottom, cols left..right (inclusive) with [value]. */
    private fun fill(
        probability: FloatArray,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        value: Float
    ) {
        for (y in top..bottom) {
            for (x in left..right) {
                probability[y * mapWidth + x] = value
            }
        }
    }

    @Test
    fun two_text_regions_produce_two_boxes() {
        val probability = FloatArray(mapWidth * mapHeight)
        // Text block A: rows 5..14, cols 10..29
        fill(probability, 10, 5, 29, 14, 0.9f)
        // Text block B: rows 20..34, cols 50..79
        fill(probability, 50, 20, 79, 34, 0.8f)

        val boxes = processor.process(
            probability, mapWidth, mapHeight,
            ratioX = 1f, ratioY = 1f,
            origWidth = mapWidth, origHeight = mapHeight
        )

        assertEquals(2, boxes.size)
        val sorted = boxes.sortedWith(compareBy({ it.top }, { it.left }))
        val blockA = sorted[0]
        val blockB = sorted[1]

        // Unclip 0.15 outward expansion (std DBNet post-processing).
        assertEquals(8.0, blockA.left.toDouble(), 1.0)
        assertEquals(4.0, blockA.top.toDouble(), 1.0)
        assertEquals(31.0, blockA.right.toDouble(), 1.0)
        assertEquals(15.0, blockA.bottom.toDouble(), 1.0)

        assertEquals(47.0, blockB.left.toDouble(), 1.0)
        assertEquals(19.0, blockB.top.toDouble(), 1.0)
        assertEquals(82.0, blockB.right.toDouble(), 1.0)
        assertEquals(35.0, blockB.bottom.toDouble(), 1.0)
    }

    @Test
    fun empty_probability_map_returns_empty_list() {
        val probability = FloatArray(mapWidth * mapHeight) // all zeros

        val boxes = processor.process(
            probability, mapWidth, mapHeight,
            ratioX = 1f, ratioY = 1f,
            origWidth = mapWidth, origHeight = mapHeight
        )

        assertTrue(boxes.isEmpty())
    }

    @Test
    fun boxes_mapped_back_to_original_coordinates() {
        val probability = FloatArray(mapWidth * mapHeight)
        fill(probability, 10, 5, 29, 14, 0.9f)

        val boxes = processor.process(
            probability, mapWidth, mapHeight,
            ratioX = 0.5f, ratioY = 0.5f,
            origWidth = 200, origHeight = 80
        )

        assertEquals(1, boxes.size)
        val box = boxes[0]
        // Unclip 0.15: padX=20*0.15/0.85/2≈1.76, padY=10*0.15/0.85/2≈0.88 → /0.5
        assertEquals(16.0, box.left.toDouble(), 1.0)
        assertEquals(8.0, box.top.toDouble(), 1.0)
        assertEquals(61.0, box.right.toDouble(), 1.0)
        assertEquals(30.0, box.bottom.toDouble(), 1.0)
    }

    @Test
    fun mapped_boxes_clamped_to_original_bounds() {
        val probability = FloatArray(mapWidth * mapHeight)
        fill(probability, 0, 0, 9, 9, 0.9f)

        val boxes = processor.process(
            probability, mapWidth, mapHeight,
            ratioX = 0.9f, ratioY = 0.9f,
            origWidth = mapWidth, origHeight = mapHeight
        )

        assertEquals(1, boxes.size)
        assertTrue(boxes[0].left >= 0)
        assertTrue(boxes[0].top >= 0)
        assertTrue(boxes[0].right < mapWidth)
        assertTrue(boxes[0].bottom < mapHeight)
    }

    @Test
    fun tiny_regions_below_min_size_are_filtered() {
        val probability = FloatArray(mapWidth * mapHeight)
        fill(probability, 30, 10, 30, 10, 0.99f) // single hot pixel

        val boxes = processor.process(
            probability, mapWidth, mapHeight,
            ratioX = 1f, ratioY = 1f,
            origWidth = mapWidth, origHeight = mapHeight
        )

        assertTrue(boxes.isEmpty())
    }

    @Test
    fun `boxes expand outward by unclip ratio and clamp to original bounds`() {
        // 30x30 solid block in a 60x60 prob map; ratio 0.5 => scaled box 15x15 in original.
        val map = FloatArray(60 * 60) { 0f }
        for (y in 15 until 45) for (x in 15 until 45) map[y * 60 + x] = 1f
        val processor = DbPostProcessor(binThreshold = 0.3f, minSide = 4)
        val boxes = processor.process(map, 60, 60, ratioX = 0.5f, ratioY = 0.5f, origWidth = 120, origHeight = 120)

        assertEquals(1, boxes.size)
        val b = boxes[0]
        // Raw box would be [30..60); unclip 0.15 grows each side by 0.15/0.85 of the box,
        // so left must be strictly below 30 and right above 60 (clamped at 119).
        assertTrue("expected outward growth, left=${b.left}", b.left < 30)
        assertTrue("expected outward growth, right=${b.right}", b.right > 60)
        assertTrue(b.left >= 0 && b.top >= 0 && b.right <= 119 && b.bottom <= 119)
    }
}
