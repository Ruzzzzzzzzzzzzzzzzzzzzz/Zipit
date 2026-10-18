# Zipit Auth — 多软件服务器端授权验证系统 · 设计/接口契约 v1

本文件是 Lead 定稿的**实现契约**。服务端、客户端（混淆注入的验证窗口）、GUI 三方按此并行开发，任何字段/端点变更必须先通知 Lead 并由 Lead 更新本文件。所有新增代码 Java 8 兼容、不得引入第三方依赖。

## 0. 目标与假设

- 一个授权服务器管理**多个软件**；每个软件有独立 id、名称、授权组（group）和**独立卡密**。
- 终端用户注册账号 → 用某软件卡密激活 → 自动加入该软件授权组 → 获得该软件 token。
- 被 Zipit 混淆的软件启动（`main`）或配置的入口方法被调用时，注入的 Auth Gate 弹出登录/注册/激活窗口，验证通过才放行。
- **权限隔离**：token 与授权组按软件绑定；只有 A 软件授权的账号/ token 不能通过 B 软件的验证。同一账号可分别激活多个软件（各自独立授权），互不越权（A 的组不解锁 B）。
- Admin 管理控制台（Web）可生成/作废卡密、管理软件、用户、授权组。
- 现有 RSA 企业许可证（`/api/licenses`、`/api/validate`、`license:` 配置）**保留兼容**，不得破坏既有测试。

## 1. 服务端数据模型（`zipit-license-server`，dataDir 下新增 `auth/`）

- `auth/software.tsv`: `id \t name \t group \t description \t enabled(0/1) \t createdAt`
- `auth/users.tsv`: `id \t username \t passwordHash \t createdAt \t disabled(0/1)`
  - passwordHash = `pbkdf2$<iterations>$<saltBase64>$<hashBase64>`，PBKDF2WithHmacSHA256，salt 16B，key 32B，iterations 120000；校验用 `MessageDigest.isEqual`。
- `auth/memberships.tsv`: `userId \t username \t softwareId \t group \t cardCode \t activatedAt \t expiresAt(0=永久) \t revoked(0/1)`
- `auth/cards.tsv`: `code \t softwareId \t durationDays(0=永久) \t note \t createdAt \t status \t usedByUserId \t usedByUsername \t usedAt \t batchId`
  - status ∈ `unused | used | revoked | disabled`
  - code 格式：`<PREFIX>-<4x4 Base32 大写>`，如 `ALPHA-7K2M-9QX3-4TZ8`；每软件唯一、全局唯一，避免易混淆字符（I/O/0/1）。
- `auth/token.key`: 32 random bytes（HMAC secret），首次启动生成。
- 所有写操作原子（tmp+move）、内存态与文件同步；服务重启后数据仍在。

Token（客户端不透明、服务端无状态校验）：
`base64url(payload) + "." + base64url(HMAC-SHA256(secret, payloadPart))`
payload = `uid|uname|sid|group|iatMillis|expMillis|jti`（user id 内不含 `|`；uname 经过 `|` 转义或限定用户名字符集 `[A-Za-z0-9_.-]{3,32}`，注册时强制校验）。
默认 token 有效期 7 天（`--token-days`）。`/session` 每次校验签名+过期+用户 disabled+membership 未撤销/未过期+software enabled。

## 2. REST API（请求 `application/x-www-form-urlencoded`，响应 `application/json; charset=UTF-8`；除标注外均无需鉴权）

管理端点鉴权：`X-Admin-Token` header（沿用现 `--admin-token`；为空时不鉴权，仅限本机测试）。所有管理端点前缀 `/api/admin/`。

### 2.1 客户端（Auth Gate 使用）

