# Zipit 多软件授权验证系统 + GUI 增强 — 最终验证报告

- 验证人：Team Lead（独立于 auth-server / core-fix / gui-rewrite）
- 日期：2026-10-18
- 结论：**PASS**
- 环境：Windows（1920×1080，中文 locale）、Zulu JDK 8u502、Gradle 8.14 `--offline`、Microsoft Edge（headless 截图）
- 契约：[tools/auth/DESIGN.md](DESIGN.md)（v1，实现无破坏性偏差，详见 §8）

---

## 1. 交付范围

| 子项 | 内容 |
|---|---|
| 服务端 | `zipit-license-server` 扩展为多软件授权服务器：用户注册/登录、按软件区分的卡密、授权组/成员关系、HMAC token、严格按软件隔离、Admin API、全新 Web 管理控制台；旧 RSA 企业许可证路由完整保留 |
| 客户端 | Zipit 新 `authGate` 配置 + `AuthGatePass(order 118)`：向被保护软件的 `main` 与配置入口注入 `guard()`；运行时模板 `ZipitAuthGate` 提供品牌化登录/注册/卡密激活窗口、token 缓存与在线校验（无离线绕过） |
| GUI | 字体清晰化（左侧导航等）、毛玻璃质感、暗/亮主题 300ms 水平滑动过渡动画、Enterprise 页新增 Auth Gate 配置卡 + 连接测试 + 管理控制台入口 |
| 打包 | `dist/` 全部产物（CLI、GUI、授权服务器、插件、SBOM、SHA256SUMS） |

## 2. 构建与测试

```
.\gradlew.bat --offline clean :zipit-core:test :zipit-gui:test \
  :zipit-license-server:test :zipit-gradle-plugin:test \
  :zipit-cli:fatJar :zipit-gui:fatJar :zipit-license-server:fatJar dist
→ BUILD SUCCESSFUL（37 tasks）
测试：94 tests / 0 failures / 0 errors / 0 skipped
  core 69（含 AuthGateTest 4、HWID 运行时模板、VM Boolean 回归）、license-server 21（含 AuthServerTest 17 + 旧 LicenseServerTest 4，
  其中 4 个 HWID/迁移测试）、gui 3、gradle-plugin 1
dist/SHA256SUMS：8 个产物全部校验一致（0 mismatches）
```

最终构建（含 VM Boolean 修复）于 2026-10-18 16:12 完成：94/94 测试通过；`zipit-1.0.0-all.jar` 715,932 B、`zipit-gui-1.0.0-all.jar` 1,907,816 B、`zipit-license-server-1.0.0-all.jar` 800,695 B；dist SHA256 0 mismatch。

## 3. 服务端验证

### 3.1 单元/进程内测试（auth-server 出具，Lead 复核日志）
- `AuthSmoke`（plain main，真实 HTTP）：**95/95 PASS**（`tools/auth/server/evidence/smoke-run.log`）——v1 的 72 项 + 18 项 HWID + 5 项旧数据迁移；覆盖注册/重名、无授权登录、激活、重复卡密、跨软件卡密不匹配、作废卡密、撤销会员、禁用用户/软件、管理员鉴权 401、批量卡密、概览统计、审计、限流第 31 次 429、重启后旧 token 仍有效，以及 HWID 首次绑定/同机通过/异机拒绝/reset 换绑/审计。
- JUnit：**21/21 PASS**（4 legacy + 17 新 `AuthServerTest`，`evidence/junit-run.log`）。
- 契约检查（真实 Main 进程）：token payload 格式/TTL、TSV 列布局（users 6 列含 hwid）、32B `auth.key`、`--no-legacy`、重启持久化、HWID reset/set 审计（`evidence/contract-check.log`）。

