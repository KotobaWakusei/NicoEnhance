# NicoEnhance

[![Build](https://github.com/KotobaWakusei/NicoEnhance/actions/workflows/build.yml/badge.svg)](https://github.com/KotobaWakusei/NicoEnhance/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/KotobaWakusei/NicoEnhance)](https://github.com/KotobaWakusei/NicoEnhance/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-29%2B-brightgreen.svg)](https://developer.android.com/studio/releases/platforms)

基于 LSPosed 的 niconico Android 客户端增强模块。

## 功能

- **界面汉化** — 将 niconico 日文 UI 整句翻译为简体中文（2290+ 资源键 / 1560+ 整句文本，不做词语替换）
- **广告移除** — 移除应用内广告、Banner 广告、视频广告
- **WebView 翻译** — 翻译版权页面和贡献者渲染器内容
- **高级会员解锁** — 解锁 niconico 高级会员功能
- **Compose 文本翻译** — 翻译 Jetpack Compose 动态文本
- **Preference 翻译** — 翻译设置页面 Preference 标题/摘要
- **运行时配置** — 可在 niconico 设置页面中开关各项功能

## 要求

| 项 | 要求 |
| --- | --- |
| Android | 10 (API 29) 或更高 |
| LSPosed | v1.9+ / v2.x |
| 设备 | 已 Root（KernelSU / Magisk / APatch） |

## 安装

1. 在 LSPosed 管理器中启用 NicoEnhance 模块
2. 勾选目标应用 `jp.nicovideo.android`
3. 强制停止并重新打开 niconico
4. 在 niconico 设置页面中可找到 NicoEnhance 配置入口

## 下载

[Releases](https://github.com/KotobaWakusei/NicoEnhance/releases)

## 构建

```bash
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 发布包（需签名环境变量）
```

构建产物位于 `app/build/outputs/apk/`。签名信息通过环境变量注入：

| 环境变量 | 说明 |
| --- | --- |
| `KS_STORE_PASSWORD` | keystore 密码 |
| `KS_KEY_ALIAS` | 密钥别名 |
| `KS_KEY_PASSWORD` | 密钥密码 |

## 目录结构

```
app/src/main/
├── assets/translations/zh-CN/   # 翻译资源（strings 资源键 / exact 整句）
├── java/io/github/nicoenhance/  # 模块源码
└── resources/META-INF/xposed/   # LSPosed 模块元数据
```

## 致谢

- 本项目的功能参考自 [NAuxiliary](https://github.com/chorusfruit-233/NAuxiliary)
- 翻译资源基于 niconico Android 客户端提取

## 贡献

欢迎提交 Issue 与 PR，请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 与 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。

## License

[GNU General Public License v3.0](LICENSE)
