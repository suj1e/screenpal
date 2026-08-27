package com.suj1e.screenpal.translate

/** 转译失败（无 Key / 网络错误 / HTTP 错误码 / 空、畸形响应）。 */
class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)