### 3.2 Lead 独立 API E2E（dist 服务器 jar，端口 18090，全新数据目录）
`powershell -ExecutionPolicy Bypass -File tools/auth/e2e/run-e2e.ps1 -Fresh` → **37/37 checks PASS**（`tools/auth/e2e/logs/run-e2e-console.log`、`summary.txt`），其中服务器 API 套件 **44/44 PASS**，关键证据：

| 场景 | 结果 |
|---|---|
| 创建软件 alpha / beta，各自生成卡密 | `ALPHA-…`、`BETA-…` |
| 注册 → 无授权登录 | `ACTIVATION_REQUIRED` |
| 用 alpha 卡密激活 → 登录 alpha | `OK` + token |
| 登录 beta（未激活 beta） | `ACTIVATION_REQUIRED` |
| **隔离**：alpha token 校验 beta | `valid:false / TOKEN_INVALID` |
| **隔离**：beta token 校验 alpha | `valid:false / TOKEN_INVALID` |
| 卡密跨软件使用（beta 卡配 alpha 软件） | `CARD_SOFTWARE_MISMATCH`（409） |
| 已使用卡密再次激活 | `CARD_USED` |
| **隔离**：只有 alpha 授权的账号登录/校验 beta | `ACTIVATION_REQUIRED` / `TOKEN_INVALID` |
| **HWID**：login/activate/session 缺失 hwid | `HWID_REQUIRED` |
| **HWID**：首次登录/激活绑定机器 A | `OK`，users 列表 `hwid` 一致 |
| **HWID**：机器 B 登录/激活/会话 | `403 HWID_MISMATCH` / `valid:false HWID_MISMATCH` |
| **HWID**：管理员 reset 后机器 B 绑定成功、机器 A 被拒 | `OK` → `HWID_MISMATCH` |
| 管理员端点无 token | 401 |
| 禁用用户后已有 token | 立即 `TOKEN_INVALID`；重新启用恢复 |
| 服务重启后 | 用户数据仍在，旧 token 仍 `valid:true` |

### 3.3 管理控制台
- `GET /` 由 dist 服务器进程返回 HTML（67092 字节），五个 tab 全在，无 `{{PUBLIC_URL}}` 占位残留。
- 无头 Edge 实拍（免 token 模式、真实 E2E 数据）：`tools/auth/e2e/screens/admin/admin-{overview,software,cards,users,license}.png`。概览显示 2 软件 / 2 用户 / 3 已用卡密 / 3 授权 + 审计流水；卡密页显示批量生成、状态、使用者、批次、复制/导出；均为真实服务器请求数据。
- 管理 API 端点：`overview / software(+toggle) / cards(+revoke) / users(+toggle,membership revoke,hwid reset/set) / audit`；`X-Admin-Token` 强制（空=本机开放）。
- 用户页展示 **HWID 硬件绑定** 列（已绑定/未绑定 + 全文 tooltip）、授权（软件:组:状态:到期）、禁用/设置 HWID/重置 HWID 操作（`tools/auth/e2e/screens/admin/admin-users.png`）。

## 4. 客户端 Auth Gate 验证

真实流程（dist CLI + dist 服务器，DemoApp 被混淆）：

1. 混淆：`--auth-gate --auth-server http://127.0.0.1:18090 --auth-software alpha --auth-cache <file> --verify`
   → `auth-gate: zipit/runtime/AhOCCSqaMMK guards 1 method(s) (1 main, 0 configured)` + `Verification: OK`（4 classes）。
