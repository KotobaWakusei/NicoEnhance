# 贡献指南

感谢你有兴趣为 NicoEnhance 做贡献！请先阅读本指南以保持仓库整洁与构建可复现。

## 行为准则

请遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。

## 开发流程

1. **Fork 并创建分支**：修复/功能请从 `main` 拉出独立分支。
2. **开发环境**：JDK 17、Android SDK 36（compileSdk）、Gradle 8.9（wrapper 已固定）。
3. **本地构建**：
   ```bash
   ./gradlew assembleDebug    # 调试包（不需要 keystore）
   ./gradlew assembleRelease  # 发布包（需要 app/keystore/nicoenhance.jks）
   ```
   构建签名信息来自环境变量 `KS_STORE_PASSWORD` / `KS_KEY_ALIAS` / `KS_KEY_PASSWORD`，不要提交 keystore。

## 翻译资源

翻译文件位于 `app/src/main/assets/translations/zh-CN/`：

| 文件 | 用途 |
| --- | --- |
| `strings.properties` | 按资源名映射的字符串 |
| `exact.properties`   | 整串精确文本映射 |
| `phrases.properties` | 短语级替换（必须与现有最短前缀短语兼容） |

提交翻译前请确认：

- 键有序（可改进排序但不要造成大量 diff）。
- `@string/xxx` 引用值将被递归解析；新增时应写最终译文而非引用。

## 提交规范

- 提交信息使用 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)：
  `fix:` / `feat:` / `docs:` / `ci:` / `refactor:` 等。
- 一个提交只做一件事，保持 `git log` 线性、可追溯。

## Pull Request

- 目标分支：`main`。
- CI 会在 PR 中跑 `assembleRelease`，请确保通过；改动 hook 逻辑时请自测目标版本。
- 请不要把 `.apk` / `.apks` / keystore / 本地构建产物提交进仓库。