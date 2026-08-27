package com.suj1e.screenpal.translate

/** 转译策略接口（本期豆包实现；预留后续并列服务商路由）。 */
interface TranslateService {
    suspend fun translate(text: String): String
}