2. **未授权**（silent 模式、无 token/凭据）：stderr `ZIPIT_AUTH_REQUIRED: no cached token`，exit 1，应用未启动（`APP_STARTED` 不出现）。
3. **授权通过**（silent + 正确用户名/密码）：exit 0，`APP_STARTED`/`APP_FINISHED`，写出 token 缓存。
4. **缓存复用**：仅 silent、无凭据 → 在线 `/session` 校验通过，exit 0（不是离线放行）。
5. **权限隔离**：只有 alpha 授权的账号运行 beta 构建 → exit 1，`ZIPIT_AUTH_REQUIRED: ACTIVATION_REQUIRED`。
6. **服务器不可达**：`ZIPIT_AUTH_REQUIRED: server unreachable during login/activation`，exit 1，无绕过。
7. **交互窗口**：桌面启动未授权构建 → 弹出品牌化登录窗口（ZIPIT AUTH 面板、渐变胶囊式 登录/注册/激活卡密 分段标签、深色圆角输入框、渐变主按钮），应用被阻塞；最终窗口截图 `tools/auth/e2e/screens/auth-dialog-dark.png`，以及 `tools/auth/client/build/gate-dialog.png`、`dialog-register.png`、`dialog-activate.png`。
8. 产物合法性：被 gate 的 jar `audit_classes.py` → `PROBLEMS: none`，`JarLoadHarness` → `VERIFIED_CLASSES=4 FAILED_CLASSES=0`。

### 4.1 HWID（机器绑定）E2E（同一脚本内）

客户端 `ZipitAuthGate` 自动计算 64-hex HWID（网卡 MAC + 主机名 + 用户名 + OS/arch 的 SHA-256；`-Dzipit.auth.hwid` 可覆盖），并随 session/login/activate 发送：

| 场景 | 结果 |
|---|---|
| 新账号首次登录 + 卡密激活 | 绑定本机 HWID，`APP_FINISHED`，exit 0 |
| 同机缓存 token 再启动 | 在线 session + HWID 校验通过，exit 0 |
| `-Dzipit.auth.hwid=<另一台机器>` + 缓存 token | `ZIPIT_AUTH_REQUIRED: hwid mismatch`，exit 1，应用不启动 |
| 另一台机器 HWID + 正确密码登录 | 服务端 `403 HWID_MISMATCH`，客户端红色错误，exit 1 |
| 管理台重置该用户 HWID 后，用新 HWID 启动 | 重新绑定成功，exit 0 |
| 重置/换绑后，原机器再启动 | `ZIPIT_AUTH_REQUIRED: hwid mismatch`，exit 1 |
| 界面错误提示 | 弹窗状态行红色 “此账号已绑定其他机器 / Hardware ID mismatch”（`tools/auth/client/build/dialog-hwid-mismatch.png`） |

## 5. GUI 增强验证（dist 打包 GUI）

使用 dist GUI jar 的内置 snapshot harness（真实引擎运行 + 真实组件渲染，`-Dzipit.gui.snapshotAnim=true`）：

- **字体**：基础 UI 字体 14pt（本机 zh-CN 为微软雅黑 UI，英文环境解析 Segoe UI），左侧导航 14px/42px 行高/19px 图标、正文 14、卡片标题 15；开启系统 AA；CJK 路径不再 tofu。截图 `tools/auth/e2e/screens/gui-zh/new-dashboard-dark-zh.png`。
- **简洁古典风格（第三轮）**：纸/墨/古铜 token、衬线标题 + 古铜细线、1px 细线、无霓虹渐变/光斑；`tools/auth/e2e/screens/gui-{zh,en}/new-*.png`。
- **中英切换（第三轮，后续按用户要求调整）**：GUI 顶栏 `中文 / EN`，684 keys × 2 语言资源，立即生效并持久化（`-Dzipit.gui.lang` 可覆盖）；Admin 控制台 208 keys × 2 + `?lang=` + localStorage；引擎日志保持英文；**Auth Gate 弹窗按要求改为仅英文并隐藏软件 id/服务器地址（见 §13）**。详见 §11。
- **主题滑动动画**：`anim-0/1/2.png` 为 300ms 水平滑动中间帧（旧图左移/新图右移，ease-out cubic），所有切换入口统一走动画。
- **Auth Gate 配置卡**：Enterprise 页第一屏显示启用开关、Server URL、Software ID、窗口标题、允许注册、缓存、超时、入口点、Test connection / Open admin console；YAML 保存/加载往返通过（GuiStateCheck ALL CHECKS PASSED）。
- 打包 GUI 真实运行：输入 demo `SnakeGame.jar` → 15 classes / 1 resource / **Verification: OK**，状态栏 `Protected 15 classes · verification OK`；无 stderr 异常。
- 全量截图：`tools/auth/e2e/screens/gui/`（5 页 × 深浅 + anim + glass）。GUI 开发者证据：`tools/gui/screens/`。

