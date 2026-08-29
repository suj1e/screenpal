package com.suj1e.screenpal.overlay

/**
 * 框选方式（2026-08-29-selection-mode）：随手画套索（默认，升级用户行为不变）
 * 或长方形拖拽。持久化为 DataStore String（键 selectionMode），解析失败一律
 * 回退 LASSO，绝不因脏数据崩溃。
 */
enum class SelectionMode(val storageValue: String) {
    LASSO("LASSO"),
    RECT("RECT");

    companion object {
        /** 大小写不敏感解析；null/空白/未知值一律回退 LASSO，不 trim 脏数据。 */
        fun fromStorageValue(value: String?): SelectionMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: LASSO
    }
}