| 方法 | 路径 | 参数 | 成功 200 响应（扁平 JSON，便于轻量解析） |
|---|---|---|---|
| GET | `/api/auth/software` | – | `{"ok":true,"software":[{"id":"a","name":"A","group":"grp-a"}]}` |
| POST | `/api/auth/register` | `username,password` | `{"ok":true,"status":"REGISTERED","userId":"u-..."}` |
| POST | `/api/auth/login` | `username,password,software` | 有授权：`{"ok":true,"status":"OK","token":"...","software":"a","softwareName":"A","group":"grp-a","username":"bob","userId":"u-1","expiresAt":123456789}` |
| | | | 无该软件授权：`{"ok":true,"status":"ACTIVATION_REQUIRED","software":"a","softwareName":"A","username":"bob","userId":"u-1"}` |
| POST | `/api/auth/activate` | `username,password,card,software` | `{"ok":true,"status":"OK","token":...,"software":...,"group":...,"username":...,"userId":...,"expiresAt":...}` |
| POST | `/api/auth/session` | `token,software` | 有效：`{"valid":true,"status":"OK","username":...,"userId":...,"software":...,"group":...,"expiresAt":...}`；无效：`{"valid":false,"status":"TOKEN_INVALID","reason":"..."}`（HTTP 仍 200） |

错误（HTTP 4xx，扁平）：`{"ok":false,"status":"<CODE>","error":"人类可读信息"}`
CODE：`BAD_REQUEST / USERNAME_TAKEN / INVALID_CREDENTIALS / USER_DISABLED / SOFTWARE_UNKNOWN / SOFTWARE_DISABLED / CARD_INVALID / CARD_USED / CARD_REVOKED / CARD_DISABLED / CARD_SOFTWARE_MISMATCH / RATE_LIMITED / SERVER_ERROR`
- 限流：同一 IP 的 login/register/activate 每 60s 最多 30 次；超限 429 `RATE_LIMITED`。
- 注册：用户名 3–32，`[A-Za-z0-9_.-]`；密码 ≥ 6；用户名唯一（大小写不敏感）。
- 激活：卡密必须 `unused`；先校验卡属于请求的 `software`（不匹配 → `CARD_SOFTWARE_MISMATCH`）；成功后卡置 `used` 并绑定用户；创建/复用该用户对该软件的 membership（组 = 软件 group，expiresAt = durationDays>0 ? now+duration : 0）；返回 token。重复激活同一软件卡密 → `CARD_USED`。
- 登录：命中授权且未撤销/未过期/软件 enabled 才返回 token；否则 `ACTIVATION_REQUIRED`。

### 2.2 管理端

| 方法 | 路径 | 参数/说明 |
|---|---|---|
| GET | `/api/admin/overview` | `{software,users,cards:{unused,used,revoked},memberships,recent:[{username,software,activatedAt}]}` |
| GET/POST | `/api/admin/software` | GET 列表；POST `id,name,group,description` 创建（id `[a-z0-9_-]{2,32}` 唯一；group 缺省 `grp-<id>`） |
| POST | `/api/admin/software/{id}/toggle` | enabled 翻转 |
| GET | `/api/admin/cards` | `?software=&status=` 过滤列表 |
| POST | `/api/admin/cards` | `software,count(1..200),durationDays(0..36500),note` → `{ok:true,cards:["CODE",...],batchId:"..."}` |
| POST | `/api/admin/cards/{code}/revoke` | 仅 unused 可作废；已 used 返回 409 `CARD_USED` |
| GET | `/api/admin/users` | `?software=&q=` 列表，含 memberships 内联为 `softwareIds` 逗号串 + `memberships` 数组字段可放平（如 `memberships:"a:grp-a:active;b:grp-b:revoked"`） |
| POST | `/api/admin/users/{id}/toggle` | disabled 翻转（禁用后其 token 立即失效） |
| POST | `/api/admin/users/{id}/memberships/{softwareId}/revoke` | 撤销某软件授权 |
| GET | `/api/admin/audit` | 最近 200 条事件 `ts \t type \t user \t software \t detail`（内存环形 + 可选文件） |

### 2.3 服务端启动/CLI（`zipit-license-server` Main）

保留全部旧参数；新增：`--token-days <n>`（默认 7）、`--no-legacy`（可选，跳过旧 license 路由，不是必须）。启动 banner 增加：
```
  Auth UI    : http://host:port/            （同一个 admin.html，内含 Enterprise License + Software Auth 两个 tab）
  Client cfg : authGate.server=<url>  authGate.software=<softwareId>
```
`--open` 仍打开管理页。

## 3. Admin 控制台（`admin.html` 整体重写，单文件内联 CSS/JS）

深色现代风格（背景 #0B0F17，卡片 #121A28，强调 indigo→cyan，圆角 14px，Segoe UI）。登录页：输入 Admin Token（localStorage 保存，header `X-Admin-Token`），错误提示，可“免 token 模式”直接进入（服务端未设 token 时）。