## 6. 使用方式

```bash
# 1) 启动授权服务器（管理控制台 http://host:8090/）
java -jar dist/zipit-license-server-1.0.0-all.jar \
  --host 0.0.0.0 --port 8090 --data ./auth-data \
  --admin-token s3cret --public-url https://auth.example.com

# 2) 管理台建软件（如 my-app）+ 生成卡密
# 3) 给软件加授权门
java -jar dist/zipit-1.0.0-all.jar -i app.jar -o app-protected.jar \
  --auth-gate --auth-server https://auth.example.com \
  --auth-software my-app --auth-title "My App 授权验证" --verify

# 4) 用户运行 app-protected.jar → 登录/注册/输入卡密 → 服务端确认该软件授权后启动
```

YAML `authGate:` 段、`--auth-*` 参数、管理 API 详见 [DESIGN.md](DESIGN.md) 与 [README.md](../../README.md)。

## 7. 关键证据索引

| 证据 | 路径 |
|---|---|
| 服务端 smoke 72/72、JUnit 17/17、契约检查 | `tools/auth/server/evidence/` |
| Lead 端到端 29/29 checks | `tools/auth/e2e/logs/`（`run-e2e-console.log`、`summary.txt`、`state.json`） |
| 授权窗口/管理台截图 | `tools/auth/e2e/screens/auth-dialog-dark.png`、`tools/auth/client/build/gate-dialog.png` / `dialog-hwid-mismatch.png` 等、`tools/auth/e2e/screens/admin/` |
| **HWID 证据** | `tools/auth/client/build/hwid-*.log`、`tools/auth/server/evidence/smoke-run.log`（HWID 段）、`evidence/contract-check.log`、`evidence/admin-users.png`；E2E `logs/gate-hwid-*.log` |
| GUI 截图/动画帧 | `tools/auth/e2e/screens/gui/`、`tools/gui/screens/` |
| 被 gated 的 demo jar + 映射 | `tools/auth/e2e/build/` |
| 客户端/服务端实现说明 | `tools/auth/client/RESULTS.md`、`tools/auth/server/EVIDENCE.md` |

## 8. 实现与契约的差异（均为兼容性增补）

- 服务端增加管理端专用错误码 `UNAUTHORIZED(401)`、`USER_UNKNOWN(404)`；客户端错误码与 DESIGN §2.1 完全一致。
- 管理端列表 JSON 形状在契约未限定处由实现确定（控制台同源消费）；`/api/auth/software` 只返回启用软件。
- 卡密格式为 `PREFIX-XXXX-XXXX-XXXX`（3×4，DESIGN 示例同款）。
- GUI 字体使用 FlatLaf 本地化物理字体族（本机为微软雅黑 UI，避免 JDK8 下硬编码 Segoe UI 对中文路径显示 tofu）。
- 限流为单进程内存计数（30/60s/IP，register+login+activate 共享预算）。

## 9. 遗留风险（不影响本次验收）

