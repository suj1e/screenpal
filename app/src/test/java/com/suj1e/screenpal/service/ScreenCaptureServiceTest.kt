package com.suj1e.screenpal.service

import com.suj1e.screenpal.ScreenPalApplication
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScreenCaptureServiceTest {

    @Before
    fun setup() {
        mockkStatic(android.os.ResultReceiver::class)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun companion_constants_defined() {
        // Verify constants exist and have expected values
        assertEquals(1001, ScreenCaptureService.NOTIFICATION_ID)
        assertEquals("ScreenPal_Capture", ScreenCaptureService.CHANNEL_ID)
        assertEquals(12000L, ScreenCaptureService.CAPTURE_TIMEOUT_MS)
        assertEquals(10000L, ScreenCaptureService.IMAGE_WAIT_TIMEOUT_MS)
        assertEquals(85, ScreenCaptureService.JPEG_QUALITY)
        assertEquals("screenshots", ScreenCaptureService.CAPTURE_DIR)
        assertEquals("extra_result_code", ScreenCaptureService.EXTRA_RESULT_CODE)
        assertEquals("extra_result_data", ScreenCaptureService.EXTRA_RESULT_DATA)
        assertEquals("extra_result_receiver", ScreenCaptureService.EXTRA_RESULT_RECEIVER)
        assertEquals(-1, ScreenCaptureService.RESULT_OK)
        assertEquals(1, ScreenCaptureService.RESULT_ERROR)
    }
}