Tabs：
1. **概览 Overview**：统计卡（软件数/用户数/未用卡密/已用卡密/授权数）、最近激活表。
2. **软件 Software**：表格（id/名称/授权组/启用/用户数/卡密数），新建软件表单，启用切换，行内“生成卡密”快捷入口。
3. **卡密 Cards**：软件筛选 + 状态筛选；批量生成（数量/有效期/备注）；结果列表支持一键复制/导出 `.txt`/CSV；作废未使用卡密。
4. **用户 Users**：搜索用户名/软件筛选；列出用户名、状态、授权（软件:组:状态:到期）、注册时间；禁用/启用、撤销某软件授权。
5. **许可证（旧）**：保留原企业 License 管理功能（可简化为表格+生成/续期/作废/下载）。
所有请求用 `fetch` + `URLSearchParams`（form-encoded）+ `X-Admin-Token`；错误 toast；无外部 CDN/依赖（离线可用）。页面顶部显示服务器 publicUrl 与运行状态。

## 4. 客户端 Auth Gate（Zipit 新 Pass + 注入运行时）

### 4.1 配置（`authGate:`，CLI 同义参数）

```yaml
authGate:
  enabled: false          # 总开关
  server: "http://127.0.0.1:8090"
  software: "my-app"      # 服务端注册的 software id
  title: "My App 授权验证"
  register: true          # 窗口内是否允许注册
  cacheFile: ""           # 默认 ~/.zipit-auth/<software>.token
  timeoutMs: 5000
  entryPoints: []         # 额外验证入口，格式 "com.x.Main#run" 或 "com.x.Main#run()V"
```

Java API（`ZipitConfig.AuthGateOptions`）：
`new ZipitConfig.AuthGateOptions(boolean enabled, String server, String software, String title, boolean allowRegister, String cacheFile, int timeoutMs, List<String> entryPoints)`
getters：`enabled() server() software() title() allowRegister() cacheFile() timeoutMs() entryPoints()`；`ZipitConfig.authGate()`；`Builder.authGate(AuthGateOptions)`；`toBuilder()` 必须复制。

YAML/CLI：`ConfigLoader`/`ConfigWriter` 读写该段；CLI 新增 `--auth-gate --auth-server <url> --auth-software <id> --auth-title <t> --auth-no-register --auth-cache <file> --auth-timeout <ms> --auth-entry <class#method>`（可重复/逗号分隔）。

### 4.2 Pass 与注入

- 新 `AuthGatePass`，`order()` = **118**（在 anti-debug 115 之后、resource-rewrite 120 之前），`enabled()` = `config.authGate().enabled()`。
- `RuntimeSupport` 新增 `default String ensureAuthGate(ClassPool pool, long seed, ZipitConfig.AuthGateOptions options) { return null; }`，`DefaultRuntimeSupport` 实现：读取模板 `ZipitAuthGate.class` → `ClassRemapper` 随机改名（前缀 `zipit/runtime/A`）→ 替换模板中 LDC 占位符 `@@SERVER@@ @@SOFTWARE@@ @@TITLE@@ @@CACHE@@ @@REGISTER@@ @@TIMEOUT@@ @@ENTRY@@` 为实际配置 → `addGenerated`，version = V1_8。
- Pass 对每个可转换类：`main([Ljava/lang/String;)V` 且非 abstract/native → 方法头插入 `INVOKESTATIC <gate> guard ()V`；`entryPoints` 解析为 `owner#name` 或 `owner#name#desc`（`owner` 为**当前改名后 internal name**，也允许原始名通过 mappings 解析），命中方法头插入同样调用；跳过 `<init>/<clinit>`、abstract/native、generated/frozen、keep 类。
- 注入后不得破坏类版本规则（仅 invokestatic）；统计 `authGate.entrypoints`、`authGate.classes`、`authGate.helper`。
- 生成模板类不得被 string-encryption/anti-llm 处理（generated 类本来就跳过）；但会被写入输出 jar。

### 4.3 `ZipitAuthGate`（模板类，包 `io.zipit.core.runtime`）

