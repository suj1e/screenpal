package com.suj1e.screenpal.tts

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PiperTtsEngineTest {

    private fun newEngine(): PiperTtsEngine =
        PiperTtsEngine(
            context = mockk(relaxed = true),
            modelDownloader = mockk(relaxed = true)
        )

    @Test
    fun state_startsAsPending() {
        val engine = newEngine()
        assertEquals(PiperTtsEngine.State.PENDING, engine.state)
        assertFalse(engine.isInitialized)
    }

    @Test
    fun phonemeLookup_mapsKnownCharsAndSkipsUnknown() {
        val engine = newEngine()
        val config = PiperTtsEngine.PiperConfig(
            sampleRate = 22050,
            noiseScale = 0.667f,
            lengthScale = 1.0f,
            noiseWarp = 0.8f,
            phonemeIdMap = mapOf("你" to 10L, "好" to 20L),
            bosId = 1L,
            eosId = 2L,
            blankId = 0L
        )

        val ids = engine.textToPhonemeIds("你好§", config)

        // BOS + [你,blank] + [好,blank] + EOS ; unknown char skipped
        assertEquals(listOf(1L, 10L, 0L, 20L, 0L, 2L), ids.toList())
    }

    @Test
    fun wavWriter_producesParsableHeader() {
        val engine = newEngine()
        val pcm = shortArrayOf(0, 1, -1, 32767, -32768)
        val file = File.createTempFile("tts_test", ".wav")

        engine.writeWav(file, pcm, sampleRate = 22050)

        val bytes = file.readBytes()
        assertEquals(44 + pcm.size * 2, bytes.size)
        assertTrue(bytes.copyOfRange(0, 4).decodeToString() == "RIFF")
        assertTrue(bytes.copyOfRange(8, 12).decodeToString() == "WAVE")
        assertTrue(bytes.copyOfRange(36, 40).decodeToString() == "data")
        // Data chunk size (little endian) at offset 40
        val dataChunkSize = (bytes[40].toInt() and 0xFF) or
            ((bytes[41].toInt() and 0xFF) shl 8) or
            ((bytes[42].toInt() and 0xFF) shl 16) or
            ((bytes[43].toInt() and 0xFF) shl 24)
        assertEquals(pcm.size * 2, dataChunkSize)
        file.delete()
    }
}
