package com.suj1e.screenpal.tts

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsEngineTypeTest {

    @Test
    fun from_validStrings_parse() {
        assertEquals(TtsEngineType.PIPER, TtsEngineType.from("PIPER"))
        assertEquals(TtsEngineType.CLOUD, TtsEngineType.from("cloud"))
        assertEquals(TtsEngineType.SYSTEM, TtsEngineType.from("System"))
    }

    @Test
    fun from_nullOrUnknown_defaultsToPiper() {
        assertEquals(TtsEngineType.PIPER, TtsEngineType.from(null))
        assertEquals(TtsEngineType.PIPER, TtsEngineType.from("unknown"))
        assertEquals(TtsEngineType.PIPER, TtsEngineType.from(""))
    }
}

class FakeTtsEngine(
    private val failOnSpeak: Boolean = false,
    private val failOnInit: Boolean = false
) : TtsEngine {
    val spokenTexts = mutableListOf<String>()
    var stopped = false
        private set
    var shutDown = false
        private set

    override val isInitialized: Boolean = true

    override suspend fun initialize() {
        if (failOnInit) throw TtsException("fake init failure")
    }

    override suspend fun speak(text: String, rate: Float, pitch: Float) {
        if (failOnSpeak) throw TtsException("fake speak failure")
        spokenTexts.add(text)
    }

    override fun stop() {
        stopped = true
    }

    override fun shutdown() {
        shutDown = true
    }
}

class TtsManagerFallbackTest {

    private fun buildManager(
        piper: FakeTtsEngine,
        cloudEngine: TtsEngine? = null,
        engineType: TtsEngineType = TtsEngineType.PIPER
    ): Pair<TtsManager, FakeTtsEngine> {
        val systemFake = FakeTtsEngine()
        val manager = TtsManager(
            context = mockk(relaxed = true),
            piperEngine = piper,
            cloudProviderFactory = { cloudEngine },
            systemEngineProvider = { systemFake },
            settingsProvider = { TtsConfig(engineType, 1.0f, 1.0f) }
        )
        return manager to systemFake
    }

    @Test
    fun speak_blankText_doesNotCallAnyEngine() = kotlinx.coroutines.runBlocking {
        val piper = FakeTtsEngine()
        val manager = buildManager(piper).first

        manager.speak("")

        assertTrue(piper.spokenTexts.isEmpty())
    }

    @Test
    fun piperSuccess_usesPiperOnly() = kotlinx.coroutines.runBlocking {
        val piper = FakeTtsEngine()
        val (manager, systemFake) = buildManager(piper)

        manager.speak("你好世界")

        assertEquals(listOf("你好世界"), piper.spokenTexts)
        assertTrue(systemFake.spokenTexts.isEmpty())
    }

    @Test
    fun piperFails_degradesToSystem() = kotlinx.coroutines.runBlocking {
        val piper = FakeTtsEngine(failOnSpeak = true)
        val (manager, systemFake) = buildManager(piper)

        manager.speak("测试降级")

        assertTrue(piper.spokenTexts.isEmpty())
        assertEquals(listOf("测试降级"), systemFake.spokenTexts)
    }

    @Test
    fun stop_notifiesEngines() {
        val piper = FakeTtsEngine()
        val (manager, systemFake) = buildManager(piper)

        manager.stop()

        assertTrue(piper.stopped)
        assertTrue(systemFake.stopped)
    }
}

/**
 * Fallback matrix after Doubao (火山引擎) became the CLOUD engine
 * (2026-08-27-tts-domestic-online):
 *   CLOUD selected: Doubao -> Piper -> System (missing credentials = null factory
 *   drops straight to Piper); PIPER selected: Piper -> Doubao -> System.
 */
class TtsManagerCloudFallbackTest {

    private fun buildManager(
        piper: FakeTtsEngine,
        cloudEngine: TtsEngine?,
        engineType: TtsEngineType
    ): Triple<TtsManager, FakeTtsEngine, TtsEngine?> {
        val systemFake = FakeTtsEngine()
        val manager = TtsManager(
            context = mockk(relaxed = true),
            piperEngine = piper,
            cloudProviderFactory = { cloudEngine },
            systemEngineProvider = { systemFake },
            settingsProvider = { TtsConfig(engineType, 1.0f, 1.0f) }
        )
        return Triple(manager, systemFake, cloudEngine)
    }

    @Test
    fun cloudSelected_cloudSpeakFails_degradesToPiper() = kotlinx.coroutines.runBlocking {
        val piper = FakeTtsEngine()
        val (manager, systemFake, _) = buildManager(
            piper,
            FakeTtsEngine(failOnSpeak = true),
            TtsEngineType.CLOUD
        )

        manager.speak("豆包失败降级")

        assertEquals(listOf("豆包失败降级"), piper.spokenTexts)
        assertTrue(systemFake.spokenTexts.isEmpty())
    }