`public static void guard()`：
1. `silent = System.getProperty("zipit.auth.silent")`；若缓存 token 文件存在 → POST `/session`；`valid=true` 直接 return（刷新缓存）。
2. 若设置 `zipit.auth.token` → 直接校验该 token（失败继续）。
3. 若无缓存且设置了 `zipit.auth.username`/`zipit.auth.password`（+ 可选 `zipit.auth.card`）→ 自动 login（无授权则自动 activate）并写缓存，成功 return。
4. headless 环境（`GraphicsEnvironment.isHeadless()`）：stderr 打印 `Zipit Auth: authorization required`，`System.exit(1)`。
5. 否则 `SwingUtilities.invokeAndWait` 弹出模态验证窗口（无边框圆角、深色玻璃感、可拖拽、Enter 提交、Esc=退出）：
   - Tab「登录」：用户名/密码/登录按钮/切换注册/激活卡密；
   - Tab「注册」：用户名/密码/确认密码/注册（成功后自动切到激活，提示用卡密激活）；
   - Tab「激活卡密」：用户名/密码/卡密/激活按钮；
   - 底部显示服务器地址与软件名、状态行（错误红色、成功绿色）、退出按钮；
   - 成功后保存 token 到缓存文件并关闭窗口。
6. 取消/关闭窗口 → `System.exit(1)`（受保护软件不放行）。
7. 网络/服务器错误：窗口内可重试；不允许跳过。
8. 缓存文件格式：简单文本 `server=<url>\nsoftware=<id>\ntoken=<token>\n`，权限尽量 600（Windows 忽略）。
9. 所有 HTTP 用 `HttpURLConnection`（GET/POST form），connect/read timeout = 配置值；响应用内置扁平 JSON 解析（字符串/数字/布尔）。
10. 自动化属性只用于测试/CI：`zipit.auth.silent=1|true`（禁止弹窗，失败 stderr + exit 1）。测试还可直接调用包内/公开 `authenticate(...)` 辅助方法。

### 4.4 测试

- `zipit-core` 新增 `AuthGateTest`：配置 YAML 往返；pass 注入 main+entrypoint 且输出类可被 `ZipitVerifier` 验证；模板占位符替换正确；关闭时零注入。
- `zipit-license-server` 新增 `AuthServerTest`：注册/重名/登录无授权/激活/激活后可登录/session 校验/token 过期/撤销会员/禁用用户/卡密已用/卡密跨软件不匹配/卡密作废/管理端鉴权/生成批量卡密/概览统计。
- Lead E2E：真实启动服务器 → 混淆 demo app（带 authGate）→ 无凭证启动出现验证窗口（截图）→ 注册+激活卡密 → 验证通过启动成功；错误软件 token 隔离测试；admin.html 浏览器截图。

## 5. Zipit GUI 改造（`zipit-gui`）

1. **字体修复**：左侧导航（"模式栏"）与整体正文偏细小/发虚。
   - `Theme` 基础字体：显式 `new Font("Segoe UI", PLAIN, 14)`（回退 Tahoma），`UIManager.defaultFont` 14；避免 Java 逻辑字体冒充。
   - `NavButton`：14px 常规 / active 使用 `Font.BOLD` 且尺寸不变（不因加粗抖动）；行高 42；图标 19px；颜色对比提升（未选中 MUTED→提高亮度）。
   - 页面正文 13→14，卡片标题 14.5→15，副标题 11.5→12；所有字号取整且不要小于 12。
   - `Main` 在 LAF 前设置 `awt.useSystemAAFontSettings=on`、`swing.aatext=true`（若未设置）。
2. **毛玻璃效果**：
   - 窗口底层绘制 `AmbientBackground`（深/浅两套柔和 indigo/cyan 光斑渐变，随主题切换）。
   - 新增 `Glass` 工具：缓存按尺寸/主题的模糊背景（可缩小后 box-blur 再加权放大），供顶栏、侧边栏、状态栏、模态对话框、DropZone/悬浮面板以半透明+模糊+1px 高光内描边的方式绘制；保证文字可读。
   - 对话框（About/工具/确认）使用毛玻璃背景。
