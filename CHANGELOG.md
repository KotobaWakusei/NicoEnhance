# Changelog

本仓库所有值得记录的变更均收录于此，格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Fixed

- 修复短语匹配器：原 Aho-Corasick 实现 `step()` 的 fail 链为死代码，导致左起最短而非最长匹配，
  重叠短语（如 `フォロー新着`）被拆成两个短语翻译；改为 leftmost-longest 贪心扫描。
- 修复 `@string/xxx` 字面量泄漏：`strings.properties` 中 123 条值为 `@string/...` 的翻译条目
  会被原样渲染到界面，现按引用递归解析为实际译文。
- 复数/数组文本翻译兜底：`plurals.`/`array.` 字典段为空时，不再静默放弃，改用短语级翻译回退。
- 广告视图钩子：`getMethod()` 只返回 public 方法，混淆后的 `start`/`a` 方法会静默失效；
  改为遍历 declared 方法（含非 public）。
- 会员解锁：DexKit 全量扫描不再强制所有 boolean getter 为 true（避免误伤 `isEnabled` 等），
  仅对名称含 `premium` 的方法生效；定向 UI 状态类保持原有行为。
- 版本检查：支持无 `v` 前缀的 tag 与语义化比较（`1.10` > `1.9`）。
- 缓存上限：`exactCache` 增加 2048 条上限，长会话不再无界增长。
- 日志噪音：移除逐条 `MISS` WARN 日志；Settings 哨兵写入失败仅告警一次。

## [1.0.3] - 2026-07

### Changed

- 适配 niconico 9.9.0 混淆类名漂移，重构 `ClassNameProvider` 解析策略。
- 更新翻译资源（2267+ 字符串 / 1566+ 精确文本 / 219+ 短语）。

[Unreleased]: https://github.com/KotobaWakusei/NicoEnhance/compare/v1.0.3...HEAD
[1.0.3]: https://github.com/KotobaWakusei/NicoEnhance/releases/tag/NicoEnhance
