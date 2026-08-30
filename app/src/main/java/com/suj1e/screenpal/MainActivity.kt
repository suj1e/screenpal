package com.suj1e.screenpal

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suj1e.screenpal.ui.theme.ScreenPalTheme
import com.suj1e.screenpal.util.AccessibilityHelper
import com.suj1e.screenpal.util.PermissionHelper

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                MainViewModel((application as ScreenPalApplication).settingsRepository)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreenPalTheme {
                MainScreen(
                    viewModel = viewModel,
                    onRequestOverlayPermission = { PermissionHelper.requestOverlayPermission(this) },
                    onRequestNotificationPermission = {
                        PermissionHelper.requestNotificationPermission(this)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PermissionCard(
                overlayGranted = state.overlayPermissionGranted,
                notificationGranted = state.notificationPermissionGranted,
                accessibilityEnabled = state.accessibilityEnabled,
                onRequestOverlay = onRequestOverlayPermission,
                onRequestNotification = onRequestNotificationPermission,
                onRequestAccessibility = {
                    // 免弹窗截屏主路径：直跳系统无障碍设置（新任务栈，Robolectric/非 Activity 均可拉起）。
                    context.startActivity(
                        AccessibilityHelper.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )

            ToggleCard(
                title = "悬浮窗",
                description = if (state.floatingWindowEnabled) "悬浮窗运行中，可在任意应用上点击悬浮球" else "退出 App 后在桌面和其他应用顶部显示悬浮球",
                checked = state.floatingWindowEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (!viewModel.startFloatingWindow(context)) {
                            Toast.makeText(context, "请先授予所需权限", Toast.LENGTH_SHORT).show()
                            onRequestOverlayPermission()
                        }
                    } else {
                        viewModel.stopFloatingWindow(context)
                    }
                }
            )

            Button(
                onClick = {
                    if (state.floatingWindowEnabled) {
                        viewModel.stopFloatingWindow(context)
                    } else {
                        if (!viewModel.startFloatingWindow(context)) {
                            Toast.makeText(context, "请先授予所需权限", Toast.LENGTH_SHORT).show()
                            onRequestOverlayPermission()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.floatingWindowEnabled) "停止悬浮窗" else "启动悬浮窗")
            }

            StepfunCloudSettingsCard(
                stepfunApiKey = state.stepfunApiKey,
                stepfunVoice = state.stepfunVoice,
                onStepfunApiKeyChange = viewModel::setStepfunApiKey,
                onStepfunVoiceChange = viewModel::setStepfunVoice
            )

            SelectionModeCard(
                mode = state.selectionMode,
                onModeChange = viewModel::setSelectionMode
            )

            TtsSettingsCard(
                engine = state.ttsEngine,
                rate = state.ttsRate,
                pitch = state.ttsPitch,
                onEngineChange = viewModel::setTtsEngine,
                onRateChange = viewModel::setTtsRate,
                onPitchChange = viewModel::setTtsPitch
            )

            OcrSettingsCard(
                mode = state.ocrMode,
                onModeChange = viewModel::setOcrMode
            )

            ToggleCard(
                title = "中文播报",
                description = "仅作用于翻译朗读模式；讲解模式总是走 AI。" +
                    if (state.translationEnabled) "外文经 StepFun 转译为简体中文播报" else "已关闭：识别到什么语言就读什么语言",
                checked = state.translationEnabled,
                onCheckedChange = viewModel::setTranslationEnabled
            )

            BroadcastModeCard(
                mode = state.broadcastMode,
                onModeChange = viewModel::setBroadcastMode
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 权限卡片三行（2026-08-29-permission-tri-card）：悬浮窗 / 通知（现状不变）+
 * 无障碍权限（免弹窗截屏）。无障碍行状态实时读系统回显（MainUiState.accessibilityEnabled，
 * onResume 刷新）；行说明常显，讲清与 MediaProjection 录制弹窗的关系。
 */
@Composable
fun PermissionCard(
    overlayGranted: Boolean,
    notificationGranted: Boolean,
    accessibilityEnabled: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestAccessibility: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("权限状态", style = MaterialTheme.typography.titleMedium)
            PermissionRow("悬浮窗权限", overlayGranted, onGrant = onRequestOverlay)
            if (overlayGranted) {
                Text(
                    "小米系请在弹出的权限编辑页同时开启「后台弹出界面」，否则其他应用上点球无法打开框选页",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            PermissionRow("通知权限", notificationGranted, onGrant = onRequestNotification)
            AccessibilityPermissionRow(
                enabled = accessibilityEnabled,
                onEnable = onRequestAccessibility
            )
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        if (granted) {
            Text("已授权", color = Color(0xFF2E7D32))
        } else {
            TextButton(onClick = onGrant) { Text("去授权") }
        }
    }
}

/**
 * 第三行「无障碍权限（免弹窗截屏）」：已开启 → 绿色 ✓ 文本（无按钮）；
 * 未开启 → 「去开启」深链系统无障碍设置；行说明常显（含 Android 10 及以下回退说明）。
 */
@Composable
private fun AccessibilityPermissionRow(enabled: Boolean, onEnable: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("无障碍权限（免弹窗截屏）", modifier = Modifier.weight(1f))
            if (enabled) {
                Text("已开启 ✓", color = Color(0xFF2E7D32))
            } else {
                TextButton(onClick = onEnable) { Text("去开启") }
            }
        }
        Text(
            "开启后点悬浮球零弹窗识读（Android 10 及以下回退系统录制弹窗）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun TtsSettingsCard(
    engine: String,
    rate: Float,
    pitch: Float,
    onEngineChange: (String) -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("语音播报设置", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            EngineOptionRow("CLOUD", "在线语音（StepFun，凭据在「StepFun 云服务」）", engine, onEngineChange)
            EngineOptionRow("PIPER", "Piper 离线（无网/无凭据兜底）", engine, onEngineChange)
            EngineOptionRow("SYSTEM", "系统 TTS（兜底）", engine, onEngineChange)

            Text("语速：%.1fx".format(rate))
            Slider(value = rate, onValueChange = onRateChange, valueRange = 0.5f..2.0f, steps = 5)
            Text("音调：%.1f".format(pitch))
            Slider(value = pitch, onValueChange = onPitchChange, valueRange = 0.5f..2.0f, steps = 5)
        }
    }
}

@Composable
private fun EngineOptionRow(value: String, label: String, selected: String, onSelect: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected.equals(value, true), onClick = { onSelect(value) })
        Text(label)
    }
}

/**
 * 「StepFun 云服务」卡：在线能力的唯一凭据入口。一把 API Key 包办三项在线能力
 * （在线 TTS / 云 OCR 增强 / AI 转译）；API Key 与音色两项常显。
 */
@Composable
fun StepfunCloudSettingsCard(
    stepfunApiKey: String,
    stepfunVoice: String,
    onStepfunApiKeyChange: (String) -> Unit,
    onStepfunVoiceChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("StepFun 云服务", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            Text(
                "一把 API Key 包办：在线语音播报 · 云 OCR 增强 · AI 转译",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = stepfunApiKey,
                onValueChange = onStepfunApiKeyChange,
                label = { Text("StepFun API Key（TTS + 视觉 OCR + 转译共用）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = stepfunVoice,
                onValueChange = onStepfunVoiceChange,
                label = { Text("音色（voice，默认 tianmeinvsheng）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

/**
 * 「框选方式」卡（2026-08-29-selection-mode）：随手画套索 / 长方形拖拽两单选，
 * 默认随手画（升级用户行为不变）。持久化为 DataStore selectionMode，
 * 截图框选页按此构造 SelectionView。
 */
@Composable
fun SelectionModeCard(
    mode: String,
    onModeChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("框选方式", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            Text(
                "选择屏幕文字的框选手势",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            EngineOptionRow("LASSO", "随手画（圈出任意形状，默认）", mode, onModeChange)
            EngineOptionRow("RECT", "长方形（拖拽框选矩形）", mode, onModeChange)
        }
    }
}

/**
 * 「播报模式」卡（2026-08-29-broadcast-mode）：翻译朗读（外文转中文原样朗读，
 * 默认）/ AI 讲解（问 AI 这是什么）两单选，插在「中文播报」卡之后。翻译模式
 * 受「中文播报」开关控制；讲解模式总是走 AI（不受开关限制，显式选择即意图）。
 * 持久化为 DataStore broadcastMode，截图框选页按此分发播报管道。
 */
@Composable
fun BroadcastModeCard(
    mode: String,
    onModeChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("播报模式", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            Text(
                "翻译模式受「中文播报」开关控制；讲解模式总是走 AI",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            EngineOptionRow("TRANSLATE", "翻译朗读：外文转中文原样朗读（默认）", mode, onModeChange)
            EngineOptionRow("EXPLAIN", "AI 讲解：问 AI 这是什么", mode, onModeChange)
        }
    }
}

@Composable
fun OcrSettingsCard(
    mode: String,
    onModeChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OCR 设置", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            EngineOptionRow("LOCAL", "仅端侧 PP-OCR（离线）", mode, onModeChange)
            EngineOptionRow("CLOUD", "仅云端 StepFun 视觉（凭据在「StepFun 云服务」）", mode, onModeChange)
            EngineOptionRow("HYBRID", "混合模式：端侧优先，低置信度走云端 StepFun", mode, onModeChange)
        }
    }
}
