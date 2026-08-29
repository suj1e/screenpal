package com.suj1e.screenpal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * StepFun-only 收敛契约（2026-08-29-stepfun-only）：源码中不允许再出现豆包实现类、
 * 服务商路由层、火山侧与方舟侧设置的任何引用。token 以字符串拼接构造，
 * 避免本测试文件自匹配。覆盖范围按任务推进：任务 1 断言 main 源码无已删除
 * 服务商层残留；任务 2 追加火山/方舟键；任务 3 扩展到 test 源码。
 */
class StepfunOnlySourceTest {

    private val bannedTokens = listOf(
        "Doub" + "ao",
        "Vendor" + "Router"
    )

    @Test
    fun mainSources_containNoDeletedVendorLayerResidue() {
        val offenders = bannedFiles("src/main/java")
        assertTrue(
            "main 源码仍有已删除服务商层的残留：$offenders",
            offenders.isEmpty()
        )
    }

    private fun bannedFiles(root: String): List<String> =
        File(root).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                bannedTokens.filter { text.contains(it) }.map { "${file.path} -> $it" }
            }
            .toList()
}