- 默认明文 HTTP；生产应使用 `--keystore/--storepass` 启 HTTPS 或置于 TLS 反向代理后。
- token 缓存为本地明文文件（用户目录），仅含短期 token，可吊销；不含密码。
- Auth Gate 是**服务端授权校验**，不是防逆向的密码学证明：攻击者可 patch 客户端字节码；不要把本机校验当唯一安全边界。
- 无离线宽限：服务器不可达时按设计拒绝（可通过修改 Gate 模板加宽限策略，但会引入绕过风险）。
- 服务器数据为单机原子文件存储（无集群/多实例共享写）；并发多实例部署需外部数据库。
- 演示/自动化属性（`zipit.auth.silent/username/password/card` 等）只用于测试，仍必须通过服务端校验，不构成绕过。
- 桌面弹窗需要图形环境；headless 下按设计直接拒绝启动。
- **HWID 相关**：指纹由 MAC/主机名/用户名/OS/arch 哈希得到，更换网卡、重装系统、虚拟机克隆等会触发 `HWID_MISMATCH`，需要管理员在用户页“重置 HWID”（这是预期流程）；MAC 可被伪造/篡改，且 Java 层面无法读取 TPM 等硬件根，因此 HWID 是“防普通共享/换机”的强度，不是不可伪造的硬件证明；机器绑定为账号级（一个账号一台机器），如需多机授权需扩展为多 HWID 列表。

## 10. 追加要求：HWID 机器绑定（v1.1）

- 契约：DESIGN 附录 B；服务端证据 `tools/auth/server/evidence/`（smoke 95/95、JUnit 21/21、contract-check、admin-users.png）。
- 实现：
  - 服务端：`users.tsv` 第 6 列 hwid（旧 5 列兼容迁移）；login/activate/session 强制校验 hwid；首次成功签发 token 时绑定；异机 `HWID_MISMATCH`（login/activate 403，session 200 valid:false）；管理端 `GET users` 暴露 hwid，`POST /api/admin/users/{id}/hwid/reset`、`/hwid`（set）；审计 `hwid_bind/hwid_reset/hwid_set`。
  - 客户端：`ZipitAuthGate` 单类内计算 SHA-256 HWID（排序非回环 MAC、主机名、user.name、os.name、os.arch），`-Dzipit.auth.hwid` 可覆盖；三个请求均带 `hwid`；异机时弹窗红色错误/`ZIPIT_AUTH_REQUIRED: hwid mismatch` + 退出，不缓存绕过。
  - 管理台：用户页 HWID 列 + 设置/重置 HWID 按钮 + toast。
- 验收：Lead E2E 中 HWID 段全部通过（首次绑定、同机缓存通过、异机拒绝、admin reset 换绑、换绑后原机拒绝）；全量测试 93/93 通过。

## 11. 第三轮：简洁古典 UI + 中文/英文切换

目标：三端 UI 统一为「简洁古典」（纸/墨/古铜、衬线标题、细线、无霓虹渐变），并让混淆器支持中英切换。

- **设计契约**：`tools/ui/DESIGN.md`（颜色/字体/圆角/动效 token + 三端 i18n 行为）。
- **Auth Gate 弹窗（最终版，v1.2）**：墨色面板 #1C1813、古铜 #C7A76B、衬线标题 + 细线、下划线式 Sign in / Register / Activate card 标签；按用户后续要求**仅英文**、**不显示授权软件 id**、**不显示服务器地址/端点**、窗口标题使用配置的通用标题（如 Authorization），不再有 `中文 / EN` 切换与 `-Dzipit.auth.lang` 行为；业务/占位符/HTTP/HWID/缓存/silent 行为不变，模板仍为单 class 文件。截图 `tools/auth/e2e/screens/auth-dialog-{zh,en}.png`（现均为英文界面）、`tools/forge-test/evidence/authgate-en-popup/authgate-popup-cropped.png`。
- **Zipit GUI**：全新 parchment/ink/bronze 主题、衬线标题、1px 细线、无霓虹/光斑；顶栏语言按钮（中文/EN）立即生效并持久化，`-Dzipit.gui.lang` 可覆盖；684 keys × 2 语言资源；引擎日志保持英文。打包 GUI 实跑 15 classes / Verification OK、snapshot stderr 为空。截图 `tools/auth/e2e/screens/gui-{zh,en}/`（5 页 × 深浅 + anim + About）。
- **Admin 控制台**：纸色默认主题、衬线标题、细线表格、实色古铜按钮；右上 `中文 / English` 切换（navigator.language 默认、localStorage 持久化、`?lang=zh|en` 深链），208 keys × 2，单语显示无混排；HWID/重置等全部功能保留。dist 服务器实拍 10 张：`tools/auth/e2e/screens/admin-final/`；开发者证据 `tools/auth/server/evidence/ui/`（含 i18n 检查 18 PASS）。
- **验证**：全量测试 **94/94** 通过；真实服务器 E2E **37/37** checks（含 Auth Gate 弹窗截图、授权/隔离/HWID/重启持久化）；dist SHA256 0 错误；三端截图均为 dist 打包产物实拍/真实引擎渲染。


