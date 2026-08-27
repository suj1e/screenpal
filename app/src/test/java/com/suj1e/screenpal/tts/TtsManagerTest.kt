package com.suj1e.screenpal.tts

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
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
        cloudProvider: GoogleCloudTtsProvider? = null,
        engineType: TtsEngineType = TtsEngineType.PIPER
    ): Pair<TtsManager, FakeTtsEngine> {
        val systemFake = FakeTtsEngine()
        val manager = TtsManager(
            context = mockk(relaxed = true),
            piperEngine = piper,
            cloudProviderFactory = { cloudProvider },
            cloudPlayer = CloudAudioPlayer(),
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
 * Contract tests for TtsManager.initialize(): exactly one engine init attempt per
 * warm-up call (repeat-guard lives in PiperTtsEngine.isInitialized), and failures
 * are swallowed (fail-safe; only logged, degradation happens at speak time).
 */
class TtsManagerInitializeContractTest {

    private fun buildManager(piper: TtsEngine): TtsManager = TtsManager(
        context = mockk(relaxed = true),
        piperEngine = piper,
        settingsProvider = { TtsConfig(TtsEngineType.PIPER, 1.0f, 1.0f) }
    )

    @Test
    fun initialize_piperAlreadyInitialized_engineInitializeCalledExactlyOnce() =
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
