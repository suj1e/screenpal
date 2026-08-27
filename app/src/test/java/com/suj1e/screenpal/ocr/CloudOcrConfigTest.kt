package com.suj1e.screenpal.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CloudOcrConfig 契约：收敛为单字段 arkApiKey（火山方舟 API Key，云 OCR 与 AI 转译共用）。
 */
class CloudOcrConfigTest {

    @Test
    fun cloudOcrConfig_hasSingleArkApiKeyField() {
        val fields = CloudOcrConfig::class.java.declaredFields
            .filter { !it.isSynthetic && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .sorted()
        assertEquals(listOf("arkApiKey"), fields)
    }

    @Test
    fun cloudOcrConfig_construction_keepsKey() {
        val config = CloudOcrConfig("ark-key-1")
        assertEquals("ark-key-1", config.arkApiKey)
    }
}
