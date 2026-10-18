# Zipit 简洁古典视觉 + 中英双语 · 设计/实现契约 v1

本文件是 Lead 定稿的三端改造契约：**验证 UI（被保护软件 Auth Gate 弹窗）、Zipit GUI、Admin 管理控制台** 统一为「简洁古典」风格，并支持中文/英文切换。Java 8、零新增依赖；不改业务语义/接口。

## 1. 风格目标：简洁古典（Restrained Classic）

去掉霓虹渐变、光斑、发光描边。用纸张/墨色/古铜金、衬线标题、细线分割、克制阴影，营造安静、典雅、耐看的桌面古典感；信息密度和可读性优先。

### 1.1 颜色 Token（三端统一）

| Token | Light（纸） | Dark（墨） | 用途 |
|---|---|---|---|
| bg | `#F6F1E7` | `#14110D` | 窗口/页面底色 |
| surface | `#FBF8F1` | `#1C1813` | 卡片/输入区 |
| surfaceAlt | `#F1EADB` | `#241F18` | 次级面板/hover |
| border | `#DED3C0` | `#322B22` | 1px 细线 |
| borderStrong | `#C9BCA4` | `#453B2E` | 强调分割线 |
| ink | `#2B2620` | `#E9E1D2` | 主文字 |
| muted | `#7C7163` | `#A29682` | 次级文字 |
| accent | `#8A6A3B`（古铜） | `#C7A76C`（香槟金） | 强调/选中/主按钮 |
| accentStrong | `#6E5329` | `#D8BC85` | hover/active |
| danger | `#A03D32` | `#C8695C` | 错误 |
| success | `#4F6B4A` | `#8AA67F` | 成功 |
| warning | `#8A6D2F` | `#C4A45E` | 警告 |

- 不再使用 indigo→cyan 渐变、彩色光斑、外发光；允许极弱的纸纹/噪点或 1% 渐变。
- 圆角收敛：卡片 6px、按钮/输入 4px、弹窗 8px；边框 1px；阴影极轻。
- 标题下方可加 1px 古铜色细线或双线分隔；列表用横线而非浮起卡片堆叠。
- 状态色只做小面积（圆点/文字/左侧 2px 竖线），不要整块高饱和填充。

### 1.2 字体

- 英文显示字体：`Georgia`（回退 `Palatino Linotype`, `Times New Roman`, 逻辑 `Serif`）；正文 `Segoe UI`。
- 中文显示字体：`SimSun`（宋体）/ `Songti SC` / 逻辑 `Serif`（用于标题）；正文 `Microsoft YaHei UI`/`Segoe UI`。
- 标题使用衬线、字距略宽（0.02em）；正文 13–14px；辅助 12px；数字可用 `Consolas`/`Georgia` 小号大写标签。
- 所有字号整数 pt（避免 JDK8 分数磅值渲染缝隙）。

### 1.3 动效

- 保留暗/亮切换的水平滑动（300ms ease-out），但去掉炫光；可在滑动同时做轻微淡入。
- hover/focus 只改边框/底色/文字色，不做放大或发光。

## 2. Zipit GUI：中英切换

- 新增 `I18n`/`Lang` 机制：`I18n.t("key")` + `zipit-gui/src/main/resources/i18n/messages.properties`（英文默认）与 `messages_zh.properties`（中文）。
- 语言检测顺序：`java.util.prefs.Preferences` 保存值 → `-Dzipit.gui.lang=zh|en` → 系统 Locale（zh 开头为中文，否则英文）。
- 顶栏新增语言切换（`中文 / EN` 或地球图标菜单），点击立即生效并持久化；切换后重建/刷新界面即可（允许短暂重排），不得要求重启。
- **必须覆盖**：侧边栏导航、页面标题/副标题、卡片标题/说明、所有开关/字段标签、按钮、菜单、状态栏、控制台提示/过滤器、对话框（About/工具/错误）、Auth Gate 卡片文案。引擎日志（`[INFO] Pass ...`）保持英文（记录在 README）。
- 所有用户可见字符串不得再硬编码中文或英文；资源键命名 `page.dashboard.title`、`nav.transforms`、`btn.run` 等。
- 中英文两套截图：Dashboard/Transforms/Rules/Enterprise/Console × dark/light × zh/en 至少关键页面覆盖。

## 3. 被保护软件 Auth Gate 弹窗

