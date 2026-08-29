package com.suj1e.screenpal.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SelectionMode enum contract (2026-08-29-selection-mode): LASSO is the
 * default (back-compat with the existing lasso-only behavior), storage values
 * round-trip through the DataStore String key, and unknown/blank values
 * degrade to LASSO instead of crashing.
 */
class SelectionModeTest {

    @Test
    fun storageValues_areLassoAndRect() {
        assertEquals("LASSO", SelectionMode.LASSO.storageValue)
        assertEquals("RECT", SelectionMode.RECT.storageValue)
    }

    @Test
    fun fromStorageValue_parsesExactValues() {
        assertEquals(SelectionMode.LASSO, SelectionMode.fromStorageValue("LASSO"))
        assertEquals(SelectionMode.RECT, SelectionMode.fromStorageValue("RECT"))
    }

    @Test
    fun fromStorageValue_isCaseInsensitive() {
        assertEquals(SelectionMode.RECT, SelectionMode.fromStorageValue("rect"))
        assertEquals(SelectionMode.LASSO, SelectionMode.fromStorageValue("lasso"))
    }

    @Test
    fun fromStorageValue_nullOrUnknown_degradesToLassoDefault() {
        assertEquals(SelectionMode.LASSO, SelectionMode.fromStorageValue(null))
        assertEquals(SelectionMode.LASSO, SelectionMode.fromStorageValue(""))
        assertEquals(SelectionMode.LASSO, SelectionMode.fromStorageValue("FREEFORM"))
        assertEquals(SelectionMode.LASSO, SelectionMode.fromStorageValue(" RECT "))
    }
}