    @Test
    fun cloudSelected_cloudAndPiperBothFail_degradesToSystem() = kotlinx.coroutines.runBlocking {
        val piper = FakeTtsEngine(failOnSpeak = true)
        val (manager, systemFake, _) = buildManager(
            piper,
            FakeTtsEngine(failOnSpeak = true),
            TtsEngineType.CLOUD
        )

        manager.speak("两级降级")

        assertEquals(listOf("两级降级"), systemFake.spokenTexts)
        assertTrue(piper.spokenTexts.isEmpty())
    }

    @Test
    fun cloudSelected_factoryReturnsNull_goesStraightToPiper() = kotlinx.coroutines.runBlocking {
        // Missing Volcano credentials -> factory yields null -> Piper speaks directly.
        val piper = FakeTtsEngine()
        val (manager, systemFake, _) = buildManager(piper, null, TtsEngineType.CLOUD)

        manager.speak("无凭据直落 Piper")

        assertEquals(listOf("无凭据直落 Piper"), piper.spokenTexts)
        assertTrue(systemFake.spokenTexts.isEmpty())
    }

    @Test
    fun piperSelected_piperFails_cloudEngineSucceeds_usesCloud() = kotlinx.coroutines.runBlocking {
        val piper = FakeTtsEngine(failOnSpeak = true)
        val cloud = FakeTtsEngine()
        val (manager, systemFake, _) = buildManager(piper, cloud, TtsEngineType.PIPER)

        manager.speak("Piper 失败走豆包")

        assertEquals(listOf("Piper 失败走豆包"), cloud.spokenTexts)
        assertTrue(piper.spokenTexts.isEmpty())
        assertTrue(systemFake.spokenTexts.isEmpty())
    }

    @Test
    fun stop_notifiesActiveCloudEngine() = kotlinx.coroutines.runBlocking {
        val piper = FakeTtsEngine()
        val cloud = FakeTtsEngine()
        val (manager, _, _) = buildManager(piper, cloud, TtsEngineType.CLOUD)

        manager.speak("播报中")
        assertFalse(cloud.stopped)
        manager.stop()

        assertTrue(cloud.stopped)
    }
}

/**
 * Contract tests for TtsManager.initialize(): exactly one engine init attempt per
 * warm-up call (repeat-guard lives in PiperTtsEngine.isInitialized), and failures
 * are swallowed (fail-safe; only logged, degradation happens at speak time).
 */
/**
 * NOTE on naming: TtsManager.initialize() is an unconditional single delegation
 * to the engine (TtsManager.kt) — real idempotency lives in PiperTtsEngine's
 * `if (isInitialized) return` early exit, which currently has no unit test and
 * is instead covered by the task-4 emulator acceptance (model lands once).
 * These tests therefore pin "exactly-once delegation per call" + fail-safety.
 */
class TtsManagerInitializeContractTest {

    private fun buildManager(piper: TtsEngine): TtsManager = TtsManager(
        context = mockk(relaxed = true),
        piperEngine = piper,
        settingsProvider = { TtsConfig(TtsEngineType.PIPER, 1.0f, 1.0f) }
    )

    @Test
    fun initialize_delegatesToEngineExactlyOncePerCall() =
        kotlinx.coroutines.runBlocking {
            val piper = mockk<TtsEngine>(relaxed = true)
            every { piper.isInitialized } returns true
            val manager = buildManager(piper)

            manager.initialize()

            coVerify(exactly = 1) { piper.initialize() }
        }

    @Test
    fun initialize_piperInitThrows_doesNotPropagate() = kotlinx.coroutines.runBlocking {
        val piper = mockk<TtsEngine>(relaxed = true)
        coEvery { piper.initialize() } throws RuntimeException("piper init boom")
        val manager = buildManager(piper)

        // Fail-safe: must not throw outward; PiperTtsEngine marks itself FAILED.
        manager.initialize()

        coVerify(exactly = 1) { piper.initialize() }
    }
}

class ModelDownloaderConfigUrlContractTest {
    @Test
    fun configUrl_targetsUpstreamOnnxJsonName() {
        // Regression: upstream config is "<model>.onnx.json"; the old URL
        // ("<model>.json") 404s and Piper initialization always failed.
        val url = com.suj1e.screenpal.tts.ModelDownloader.configUrl()
        assertTrue("config URL must point to the upstream .onnx.json name", url.endsWith("zh_CN-huayan-medium.onnx.json"))
    }
}
