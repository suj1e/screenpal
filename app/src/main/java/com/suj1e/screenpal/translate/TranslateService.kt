package com.suj1e.screenpal.translate

/** 转译策略接口（本期 StepFun 实现；将来加第二家只需新增实现类）。 */
interface TranslateService {
    suspend fun translate(text: String): String
}
