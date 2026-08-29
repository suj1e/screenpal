package com.suj1e.screenpal.translate

/**
 * 播报模式（2026-08-29-broadcast-mode）：翻译朗读（外文转中文原样朗读，默认，
 * 受「中文播报」开关控制）或 AI 讲解（问 AI 这是什么，总是走 AI，跳过中文
 * 启发式）。持久化为 DataStore String（键 broadcastMode），解析失败一律
 * 回退 TRANSLATE，绝不因脏数据崩溃（与 SelectionMode 同款防御）。
 */
enum class BroadcastMode(val storageValue: String) {
    TRANSLATE("TRANSLATE"),
    EXPLAIN("EXPLAIN");

    companion object {
        /** 大小写不敏感解析；null/空白/未知值一律回退 TRANSLATE，不 trim 脏数据。 */
        fun fromStorageValue(value: String?): BroadcastMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: TRANSLATE
    }
}
