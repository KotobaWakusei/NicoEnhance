# NicoEnhance

Nico增强模块 —— 基于 LSPosed 的 niconico Android 客户端增强模块。

## 功能

- **界面汉化** — 将 niconico 日文 UI 翻译为简体中文（2267+ 字符串 / 1566+ 精确文本 / 219+ 短语）
- **广告移除** — 移除应用内广告、Banner 广告、视频广告
- **WebView 翻译** — 翻译版权页面和贡献者渲染器内容
- **高级会员解锁** — 解锁 niconico 高级会员功能
- **Compose 文本翻译** — 翻译 Jetpack Compose 动态文本
- **Preference 翻译** — 翻译设置页面 Preference 标题/摘要
- **运行时配置** — 可在 niconico 设置页面中开关各项功能

## 要求

- Android 10 (API 29) 或更高
- LSPosed v1.9+ / LSPosed v2.x
- 已 Root 设备 (KernelSU / Magisk / APatch)

## 安装

1. 在 LSPosed 管理器中启用 NicoEnhance 模块
2. 勾选目标应用 `jp.nicovideo.android`
3. 强制停止并重新打开 niconico
4. 在 niconico 设置页面中可找到 NicoEnhance 配置入口

## 下载

[Releases](https://github.com/KotobaWakusei/NicoEnhance/releases)

## 构建

```bash
./gradlew assembleRelease
```

构建产物位于 `app/build/outputs/apk/release/`。

## 致谢

- 本项目的功能参考自 [NAuxiliary](https://github.com/chorusfruit-233/NAuxiliary)
- 翻译资源基于 niconico Android 客户端提取

## License

GNU General Public License v3.0
