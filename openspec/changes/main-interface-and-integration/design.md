# Design: 主界面 + 全流程集成

## 架构概览

```
MainActivity
    │ Compose UI
    ▼
MainViewModel
    │ 管理 UI 状态
    ▼
SettingsRepository（DataStore）
    │ 持久化配置
    ▼
UserSettings（数据模型）

[完整端到端流程]
┌─────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│ FloatingWindow  │ ──▶ │ ScreenCapture    │ ──▶ │ SelectionOverlay  │
│ Service         │     │ Service          │     │ Activity         │
│ (悬浮球点击)     │     │ (截图 → Uri)     │     │ (框选确认 → Rect) │
└─────────────────┘     └──────────────────┘     └────────┬─────────┘
                                                          │
                                                          ▼
                                                 ┌──────────────────┐
                                                 │ OcrEngine        │
                                                 │ (ML Kit / Cloud) │
                                                 └────────┬─────────┘
                                                          │
                                                          ▼
                                                 ┌──────────────────┐
                                                 │ TtsManager       │
                                                 │ (Piper / Cloud / │
                                                 │  System)         │
                                                 └──────────────────┘
```

## MainActivity 设计

### Compose UI 结构
```kotlin
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { HomeTopBar() },
        containerColor = Color(0xFFF8F9FC)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. 权限状态卡片
            PermissionCard(
                permissions = uiState.permissions,
                onRequestPermission = { viewModel.requestPermission(it) }
            )

            // 2. 悬浮窗开关
            ToggleCard(
                title = "启用悬浮窗",
                description = "退出 App 后显示悬浮球",
                checked = uiState.floatingWindowEnabled,
                onCheckedChange = { viewModel.toggleFloatingWindow(it) }
            )

            // 3. TTS 设置
            TtsSettingsCard(
                engine = uiState.ttsEngine,
                rate = uiState.ttsRate,
                pitch = uiState.ttsPitch,
                onEngineChange = { viewModel.setTtsEngine(it) },
                onRateChange = { viewModel.setTtsRate(it) },
                onPitchChange = { viewModel.setTtsPitch(it) }
            )

            // 4. OCR 设置
            OcrSettingsCard(
                mode = uiState.ocrMode,
                cloudApiKey = uiState.cloudApiKey,
                onModeChange = { viewModel.setOcrMode(it) },
                onApiKeyChange = { viewModel.setCloudApiKey(it) }
            )

            // 5. 启动按钮
            Button(
                onClick = { viewModel.startFloatingWindow() },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = uiState.allPermissionsGranted
            ) {
                Text("🚀 启动悬浮窗")
            }
        }
    }
}
```

## MainViewModel 设计

```kotlin
class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val permissionHelper: PermissionHelper,
    private val floatingWindowService: FloatingWindowService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.userSettings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }

        viewModelScope.launch {
            permissionHelper.permissionStatusFlow.collect { permissions ->
                _uiState.update { it.copy(permissions = permissions) }
            }
        }
    }

    fun requestPermission(permission: Permission) { ... }
    fun toggleFloatingWindow(enabled: Boolean) { ... }
    fun startFloatingWindow() { ... }
    fun setTtsEngine(engine: TtsEngineType) { ... }
    fun setOcrMode(mode: OcrMode) { ... }
}
```

## SettingsRepository 设计

```kotlin
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.createDataStore(
        name = "settings",
        schema = UserSettingsSerializer
    )

    val userSettings: Flow<UserSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(UserSettings()) else throw exception
        }

    suspend fun updateSettings(update: UserSettings.() -> UserSettings) {
        dataStore.edit { preferences ->
            val current = preferences.toUserSettings()
            preferences.putAll(update(current).toPreferences())
        }
    }
}
```

## UserSettings 数据模型

```kotlin
data class UserSettings(
    val floatingWindowEnabled: Boolean = false,
    val ttsEngine: TtsEngineType = TtsEngineType.PIPER,
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ocrMode: OcrMode = OcrMode.HYBRID,
    val cloudApiKey: String = ""
)
```

## 端到端流程

```
1. MainActivity 启动
   ├── 检查权限状态
   ├── 加载用户配置
   ├── 初始化 TtsManager（可选，首次使用时）
   └── 展示配置界面

2. 用户点击"启动悬浮窗"
   ├── 验证权限是否就绪
   ├── 保存配置（DataStore）
   ├── 启动 FloatingWindowService
   └── 提示用户"悬浮窗已启动，可返回桌面使用"

3. 用户点击悬浮球
   ├── 隐藏悬浮球
   ├── 触发 ScreenCaptureService 截图
   ├── 截图完成后启动 SelectionOverlayActivity
   └── 展示截图 + Canvas 框选

4. 用户框选确认
   ├── 计算裁剪坐标
   ├── 调用 OcrEngine.recognize(croppedBitmap)
   ├── 识别完成后自动 TtsManager.speak()
   └── 在 SelectionOverlayActivity 底部展示结果

5. 识别结果展示
   ├── 在 SelectionOverlayActivity 底部展示结果
   ├── 用户可操作：播放/暂停/停止、复制文本、重新选择
   └── 播报完成后恢复悬浮球显示
```

## OEM 兼容性

### 常见 OEM 设置入口

| 品牌 | 悬浮窗权限设置 | 自启动/电池优化 |
|------|---------------|----------------|
| 小米 | `miui.intent.action.APP_PERM_EDITOR` | `miui.intent.action.APP_PERM_EDITOR`（同一页面） |
| 华为 | `com.huawei.systemmanager.optimize.process.ProtectActivity` | `com.huawei.systemmanager.optimize.process.ProtectActivity` |
| OPPO | `oppo.intent.action.OPPO_PERMISSION` | `oppo.intent.action.OPPO_PERMISSION` |
| vivo | `permission.intent.action.softdetail` | `permission.intent.action.softdetail` |

### 降级策略

如果悬浮窗权限被系统拒绝：
1. 显示引导页，提供对应品牌的设置入口
2. 用户跳转设置页开启权限后返回
3. 如果仍无法获取权限，提示用户"悬浮窗功能不可用，请手动截图后使用其他 OCR 工具"

## 测试策略

### 测试金字塔

![测试金字塔](../docs/design/test-pyramid.svg)


### 分层策略

| 层级 | 目标 | 工具 | 覆盖率目标 |
|------|------|------|-----------|
| 单元测试 | MainViewModel 状态管理、SettingsRepository 读写 | JUnit 4 + MockK | 85% |
| 集成测试 | Compose UI 交互（点击、滑动） | Compose Test + UI Automator | 70% |
| 手动验证 | 端到端完整流程 | 模拟器/真机 | 100% 流程通过 |

### 测试数据

- 模拟权限状态（全部 granted / 部分 denied）
- 模拟用户配置（各种 TTS/OCR 组合）
- 模拟端到端流程的每个节点

### 边界条件

- 首次启动（无配置）
- 权限全部拒绝
- 悬浮窗服务启动失败
- 网络不可用（云端 TTS/OCR 不可用）
- TTS 引擎全部失败
