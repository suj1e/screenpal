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

        // +-2px tolerance on every edge.
        assertEquals(10.0, blockA.left.toDouble(), 2.0)
        assertEquals(5.0, blockA.top.toDouble(), 2.0)
        assertEquals(29.0, blockA.right.toDouble(), 2.0)
        assertEquals(14.0, blockA.bottom.toDouble(), 2.0)

        assertEquals(50.0, blockB.left.toDouble(), 2.0)
        assertEquals(20.0, blockB.top.toDouble(), 2.0)
        assertEquals(79.0, blockB.right.toDouble(), 2.0)
        assertEquals(34.0, blockB.bottom.toDouble(), 2.0)
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
        assertEquals(20.0, box.left.toDouble(), 2.0)
        assertEquals(10.0, box.top.toDouble(), 2.0)
        assertEquals(58.0, box.right.toDouble(), 2.0)
        assertEquals(28.0, box.bottom.toDouble(), 2.0)
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
}
