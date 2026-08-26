# tts-engine-and-main-interface

实现 PiperTtsEngine（端侧神经语音，ONNX Runtime 推理，模型按需加载）、CloudTtsProvider（云端 TTS 可选）、TtsManager 统一封装（speak/stop/语速/音调），MainActivity + MainViewModel（权限状态展示、悬浮窗开关、TTS/OCR 配置界面），以及端到端联调：悬浮窗点击 -> 框选 -> OCR -> TTS 播报全流程
