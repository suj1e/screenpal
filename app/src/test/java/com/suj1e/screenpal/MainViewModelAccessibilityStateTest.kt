package com.suj1e.screenpal

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suj1e.screenpal.util.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * MainViewModel 权限徽章必须覆盖第三行「无障碍权限（免弹窗截屏）」
 * (2026-08-29-permission-tri-card)：refreshPermissions 以系统实时回显的
 * 「已启用无障碍服务列表」为准同步 MainUiState.accessibilityEnabled，
 * 且 DataStore 设置回灌不得冲掉该运行时徽章（服务被杀后 onResume 刷新回到未开启态）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelAccessibilityStateTest {

    private val app = RuntimeEnvironment.getApplication()

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    private val shadowManager: AccessibilityManager =
        app.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    /** 系统 getEnabledAccessibilityServiceList 回显的真实形态：resolveInfo.serviceInfo 指向组件。 */
    private fun ourServiceInfo(): AccessibilityServiceInfo =
        AccessibilityServiceInfo().apply {
            ReflectionHelpers.setField(
                this,
                "mResolveInfo",
                ResolveInfo().apply {
                    serviceInfo = ServiceInfo().apply {
                        packageName = app.packageName
                        name = "com.suj1e.screenpal.service.ScreenPalAccessibilityService"
                    }
                }
            )
        }

    private fun newViewModel(repository: SettingsRepository): MainViewModel =
        ViewModelProvider(
            viewModelStore,
            viewModelFactory {
                initializer {
                    MainViewModel(repository, mainDispatcher = UnconfinedTestDispatcher())
                }
            }
        )[MainViewModel::class.java]

    @Test
    fun initialState_accessibilityDisabled_byDefault() {
        val viewModel = newViewModel(SettingsRepository(app))
        assertFalse(viewModel.uiState.value.accessibilityEnabled)
    }

    @Test
    fun refreshPermissions_marksAccessibilityEnabled_whenOurServiceEnabled() {
        shadowOf(shadowManager).setEnabledAccessibilityServiceList(listOf(ourServiceInfo()))
        val viewModel = newViewModel(SettingsRepository(app))

        viewModel.refreshPermissions(app)

        assertTrue(viewModel.uiState.value.accessibilityEnabled)
    }

    @Test
    fun refreshPermissions_marksAccessibilityDisabled_whenEnabledListEmpty() {
        // 服务被杀 / 用户在系统设置关闭后：回显列表为空 → 徽章回到未开启态。
        shadowOf(shadowManager).setEnabledAccessibilityServiceList(emptyList())
        val viewModel = newViewModel(SettingsRepository(app))

        viewModel.refreshPermissions(app)

        assertFalse(viewModel.uiState.value.accessibilityEnabled)
    }

    @Test
    fun refreshPermissions_flipsBackToDisabled_afterServiceKilled() {
        // 边界：先开启 → 服务被杀后再次 onResume 刷新应回到未开启态。
        val viewModel = newViewModel(SettingsRepository(app))
        shadowOf(shadowManager).setEnabledAccessibilityServiceList(listOf(ourServiceInfo()))
        viewModel.refreshPermissions(app)
        assertTrue(viewModel.uiState.value.accessibilityEnabled)

        shadowOf(shadowManager).setEnabledAccessibilityServiceList(emptyList())
        viewModel.refreshPermissions(app)

        assertFalse(viewModel.uiState.value.accessibilityEnabled)
    }

    @Test
    fun settingsCollectorUpdate_preservesAccessibilityBadge() {
        // DataStore 回灌路径（settings collector / update 合并）不得冲掉运行时徽章。
        shadowOf(shadowManager).setEnabledAccessibilityServiceList(listOf(ourServiceInfo()))
        val repository = SettingsRepository(app)
        val viewModel = newViewModel(repository)
        viewModel.refreshPermissions(app)
        assertTrue(viewModel.uiState.value.accessibilityEnabled)

        viewModel.setTtsRate(1.5f)
        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.uiState.value.ttsRate != 1.5f && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }

        org.junit.Assert.assertEquals(1.5f, viewModel.uiState.value.ttsRate, 0.001f)
        assertTrue(viewModel.uiState.value.accessibilityEnabled)

        // Self-clean: the DataStore file is shared across tests.
        runBlocking { repository.update { copy(ttsRate = 1.0f) } }
        runBlocking { repository.userSettings.first() }
    }
}