- 风格同 §1：纸/墨卡片、衬线标题、古铜细线、4px 圆角输入框、实色古铜主按钮、下划线式分段标签（Sign in / Register / Activate card），无渐变药丸。
- **最终调整（用户要求，2026-10-18）**：弹窗**仅英文**（不再提供中文/EN 切换，`-Dzipit.auth.lang` 不再影响弹窗）；**不显示授权软件 id**、**不显示服务器地址/端点**；窗口标题使用配置的通用标题（如 `Authorization`）。服务器 URL/软件 ID 仍在构建时写入并参与服务端校验，只是不向终端用户展示。
- 行为、占位符、HTTP 契约、HWID、缓存、silent 模式完全不变；模板仍必须编译为**单个 class 文件**（无内部类/子类；绘制资源用程序生成图像）。
- 错误/成功文案英文（HWID mismatch、wrong password、network error 等）；silent 模式 stderr 保持英文机器可读文本不变。
- 证据：英文弹窗截图（无 software/server 行）+ 三 tab（Sign in/Register/Activate card）截图；功能 E2E 保持通过。

## 4. Admin 管理控制台

- 风格同 §1（纸/墨/古铜；衬线标题；表格横线；去掉霓虹渐变/发光/大面积渐变按钮）。
- 右上角新增 `中文 / English` 语言切换，默认跟随浏览器 `navigator.language`，选择存入 `localStorage`。
- 全部可见文案双语资源化（当前中英混排改为单语显示）：导航 tab、按钮、表头、提示、空态、toast、登录页、错误信息、页脚。数据值（软件名/用户名/HWID）不翻译。
- 功能/API/鉴权不变；五个 tab 与 HWID/重置功能保持；保留 `?token=&tab#` 深链。
- 证据：zh/en 两套登录页 + 5 个 tab 截图（真实 E2E 数据）；无 CDN。

## 5. 文件所有权

| 范围 | 负责人 | 允许修改 |
|---|---|---|
| GUI 风格 + i18n | gui-rewrite | `zipit-gui/src/**`、`zipit-gui/build/gui-dev/**`、`tools/gui/**` |
| Auth Gate 弹窗风格 + i18n | core-fix | `zipit-core/src/main/java/io/zipit/core/runtime/ZipitAuthGate.java`、`zipit-core/src/test/**`（如需要）、`tools/auth/client/**` |
| Admin 控制台风格 + i18n | auth-server | `zipit-license-server/src/main/resources/admin.html`、`tools/auth/server/**` |
| 契约/集成/打包/E2E/文档/最终回答 | Lead | `tools/ui/**`、`tools/auth/e2e/**`、`tools/auth/VERIFICATION_REPORT.md`、`README.md`、`dist/**` |

构建纪律：GUI 端 javac 开发；Auth Gate 可 javac + mock，最后跑 `:zipit-core:test`；Admin 端 javac smoke + 无头浏览器截图；三者都不得改动其他模块 / Gradle / dist。最终 Gradle clean+dist 与真实服务器 E2E 由 Lead 执行。

---

## 附录 A：实现状态（2026-10-18，已实现并验证）

- Auth Gate：`ZipitAuthGate.java` 完成简洁古典重绘（墨/古铜/衬线/下划线标签）+ 自动 zh/en + `-Dzipit.auth.lang` + 窗口内切换；单 class 模板、占位符/HTTP/HWID/缓存/silent 行为不变，测试 68/68。
- Zipit GUI：Theme/组件全部迁移到 §1 token；`I18n` + `messages.properties`/`messages_zh.properties`（684 keys × 2）与顶栏语言切换（立即生效、持久化、`-Dzipit.gui.lang`）；引擎日志保留英文；GuiStateCheck 全过、真实运行 Verification OK。
- Admin 控制台：`admin.html` 单文件重绘 + 中英 208 keys × 2、顶部语言切换、localStorage/`?lang=`；服务端 smoke 95/95、JUnit 21/21、i18n 检查 18/18。
- 最终验证：全量测试 93/93；真实服务器 E2E 37/37（含 zh/en 弹窗、隔离、HWID、重启持久化）；dist SHA256 一致。
- 证据：`tools/auth/e2e/screens/auth-dialog-{zh,en}.png`、`tools/auth/e2e/screens/gui-{zh,en}/`、`tools/auth/e2e/screens/admin-final/`、`tools/auth/server/evidence/ui/`、`tools/gui/screens/`；总报告 `tools/auth/VERIFICATION_REPORT.md` §11。
