package com.suj1e.screenpal.translate

/** 转译策略接口（本期 StepFun 实现；将来加第二家只需新增实现类）。 */
interface TranslateService {
    suspend fun translate(text: String): String

    /**
     * AI 讲解（2026-08-29-broadcast-mode）：用简体中文口语化解释圈选内容
     * （这是什么、有什么用），不做语言转换。任何失败抛 [TranslationException]。
     */
    suspend fun explain(text: String): String
}
