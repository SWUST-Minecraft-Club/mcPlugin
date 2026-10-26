# MinecraftMcp

Fabric 客户端测试 Mod，通过 **MCP Streamable HTTP** 暴露 26 项测试工具。首版为 `Beta_0_1_0`，支持 **Minecraft 26.3 / Java 25 / Fabric Loader ≥ 0.20.0 / Fabric API ≥ 0.161.0（26.3 对应版本）**。服务端无需安装。

## 构建与安装

在仓库根执行：

```bash
./gradlew -p ./fabric-26_3-mod/MinecraftMcp build
# 单独测试不依赖游戏的核心（JDK 21+）
./gradlew -p ./fabric-26_3-mod/MinecraftMcp :core:test
# 单独构建 26.3
./gradlew -p ./fabric-26_3-mod/MinecraftMcp :mc26_3:build
# 已有官方客户端 jar 时可复用（仍校验官方 SHA-1）
./gradlew -p ./fabric-26_3-mod/MinecraftMcp build -PminecraftClientJar=<已有的客户端jar>
```

产物为 `./fabric-26_3-mod/MinecraftMcp/build/libs/MinecraftMcp-mc26_3-fabric-Beta_0_1_0.jar`，自动留档至 `./release/MinecraftMcp/`。把产物和 **26.3 的 Fabric API** 放进客户端实例的 `mods/` 后启动。编译期 Fabric 声明桩不打包，运行时使用 Loader / Fabric API 的真实类；不需要 Loom，不包含官方客户端或第三方库。

官方清单、客户端和编译库均验证官方 SHA-1，下载缓存位于项目的 `./.gradle/minecraft-downloads/`。缓存齐全后可用 `--offline` 重建；`clean` 不删除下载缓存。

首次启动生成实例目录下的 `config/minecraft-mcp.json`：

```json
{
  "enabled": true,
  "port": 8765,
  "token": "首次启动自动生成的随机令牌，请从本机配置读取",
  "requestTimeoutMs": 15000,
  "allowCommands": true,
  "allowChat": true
}
```

配置修改后重启生效。`enabled=false` 停用服务；多开实例使用不同 `port`，`port=0` 可分配随机端口并从日志读取。令牌至少 32 位。配置错误或端口占用只停用测试服务，并在日志报告错误类型。

## MCP 客户端连接

- URL：`http://127.0.0.1:8765/mcp`
- 传输：Streamable HTTP，响应为 JSON；不提供独立 GET SSE 流（返回 405）。
- 请求头：`Authorization: Bearer <配置中的 token>`。
- POST 使用 `Content-Type: application/json` 以及 `Accept: application/json, text/event-stream`。
- 协议：`2025-11-25`，兼容协商 `2025-06-18` / `2025-03-26`；初始化后可带对应的 `MCP-Protocol-Version`。
- 无会话，不分配 `Mcp-Session-Id`；`initialize` → `notifications/initialized` → `tools/list` / `tools/call`。

通用远程 MCP 配置示例（具体字段取决于客户端）：

```json
{
  "mcpServers": {
    "minecraft": {
      "type": "http",
      "url": "http://127.0.0.1:8765/mcp",
      "headers": {"Authorization": "Bearer <从实例配置中读取>"}
    }
  }
}
```

服务固定绑定 `127.0.0.1`，校验 Host 与 Origin。令牌不出现在启动日志中；不开放任意文件读取、Java 执行、系统命令或自定义数据包接口。聊天与游戏命令使用普通客户端发送路径，权限由原服务端判定。