## 12. 方法虚拟化 Boolean 解释器崩溃修复（真实 Forge 反馈）

- **现象**：真实 Forge 1.8.9 客户端加载极限档 v1（methodVirtualization=true）时，Mixin/模组发现正常，mod 初始化阶段崩溃：
  `java.lang.ClassCastException: java.lang.Boolean cannot be cast to java.lang.Number` at `zipit.runtime.<VM>.i(ZipitVM.java:817)` ← `run(ZipitVM.java:316)`。
- **根因**：JVM 字节码中 boolean 以 int(0/1) 表示，但方法虚拟化包装器把 boolean 参数装箱成 `java.lang.Boolean` 放入 VM 局部变量表，VM 在执行 `ifeq/if_icmp/iand` 等指令时直接 `((Number)value).intValue()` 解箱 → Boolean 走到 int 路径崩溃；布尔返回值解箱路径也只接受 Number。
- **修复**：
  - `VirtualizationPass.box()`：boolean 参数装箱改为 `Integer.valueOf(0/1)`，保证 VM 内 boolean 统一为 int 表示；
  - `VirtualizationPass` 虚拟化方法返回解箱：BOOLEAN 分支同时兼容 `Boolean` 与 `Number`（INSTANCEOF 分支），不再对裸 Boolean 做 CHECKCAST Number；
  - `ZipitVM.i/l/f/d()`：兼容 `Boolean`（true=1/false=0）、`Character` 与所有 `Number`；`coerceReturn('Z')` 同样兼容两种表示；
  - 新增回归测试 `RegressionTest.virtualizedBooleanParametersAndReturnsMatchJvmSemantics`（boolean 参数参与 `&`/`!`、布尔返回值参与条件与算术路径），输入/输出行为一致。
- **复现构建 v3**：`raven-out/extreme-v3.yml`（与 v1 相同的 keep/极限配置，methodVirtualization=true）→ `raven-out/raven-bS-16-extreme-v3.jar`：25,254 类、20,328,987 B、SHA-256 `508E4174…7CA98766`；虚拟化 11,368 方法；结构检查 0 重复类/0 悬空引用、22,740/22,740 VM 运行时类加载 OK；mixins.raven.json / refmap / mcmod.info 与原件字节一致（refmap 47 组全部命中）；TweakClass/MixinConfigs 保留。
- **Forge 1.8.9 实测（A/B 对照，已 PASS）**：
  - 环境：Corretto 1.8.0_462 + Forge 11.15.1.1902 + OptiFine M5（LaunchWrapper/FMLTweaker），隔离 gameDir 仅放 v3；
  - 复现命令：`powershell -ExecutionPolicy Bypass -File tools\forge-test\run-forge-test.ps1 -Jar <jar> -Label <label>`；
  - v3 结果 **PASS**：Mixin 成功加载、FML 识别 4 个 mod、`Forge Mod Loader has successfully loaded 4 mods`、资源重载到主菜单（`Created: 512x512 textures-atlas`），stderr 空、无 crash report、**0 次** `Boolean cannot be cast` / `zipit.runtime` 异常；
  - 负向对照：同一 harness 跑归档 v1 得到 **FAIL-VM-BOOLEAN**，crash report 精确复现旧栈（`ClassCastException … ZipitVM.java:817 → setActive`），证明 harness 能检测该 bug 且 v3 已修复；
  - 证据：`tools/forge-test/{README.md,VERIFICATION_REPORT.md,run-forge-test.ps1}`，PASS 日志 `evidence/v3-virtualized/`，FAIL 对照 `evidence/v1-known-broken/`（含 crash txt），结构/加载审计 `evidence/v3-loadcheck-*.txt`。
  - 残余（均为原版/混淆前已有，不是启动缺陷）：FML 签名缺失警告、`mixins.raven.json` 无 minVersion、OptiFine CustomSky ClassNotFound；另外极限重命名可能影响 in-world 脚本引擎符号绑定（如 TellyBridge），若需要脚本功能应加 `keystrokesmod.script.**`/模块公开 API 的 keep 规则后复测。
