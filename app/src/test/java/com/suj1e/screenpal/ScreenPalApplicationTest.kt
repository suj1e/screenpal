package com.suj1e.screenpal

import android.media.projection.MediaProjection
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class ScreenPalApplicationTest {

    @Test
    fun mediaProjection_lifecycle_setAndRelease() {
        val app = ScreenPalApplication()

        // Initial state: no MediaProjection
        assertFalse(app.hasValidMediaProjection())

        // Set MediaProjection
        val projection = mockk<MediaProjection>(relaxed = true)
        app.setMediaProjection(projection)
        assertTrue(app.hasValidMediaProjection())

        // Release
        app.releaseMediaProjection()
        assertFalse(app.hasValidMediaProjection())
    }

    @Test
    fun mediaProjection_releaseTwice_noCrash() {
        val app = ScreenPalApplication()
        assertFalse(app.hasValidMediaProjection())

        // Release when null should not crash
        app.releaseMediaProjection()
        assertFalse(app.hasValidMediaProjection())
    }
}

/**
 * Recording subclass: overrides [ScreenPalApplication.warmUpTts] so the real TTS
 * warm-up (model download) never runs in tests, while verifying that onCreate wires it up.
 */
internal class WarmUpRecordingApplication : ScreenPalApplication() {
    var warmUpTtsCallCount = 0

    override fun warmUpTts() {
        warmUpTtsCallCount++
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = WarmUpRecordingApplication::class)
class ScreenPalApplicationWarmUpTtsTest {

    @Test
    fun onCreate_callsWarmUpTts() {
        // Robolectric instantiates, attaches and calls onCreate() on the configured
        // application before running the test method (no setupApplication in 4.11.1).
        val app = RuntimeEnvironment.getApplication() as WarmUpRecordingApplication

        assertEquals(1, app.warmUpTtsCallCount)
    }
}