3. **主题切换滑动动画**：
   - `ThemeTransition.play(frame, Runnable switchTheme)`：截取旧窗口图像 → 执行切换 → 截取新窗口图像 → 在 `JRootPane` glass pane 或覆盖 JWindow 上做 300ms 水平滑动（旧图左移出、新图右移入，缓动 ease-out cubic），完成后清理并 repaint。窗口移动/关闭/再次切换时安全中止。Ctrl+T、菜单、顶栏按钮统一走该动画。
   - 提供 `-Dzipit.gui.snapshotAnim=true` 让 snapshot harness 导出动画中间帧（anim-0/1/2.png）用于验收截图。
4. **Auth Gate UI**：Enterprise 页新增「软件授权验证 (Auth Gate)」卡片（启用、服务器 URL、软件 ID、窗口标题、允许注册、缓存文件、超时、入口点多行），并加「测试连接」「打开 Admin 控制台」按钮；GuiState 映射到 `AuthGateOptions`（构造/读取按第 4.1 节契约）。保存/加载 YAML 必须包含该段。
5. 保持既有功能/截图流程/RUN.md 可复现；无新增依赖；Java 8。

## 6. 文件所有权（避免冲突）

| 范围 | 负责人 | 允许修改 |
|---|---|---|
| 服务端 + admin.html + 服务端测试 | auth-server | `zipit-license-server/src/**`, `tools/auth/server/**` |
| core 配置/pass/runtime/CLI/core 测试 + demo fixture | core-fix | `zipit-core/src/**`, `zipit-cli/src/**`, `tools/auth/client/**` |
| GUI（字体/玻璃/动画/Auth 卡片） | gui-rewrite | `zipit-gui/src/**`, `zipit-gui/build/gui-dev/**`, `tools/gui/**` |
| 契约/集成/E2E/打包/README/最终报告 | Lead | `tools/auth/DESIGN.md`, `tools/auth/e2e/**`, `dist/**`, `README.md` |

公共 API 不稳定时先通知 Lead；最终构建/发行由 Lead 统一执行（`gradlew --offline ... dist`）。

---

## 附录 A：实现状态（2026-10-18，v1 契约已全部实现并验证）

- 服务端：`zipit-license-server/src/main/java/io/zipit/licenseserver/auth/{AuthCrypto,AuthStore,AuthApi,JsonOut}.java`；路由接入 `LicenseServer.java`；启动参数 `--token-days/--no-legacy` 在 `Main.java`。兼容性增补：管理端专用 `UNAUTHORIZED(401)`/`USER_UNKNOWN(404)`；`/api/auth/software` 仅返回 enabled；管理端列表 JSON 形状由实现确定。
- 客户端：核心配置 `ZipitConfig.AuthGateOptions`、`ConfigLoader/ConfigWriter`、`RuntimeSupport.ensureAuthGate`、`DefaultRuntimeSupport` 模板注入、`ZipitAuthGate` 运行时、`AuthGatePass(order 118)`、CLI 参数均已落地；被 gate 的 main 与 entryPoints 均注入且保留原类可验证性。
- GUI：Enterprise 页 Auth Gate 卡片 + Test connection / Open admin console 已接入 `AuthGateOptions`；字体/毛玻璃/主题滑动动画完成（字体按 FlatLaf 本地化物理字体族，本机 zh-CN 为微软雅黑 UI，避免 JDK8 中文 tofu）。
- 验证：见 `tools/auth/VERIFICATION_REPORT.md`；端到端脚本 `tools/auth/e2e/run-e2e.ps1`；服务端证据 `tools/auth/server/EVIDENCE.md`；客户端说明 `tools/auth/client/RESULTS.md`。
- 已知限制见 VERIFICATION_REPORT §9（明文 HTTP、token 缓存、客户端可被 patch、无离线宽限、单机文件存储、headless 拒绝启动）。
- HWID 机器绑定（附录 B）已实现：服务端 users.tsv 第 6 列 + login/activate/session 强校验 + 管理端 reset/set + 审计；客户端 AuthGate 自动计算 SHA-256 HWID 并附带、异机显示红色错误并拒绝放行；E2E 34/34、服务器 API 44/44、全量测试 93/93 通过。

---

## 附录 B：HWID 绑定契约（v1.1，追加要求）

目标：每个使用者账号绑定一台机器的 HWID；HWID 不匹配时即使账号密码/授权正确也不能使用。

### B.1 服务端