- **最终状态（2026-10-18 16:20）**：用户已在真实 Forge 1.8.9 客户端 **进世界实测正常**，v3 定为最终交付；成品路径 `混淆器/raven-bS-16-extreme.jar`（同字节副本 `raven-bS-16-extreme-v3.jar`），SHA-256 `508E41740A004E4BB716DD17DBD3BCD7C08B0A12EBD4E807146F34727CA98766`；v2（无虚拟化，5.9 MB）归档为 `raven-out/raven-bS-16-extreme-v2-noVM-final.jar`；原始 `raven-bS-16.jar` 未改动；映射 `raven-out/raven-extreme-v3-mapping.txt` 保留用于崩溃栈还原。


## 13. Auth Gate 隐私/语言调整（用户后续要求）与 Raven 授权版交付

用户要求：验证弹窗**不显示授权软件 id**、**不显示服务器地址**、**仅英文**。

- **交付 jar**：`混淆器/raven-bS-16-extreme-authgate.jar`，SHA-256 `D6CFEED95F75A22B99198965EB1C5E8D3672509C309D17DA8067C2FAC8CF6ACA`，25,255 类（~20.35 MB），v3 全部极限变换 + Auth Gate；注入入口 `keystrokesmod.Raven#init`（混淆后 `a/a.init`）+ 3 个原 main 方法。
- **弹窗行为（实测）**：英文 Sign in / Register / Activate card / Username / Password / Exit；无 `Software: <id>`、无服务器/软件页脚；窗口标题通用 `Authorization`；登录/注册/激活卡密/HWID 绑定/token 缓存逻辑不变。截图 `tools/forge-test/evidence/authgate-en-popup/authgate-popup-cropped.png`。
- **真实 Forge 1.8.9 验证**：无凭证启动→英文弹窗正常拦截；有效 token + 匹配 HWID→`Forge Mod Loader has successfully loaded 4 mods` 进主菜单，无崩溃/无 crash report，旧 VM 布尔崩溃 0 次。证据 `tools/forge-test/AUTH_GATE_TEST_REPORT.md`、`evidence/authgate-*`。
- **核心合并**：上述 UI 修改已合并进 `zipit-core/src/main/java/io/zipit/core/runtime/ZipitAuthGate.java`（`chinese=false` 仅英文、移除 software 行、移除 endpoint 页脚、隐藏语言切换），随后 `clean + dist` 重建：**94/94 测试通过**、dist SHA256 0 错误；今后用官方 dist 构建的 Auth Gate 默认即为该英文/隐藏信息界面（GUI 与管理台的中英切换不受影响）。此前的中文+显示软件/服务器版本归档为 `raven-out/raven-bS-16-extreme-authgate-zh-software-visible.jar`。
- **待用户提供**：当前 authgate jar 内置的是本机测试服 `http://127.0.0.1:18090`（软件 `raven-bs16`，含测试账号/卡密）；公开发布前需要用户提供**真实授权服务器地址**与**软件 id** 重新构建（UI 不再显示它们，但仍需写入 jar 参与服务端校验）。本地演示可运行 `Zipit/tools/forge-test/auth-server/start-auth-server.ps1`。