协议依据：[MCP HTTP 传输规范](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports)、[MCP 工具规范](https://modelcontextprotocol.io/specification/2025-11-25/server/tools)。

## 测试能力

所有结果包含 `content`，结构化结果还提供 `structuredContent`。截图同时返回 `image/png` 的 MCP 图片内容，可直接被支持图片的 MCP 客户端查看。参数错误为 JSON-RPC `-32602`；未进世界、菜单已变化、游戏线程超时等执行错误返回 `isError=true`。

| 类别 | 工具 | 主要参数 / 返回内容 |
| --- | --- | --- |
| 客户端 | `client.state` | 游戏/适配器版本、窗口/GUI/framebuffer 尺寸、FPS、连接、暂停、屏幕 |
| 截图 | `capture.screenshot` | `maxWidth`（默认 1280，64..3840）；PNG、原始尺寸、输出尺寸、GUI 转换比例 |
| 界面 | `ui.inspect` | 屏幕标题、`screenToken`、控件 id/坐标/文字/焦点/输入值、容器槽位坐标 |
| 界面 | `ui.open` / `ui.close` | `screen=inventory/pause/chat`；容器关闭通知服务端 |
| 点击 | `ui.click` | `x/y` GUI 逻辑坐标；`button=left/right/middle`；可选 shift/ctrl/alt |
| 控件 | `ui.widget_click` | `screenToken/id`，屏幕或控件变化后拒绝旧编号 |
| 滚动 | `ui.scroll` | `x/y/vertical`；可选 `horizontal` |
| 文本 | `ui.type` | `text`，向获得焦点的控件发送 Unicode 码点，返回被接收的数量 |
| 键盘 | `input.key` | `key=w/space/enter/escape/left.shift/...`；`ticks=1..200`；GUI 支持修饰键 |
| 释放 | `input.release_all` | 释放本 Mod 持有的按键；超时取消、屏幕切换也会自动释放 |
| 视角 | `player.look` | `yaw/pitch`（pitch -90..90） |
| 玩家 | `player.inspect` | 坐标、朝向、生命、饥饿、经验、游戏模式、选中槽与手持物品 |
| 背包 | `inventory.list` | `scope=player/container`、`includeEmpty`；槽位、menuId/stateId、光标物品 |
| 物品 | `item.inspect` | `scope/slot`；注册 id、数量、耐久、tooltip、注册表上下文序列化的组件 |
| 容器 | `inventory.click` | `menuId/slot/action/button`；拿取、快捷移动、快捷栏交换、丢弃、复制、全部拿取 |
| 快捷栏 | `inventory.select_hotbar` | `slot=0..8` |
| 世界 | `world.inspect` | 维度、游戏时间、天气、难度、准星方块/实体 |
| 方块 | `world.block` | `x/y/z`；已加载方块 id 与属性，不加载新区块 |
| 实体 | `world.entities` | `radius`（默认 32，最多 128）、`limit`（默认 64，最多 256） |
| 交互 | `player.interact` | `action=attack/use`、`ticks=1..200`，按真实键位触发普通游戏交互 |
| 消息 | `chat.send` | `message`；以 `/` 开头发送服务端命令 |
| 历史 | `chat.history` | `limit=1..100`；最新消息优先 |
| 等待 | `test.wait` | `ticks=1..200`，等待实际客户端 tick |
| 断言 | `test.assert` | `query/arguments/path/operator/expected` |
| 轮询 | `test.wait_for` | 同断言，另有 `timeoutMs`（默认 10000，最多 30000）、`intervalMs`（默认 100） |

### 坐标与槽位

`ui.click` 的坐标来自 `ui.inspect`，使用 GUI 逻辑单位。若从截图读取像素坐标，换算为 `guiX=imageX/guiToImageX`、`guiY=imageY/guiToImageY`。高 DPI、GUI 缩放和截图缩放都已反映在返回值中。

`scope=player` 的 `slot` 是玩家 Inventory 索引；`scope=container` 的 `slot` 是当前菜单索引。两者不能混用。先读取 `inventory.list`，再把同一容器的 `menuId/slot` 传给 `inventory.click`。`slot=-999` 仅用于 `PICKUP` 丢出光标物品。`SWAP` 的 button 为 0..8，`CLONE` 为 2，其他操作为 0/1。

创造背包有独立的客户端物品选择槽位，`inventory.click` 会给出明确错误；使用 `ui.inspect` 返回的槽位坐标配合 `ui.click` 操作。普通容器操作先产生客户端预测，随后由服务端同步，使用断言等待最终结果。

### 推荐测试流程

```text
client.state
ui.open {"screen":"inventory"}
capture.screenshot {}
inventory.list {"scope":"container","includeEmpty":true}
item.inspect {"scope":"container","slot":36}
ui.close {}
chat.send {"message":"/cd"}
test.wait_for {
  "query":"ui.inspect",
  "path":"/screen/title",
  "operator":"contains",
  "expected":"菜单",
  "timeoutMs":5000
}
```

断言路径为 [RFC 6901 JSON Pointer](https://www.rfc-editor.org/rfc/rfc6901)：数组示例 `/slots/0/item/id`，键中的 `/` 写 `~1`，`~` 写 `~0`。支持 `equals`、`contains`（字符串或数组）、`exists`、`gte/lte`。`exists` 检查字段是否存在，值为 `null` 也算存在。失败返回 `isError=true` 和实际值；`test.wait_for` 不阻塞游戏线程。

## 分层与多版本

```text
MCP 客户端
    ↓ Streamable HTTP
core/                         Java 21；不引用 Minecraft / Fabric / LWJGL
    McpServer                 HTTP、JSON-RPC、鉴权、协议协商
    ToolCatalog               稳定工具名、JSON Schema、参数校验
    TestAssertions            查询编排、JSON Pointer 断言、非阻塞轮询
    GameAdapter / ToolResult  版本边界
    ↓ GameAdapter.execute
versions/mc26_3/              Java 25；游戏与 Fabric API 仅在此处
    MinecraftMcpClient        生命周期、配置与入口
    Minecraft26Adapter        有界任务队列、tick 调度、取消与停机
    ScreenController          屏幕、控件、输入框与坐标
    InputController           SDL 转换、键位映射、自动释放
    StateReader               物品、容器、世界、玩家、聊天
    ImageCapture              GPU 异步截图、后台 PNG 编码
```

增加游戏版本时：

1. 新建 `./fabric-26_3-mod/MinecraftMcp/versions/mcXX_X/`，提供该版本自己的 `build.gradle`、版本属性、Fabric 元数据与 `GameAdapter` 实现。
2. 只在该适配模块引用 Minecraft/Fabric 类；可复用经核对稳定的适配代码，但不把版本差异放入 `core`。旧的混淆版本需要在该适配模块单独处理映射与构建。
3. `settings.gradle` 自动发现适配模块；根 `build` 自动测试和构建全部模块。也可单独执行 `:mcXX_X:build`。
4. 各模块把同一份 `core` 打进自身产物，使用不同的 `mcXX_X` 文件名和精确游戏版本依赖，独立验证与留档；目前只实现和发布 26.3。

HTTP 请求只入队，游戏对象的读取和修改在客户端 tick 上完成；PNG 编码在后台。队列限 128、每 tick 最多处理 32 个任务；HTTP 并发限 16、请求体限 1 MiB。尚未执行的超时请求会被取消，长按在后续 tick 释放；已经发送的游戏操作无法撤销。大型 tooltip 和组件有明确截断标记。

## 验证

`core` 的自动化测试会真实启动 HTTP 服务，覆盖协议协商、工具发现、鉴权/Origin、媒体类型、无 id 请求不执行、非法参数、超时取消、MCP 图片内容、命令开关、JSON Pointer 与轮询断言、配置令牌持久化。它们使用假的游戏适配器，不代表真实客户端操作已经验收。

真实客户端启动后，可在仓库根运行标准库冒烟脚本：

```bash
python3 ./scripts/minecraft-mcp-smoke.py --config <实例目录>/config/minecraft-mcp.json
# 加上 --exercise-ui：进世界后打开背包，查询容器和物品，再关闭
python3 ./scripts/minecraft-mcp-smoke.py --config <实例目录>/config/minecraft-mcp.json --exercise-ui
```

脚本完成真实 MCP 握手、查询、截图，截图保存到 `./fabric-26_3-mod/MinecraftMcp/build/smoke/screenshot.png`。游戏进程、显示环境和有效实例由运行者提供。

## 首版范围

已覆盖普通客户端 UI 与常见容器/世界查询。没有连接服务器、创建世界、切换资源包、任意传送或注入任意包的专用工具；可通过现有界面输入完成有权限的流程。Mod 自绘的控件、非标准输入框或服务端虚拟菜单需要在真实实例验证；`ui.inspect` 只能识别游戏 GUI 树中公开的控件。ArcMenu 的世界内点击可用视角调整和 `player.interact` 测试。

版本与产物规则见 [`./docs/dev/版本与产物规范.md`](../../docs/dev/版本与产物规范.md)。
