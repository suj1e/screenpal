package com.suj1e.screenpal.translate

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BroadcastMode contract (2026-08-29-broadcast-mode): two broadcast modes,
 * persisted as DataStore String (key broadcastMode). Dirty / blank / null
 * storage values must all fall back to TRANSLATE so existing installs and
 * corrupted entries never crash the app (same defense as SelectionMode).
 */
class BroadcastModeTest {

    @Test
    fun enum_hasExactlyTwoModes() {
        assertEquals(listOf("TRANSLATE", "EXPLAIN"), BroadcastMode.entries.map { it.name })
    }

    @Test
    fun storageValue_matchesEnumName() {
        assertEquals("TRANSLATE", BroadcastMode.TRANSLATE.storageValue)
        assertEquals("EXPLAIN", BroadcastMode.EXPLAIN.storageValue)
    }

    @Test
    fun fromStorageValue_parsesBothModes_caseInsensitive() {
        assertEquals(BroadcastMode.TRANSLATE, BroadcastMode.fromStorageValue("TRANSLATE"))
        assertEquals(BroadcastMode.EXPLAIN, BroadcastMode.fromStorageValue("EXPLAIN"))
        assertEquals(BroadcastMode.EXPLAIN, BroadcastMode.fromStorageValue("explain"))
        assertEquals(BroadcastMode.TRANSLATE, BroadcastMode.fromStorageValue("Translate"))
    }

    @Test
    fun fromStorageValue_nullOrBlankOrUnknown_fallsBackToTranslate() {
        assertEquals(BroadcastMode.TRANSLATE, BroadcastMode.fromStorageValue(null))
        assertEquals(BroadcastMode.TRANSLATE, BroadcastMode.fromStorageValue(""))
        assertEquals(BroadcastMode.TRANSLATE, BroadcastMode.fromStorageValue("   "))
        assertEquals(BroadcastMode.TRANSLATE, BroadcastMode.fromStorageValue("garbage"))
        assertEquals(BroadcastMode.TRANSLATE, BroadcastMode.fromStorageValue("LASSO"))
    }
}
