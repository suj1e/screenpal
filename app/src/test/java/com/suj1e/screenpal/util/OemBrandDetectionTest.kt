package com.suj1e.screenpal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OemBrandDetectionTest {

    @Test
    fun xiaomiVariants_mapToXiaomi() {
        assertEquals("XIAOMI", PermissionHelper.oemBrandFor("Xiaomi"))
        assertEquals("XIAOMI", PermissionHelper.oemBrandFor("redmi"))
    }

    @Test
    fun huaweiVariants_mapToHuawei() {
        assertEquals("HUAWEI", PermissionHelper.oemBrandFor("HUAWEI"))
        assertEquals("HUAWEI", PermissionHelper.oemBrandFor("HONOR"))
    }

    @Test
    fun oppoAndVivo_map() {
        assertEquals("OPPO", PermissionHelper.oemBrandFor("oppo"))
        assertEquals("VIVO", PermissionHelper.oemBrandFor("vivo"))
        assertEquals("VIVO", PermissionHelper.oemBrandFor("iQOO"))
    }

    @Test
    fun unknownOrBlank_returnsNull() {
        assertNull(PermissionHelper.oemBrandFor("Google"))
        assertNull(PermissionHelper.oemBrandFor(""))
        assertNull(PermissionHelper.oemBrandFor(null))
    }
}
