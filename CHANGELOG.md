# Changelog

本仓库所有值得记录的变更均收录于此，格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added

- 适配 niconico 9.14.0 混淆类名漂移：广告工厂 `sl.i` → `bm.i`、广告控制器 `tf.l` → `xf.l`、
  Compose 广告 Banner `hk.c` → `qk.c`、设置会员状态 `ep.y1` → `pp.z1`、资源持有类 `mf.l0` → `of.l0`。
- Compose 设置项改为 DexKit 指纹定位（`hp.e0` 在 9.14.0 已无 `設定` 字面量），定位失败时优雅降级到
  Fragment 设置入口；`Function0`/`Unit` 解析优先使用稳定的库名，避免短名漂移后被错误代理。
- 补充 30 条未翻译的日文整句（退出登录限制、媒体分享、邮箱注册状态等）。

### Changed

- **应用包名改为 `io.github.kotobawakusei.nicoenhance`**：满足 LSPosed 模块仓库
  `io.github.<用户名>.<应用>` 的归属校验（原 `io.github.nicoenhance` 被判定为无效包名）。
  Java namespace 保持 `io.github.nicoenhance` 不变；已安装旧包名的用户需卸载后重装。
- **翻译改为整句匹配**：移除短语/词语替换引擎（`phrases.properties` 及前缀树），
  不再把任意日文文本里的词（`設定`/`動画`/`検索`…）逐个替换——那会把用户评论、视频标题、
  简介等拼成中日混杂的垃圾。现在只翻译与词典**整串相等**（含空白归一化）的文本，
  没有整句译文的原文保持原样。

### Performance

- 配置读取节流：`ModuleConfig.refreshThrottled` 让广告 hook 热路径不再每次全量读 SharedPreferences。
- 视图树翻译去重：`translatedSubtrees` 记录已处理的 View，父/子 `onAttachedToWindow` 不再重复遍历同一子树。
- `TranslationRepository.findExactText` 对非日文文本负缓存，避免对同一 UI 文本重复做码点扫描。
- `StringTranslations` 用不可变 `HashMap` 快照替换同步的 `Properties` 查找，Compose 热路径的字典查询不再抢 `Hashtable` 监视器锁。
- `TranslationRepository.translateText` 复用 `findExactText` 的有界缓存；超过 256 字符的大文本（WebView HTML）只翻译不缓存，避免挤爆缓存。
- 多行字符串的空白归一化匹配：资源里 aapt 存的是 `…。\n時間…`，而词典按源码缩进记为 `…。\n      時間…`，
  精确匹配一直落空；`getNormalized` 折叠空白后匹配，实测可多命中约 94 条翻译。
- `MainActivity.isSelfHooked` 改为逐行读取、命中即返回，不再每次 `onResume` 全量载入 `/proc/self/maps`。
- `MainActivity` 翻译统计解析（~4k 行 properties）移出主线程，`onCreate` 不再被 I/O 阻塞。

### Fixed

- `TextView.setText(CharSequence)` 命中翻译后保留原 `Spannable` 样式/超链接，不再被替换成纯 `String`。
- hook 安装逐项隔离：单个 hook 抛异常不再导致同批其余 hook 全部被跳过，失败的 hook 会在下次调用时重试。
- 清理未使用参数（`getAdEntryView` 的 `chain`、广告移除链路的 `classLoader` 等）。
- 移除 9.14.0 已失效的广告控制器视图隐藏逻辑：控制器 `xf.l` 已变为 NativeAd loader，
  `xf.h` 不再提供取视图方法，旧的 `getMethod("f")` 反射必然失败。
- 修复 9.14.0 设置入口：设置页已是纯 Compose（`SettingFragment.onCreateView` 返回
  `ComposeView`），原有的标题栏/"关于本应用"行 View 查找完全失效；现改为在 Activity 内容区
  叠加一个 `NicoEnhance` 悬浮按钮（视图 detach 时移除），非 Compose 版本仍走原标题栏注入。
- 修复 `@string/xxx` 字面量泄漏：`strings.properties` 中 123 条值为 `@string/...` 的翻译条目
  会被原样渲染到界面，现按引用递归解析为实际译文。
- 补齐 12 条悬空 `@string/` 引用的目标键（`common_premium_terms`、`common_autoplay`、
  `notification_setting` 等），并移除 exact 中的空值/错译条目（`を=`、`です=`、`して=设置`）。
- 复数/数组文本翻译兜底：`plurals.`/`array.` 字典段为空时，按整句匹配回退，匹配不到则保持原文。
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
