package com.suj1e.screenpal

import android.media.projection.MediaProjection
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
