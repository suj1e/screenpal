package com.suj1e.screenpal.service

import com.suj1e.screenpal.ScreenPalApplication
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FloatingWindowServiceTest {

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
        assertEquals(1001, FloatingWindowService.ERROR_NEED_AUTH)
        assertEquals(1002, FloatingWindowService.ERROR_CAPTURE_FAILED)
    }

    @Test
    fun interface_captureCallback_exists() {
        // Verify interface can be implemented
        val callback = object : FloatingWindowService.CaptureCallback {
            override fun onSuccess(uri: android.net.Uri?) {}
            override fun onError(error: Int) {}
        }

        callback.onSuccess(null)
        callback.onError(0)
    }
}
