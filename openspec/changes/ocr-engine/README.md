# ocr-engine

实现 OCR 统一接口 OcrEngine，包含 MlKitOcrProvider（端侧 ML Kit 文字识别，默认优先）、CloudOcrProvider（云端 API 封装，可配置 endpoint 和 key）、混合识别策略（端侧为主，云端增强），以及根据用户配置自动切换识别模式
