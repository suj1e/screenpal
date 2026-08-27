package com.suj1e.screenpal.ocr.paddle
import kotlin.math.roundToInt

/**
 * DBNet probability-map post-processing: threshold at 0.3, extract connected
 * components (BFS), take each component's axis-aligned bounding box, and map
 * boxes back to original image coordinates.
 *
 * The det model already emits a sigmoid probability map ([1,1,H,W]), so no
 * sigmoid is applied here.
 */
class DbPostProcessor(
    private val binThreshold: Float = 0.3f,
    private val minSide: Int = 3
) {
    companion object {
        const val UNCLIP_RATIO = 0.15f
    }

    /** Axis-aligned box in original image coordinates; right/bottom inclusive. */
    data class DetectedBox(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    fun process(
        probability: FloatArray,
        mapWidth: Int,
        mapHeight: Int,
        ratioX: Float,
        ratioY: Float,
        origWidth: Int,
        origHeight: Int
    ): List<DetectedBox> {
        require(probability.size >= mapWidth * mapHeight) {
            "probability map size ${probability.size} < ${mapWidth}x$mapHeight"
        }

        val pixelCount = mapWidth * mapHeight
        val visited = BooleanArray(pixelCount)
        val queue = IntArray(pixelCount)
        val boxes = mutableListOf<DetectedBox>()

        for (start in 0 until pixelCount) {
            if (visited[start] || probability[start] <= binThreshold) continue

            // BFS over 4-connected foreground pixels, tracking the bbox.
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var minX = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var minY = Int.MAX_VALUE
            var maxY = Int.MIN_VALUE

            while (head < tail) {
                val current = queue[head++]
                val x = current % mapWidth
                val y = current / mapWidth

                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                if (x > 0 && !visited[current - 1] && probability[current - 1] > binThreshold) {
                    visited[current - 1] = true
                    queue[tail++] = current - 1
                }
                if (x < mapWidth - 1 && !visited[current + 1] && probability[current + 1] > binThreshold) {
                    visited[current + 1] = true
                    queue[tail++] = current + 1
                }
                if (y > 0 && !visited[current - mapWidth] && probability[current - mapWidth] > binThreshold) {
                    visited[current - mapWidth] = true
                    queue[tail++] = current - mapWidth
                }
                if (y < mapHeight - 1 && !visited[current + mapWidth] && probability[current + mapWidth] > binThreshold) {
                    visited[current + mapWidth] = true
                    queue[tail++] = current + mapWidth
                }
            }

            // Drop specks that cannot be text lines.
            if (maxX - minX + 1 < minSide || maxY - minY + 1 < minSide) continue

            // DBNet trains on shrunk regions, so the raw box clips edge glyphs.
            // Expand each side by UNCLIP_RATIO before mapping back (std. unclip).
            val boxW = (maxX - minX + 1).toFloat()
            val boxH = (maxY - minY + 1).toFloat()
            val padX = boxW * UNCLIP_RATIO / (1 - UNCLIP_RATIO) / 2
            val padY = boxH * UNCLIP_RATIO / (1 - UNCLIP_RATIO) / 2
            val exMinX = minX - padX
            val exMaxX = maxX + padX
            val exMinY = minY - padY
            val exMaxY = maxY + padY

            // Map back to original coordinates (ratio = scaled / original).
            val left = (exMinX / ratioX).roundToInt().coerceIn(0, origWidth - 1)
            val top = (exMinY / ratioY).roundToInt().coerceIn(0, origHeight - 1)
            val right = (exMaxX / ratioX).roundToInt().coerceIn(0, origWidth - 1)
            val bottom = (exMaxY / ratioY).roundToInt().coerceIn(0, origHeight - 1)

            if (left <= right && top <= bottom) {
                boxes += DetectedBox(left, top, right, bottom)
            }
        }

        // Reading order: top-to-bottom, then left-to-right.
        boxes.sortWith(compareBy({ it.top }, { it.left }))
        return boxes
    }
}
