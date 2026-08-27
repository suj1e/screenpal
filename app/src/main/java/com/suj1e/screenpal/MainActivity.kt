package com.suj1e.screenpal

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
                onRequestOverlay = onRequestOverlayPermission,
                onRequestNotification = onRequestNotificationPermission
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
                apiKey = state.cloudApiKey,
                onModeChange = viewModel::setOcrMode,
                onApiKeyChange = viewModel::setCloudApiKey
            )

            ToggleCard(
                title = "中文播报",
                description = if (state.translationEnabled) "外文自动转译为简体中文播报（火山方舟）" else "已关闭：识别到什么语言就读什么语言",
                checked = state.translationEnabled,
                onCheckedChange = viewModel::setTranslationEnabled
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PermissionCard(
    overlayGranted: Boolean,
    notificationGranted: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("权限状态", style = MaterialTheme.typography.titleMedium)
            PermissionRow("悬浮窗权限", overlayGranted, onGrant = onRequestOverlay)
            PermissionRow("通知权限", notificationGranted, onGrant = onRequestNotification)
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

            EngineOptionRow("PIPER", "Piper 离线（推荐，需下载模型）", engine, onEngineChange)
            EngineOptionRow("CLOUD", "云端 Google Cloud TTS（需 API Key）", engine, onEngineChange)
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

@Composable
fun OcrSettingsCard(
    mode: String,
    apiKey: String,
    onModeChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OCR 设置", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            EngineOptionRow("LOCAL", "仅端侧 PP-OCR（离线）", mode, onModeChange)
            EngineOptionRow("CLOUD", "仅云端 Vision API（需 API Key）", mode, onModeChange)
            EngineOptionRow("HYBRID", "混合模式：端侧优先，低置信度走云端", mode, onModeChange)

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("火山方舟 API Key（云 OCR + 转译共用）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}