- `auth/users.tsv` 增加第 6 列 `hwid`（旧数据缺列时按空串兼容读取；空 = 未绑定）。
- 客户端请求（原 form-encoded）新增必填参数 `hwid`：长度 16–128，字符集 `[A-Za-z0-9_-]`（客户端发 64 位小写 SHA-256 hex）。
  - `POST /api/auth/login`、`/api/auth/activate`、`POST /api/auth/session` 均带 `hwid`；缺失/格式非法 → 400 `{"ok":false,"status":"HWID_REQUIRED"}`（session 返回 200 `{"valid":false,"status":"HWID_REQUIRED"}`）。
- 绑定规则：账号 `hwid` 为空时，首次成功 login/activate 自动写入并持久化；非空且与请求不同 → login/activate 返回 HTTP 403 `{"ok":false,"status":"HWID_MISMATCH","error":"This account is bound to another machine"}`；session 返回 HTTP 200 `{"valid":false,"status":"HWID_MISMATCH"}`。均不签发 token、不刷新授权。
- 管理员：
  - `GET /api/admin/users` 每项增加 `"hwid":"..."`（未绑定为空串）。
  - `POST /api/admin/users/{id}/hwid/reset` 清除绑定（审计事件 `hwid_reset`），返回 `{"ok":true}`。
  - `POST /api/admin/users/{id}/hwid`（可选）参数 `hwid` 手动设置/换绑（审计 `hwid_set`）。
- 审计事件增加 `hwid_bind` / `hwid_reset` / `hwid_set`；smoke/JUnit 需覆盖：首次绑定、同 HWID 通过、不同 HWID 拒绝（login/activate/session）、管理员重置后可换机、缺失 hwid 报 HWID_REQUIRED、旧 TSV 数据迁移。

### B.2 客户端（ZipitAuthGate）

- HWID 计算（纯 Java 8，无外部命令，优先稳定且不敏感）：收集排序后的非回环网卡 MAC、主机名（HOSTNAME/COMPUTERNAME）、`user.name`、`os.name`、`os.arch`，拼接后 SHA-256 → 64 位小写 hex。`-Dzipit.auth.hwid=<value>` 可覆盖（测试/受管环境）。
- 登录/激活/session 请求均附带 `hwid`；token 缓存不存 hwid（每次重新计算）。
- 窗口内遇到 `HWID_MISMATCH` → 状态行红色显示 “此账号已绑定其他机器 / Hardware ID mismatch”，不允许继续；silent 模式 → stderr `ZIPIT_AUTH_REQUIRED: hwid mismatch` + exit 1。
- `HWID_REQUIRED` 按客户端 bug 处理（正常客户端总会发送）；任何错误都不得放行。
- 客户端 mock/E2E 需覆盖：首次登录绑定、同机重登成功、伪造不同 `-Dzipit.auth.hwid` 被拒绝。

### B.3 E2E 验收（Lead 追加）

- 服务器 API：注册→登录带 hwidA 成功并绑定；session 用 hwidA 有效、用 hwidB 无效；login 用 hwidB 返回 403 HWID_MISMATCH；管理员 reset 后 hwidB 可登录并绑定。
- Auth Gate：带 `-Dzipit.auth.hwid=...` 跑通授权启动；再以不同 hwid 运行同一已绑定账号 → 被拒绝、应用不启动；管理员 reset 后恢复。
- 管理台用户页显示 HWID/未绑定状态与“重置 HWID”操作。


## 附录 C：Auth Gate UI 最终调整（v1.2，用户要求，2026-10-18）

- 弹窗**仅英文**：不再提供 `中文 / EN` 切换，`-Dzipit.auth.lang` 不再影响弹窗（GUI/管理台语言切换不受影响）。
- **隐藏部署信息**：不显示授权软件 id，不显示服务器 URL/端点；窗口标题使用 `authGate.title`（建议通用值如 `Authorization`）。服务器 URL/软件 id 仍在构建时嵌入并参与服务端校验。
- 超时/错误/成功/ silent stderr 行为不变；模板仍单 class 文件。修改已合并进 core `ZipitAuthGate.java`，官方 dist（94/94 测试）已包含。
- Raven 授权版实测证据：`tools/forge-test/AUTH_GATE_TEST_REPORT.md` 与 `evidence/authgate-en-popup/authgate-popup-cropped.png`。
