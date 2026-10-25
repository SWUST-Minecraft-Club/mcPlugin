# ChatPrivacy

> Minecraft **26.3（Folia）** 聊天防举报插件 · 测试版 `Beta_0_1_0`
>
> 产物：`./release/ChatPrivacy/ChatPrivacy-mc26_3-plugin-Beta_0_1_0.jar`
>
> 功能对标 [No Chat Reports](https://github.com/Aizistral-Studios/No-Chat-Reports) 的**服务端行为**，
> 与官方项目无关联；官方 NCR 目前只支持 Fabric / Forge / NeoForge，没有 Paper/Folia 插件。

## 它做什么

玩家公开发言时，插件会：

1. 在 Paper 的 `AsyncChatEvent` 中取消原版广播；
2. 用 Paper 的 `ChatRenderer` 按每个查看者渲染出最终聊天文本；
3. 以**系统消息**的形式发给各玩家。

系统消息不携带 Mojang 聊天签名，客户端无法把它作为聊天举报的证据；原始签名聊天包也不会被转发给任何客户端。视觉上仍然是正常的 `<玩家名> 消息`，聊天格式（前缀、颜色、称号等）由现有的 `ChatRenderer` 决定，兼容常见的聊天格式化插件。

## 它不能做什么

- **不能移除原版客户端的举报按钮和未签名提示**：这些是客户端 UI。1.20.2 之后服务端也无法关闭「聊天消息未签名」类提示；装了 No Chat Reports 客户端的玩家可以自行隐藏。
- **第一版不支持 NCR 客户端的「安全服务器」绿标**：绿标需要往服务器列表 ping 响应里注入 `preventsChatReports: true`，纯 Paper/Folia API 做不到，需要 NMS/Netty（FreedomChat 的做法）。不影响「聊天无法被举报」这个核心功能。
- **不影响皮肤 / 玩家 ID 类举报**：NCR 本身也主要针对聊天。
- **不自动修改 `server.properties`**：`enforce-secure-profile` 只检查并告警。

## 安装

1. 把 `./release/ChatPrivacy/ChatPrivacy-mc26_3-plugin-Beta_0_1_0.jar` 放进服务端的 `plugins/`；
2. 重启服务端（Folia 26.3，Java 25+）；
3. 首次启动会生成 `plugins/ChatPrivacy/config.yml`，按需修改后用 `/chatprivacy reload` 重载。

插件不依赖 MainMenu，也不需要任何 NMS、协议库或前置插件。

### 让未签名客户端能进服

如果玩家使用 No Chat Reports 这类不发送聊天签名的客户端，还需要在 `server.properties` 里设置：

```properties
enforce-secure-profile=false
```

然后重启服务端。本插件只会在启动 / 重载 / `status` 时提示，不会自动改这个文件。

## 配置

```yaml
enabled: true                  # 总开关；关闭后完全恢复原版签名聊天
bedrock-only: false            # 只改写基岩版（Geyser 转发）玩家
log-chat: true                 # 是否在控制台保留一份改写后的聊天
warn-on-secure-profile: true   # 是否检查 server.properties 的 enforce-secure-profile
messages:
  prefix: "<gray>[<aqua>聊天隐私</aqua>]</gray> "
  reloaded: "<green>配置已重新加载。</green>"
```

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 总开关。关闭后不接管聊天，原版签名聊天恢复，可以被举报 |
| `bedrock-only` | `false` | 只处理 UUID version 0 的基岩版玩家；Java 玩家仍走原版签名聊天 |
| `log-chat` | `true` | 是否把改写后的聊天发给控制台；关闭后本插件不主动记录，其它插件/原版仍可能记录 |
| `warn-on-secure-profile` | `true` | 启动/重载时检查 `server.properties`，为 `true` 时告警 |
| `messages.prefix` | 见上 | 命令回执前缀，MiniMessage |
| `messages.reloaded` | 见上 | `/chatprivacy reload` 的回执，MiniMessage |

## 命令与权限

| 命令 | 说明 |
| --- | --- |
| `/chatprivacy` / `/chatprivacy status` | 查看开关、基岩版模式、secure-profile 状态、本次启动改写统计 |
| `/chatprivacy reload` | 重载 `config.yml` 并重新检查 `server.properties` |

权限：`chatprivacy.admin`（默认 OP），同时覆盖 `status` 与 `reload`。

## 工作原理与 Folia 线程

- 监听 `io.papermc.paper.event.player.AsyncChatEvent`（`EventPriority.HIGHEST`）。
- 如果事件已被其它插件取消（禁言等），直接尊重原结果，不再转发。
- 取消事件后，原始签名聊天包不会进入 Paper 的广播路径；插件用 `event.renderer().render(...)` 为每个查看者生成最终文本。
- 玩家消息通过 `player.getScheduler().run(...)` 在该玩家所属区域线程发送，不使用 `Bukkit.getScheduler()`。
- 控制台与其它非玩家 `Audience` 直接发送；控制台是否保留由 `log-chat` 控制。
- 改写内容仍会经过 Paper 的聊天过滤 / 装饰流程，因为使用的是事件里的 `message()` 与 `renderer()`。

## 验证

```bash
./gradlew :ChatPrivacy:build          # 构建并归档到 ./release/ChatPrivacy/
./gradlew :ChatPrivacy:runServer      # 启动共用 Folia 测试服（端口 25566）
```

游戏内用两个客户端验证：

1. A 发一条公开聊天，B 应正常看到，格式与原版一致；
2. B 打开社交界面 / 举报界面，不应能选中 A 这条消息作为举报证据；
3. 用禁言类插件取消 A 的聊天时，B 不应再收到改写后的消息；
4. 执行 `/chatprivacy status`，确认统计与 `enforce-secure-profile` 状态正确。

> 如果要用 No Chat Reports 客户端测试「未签名客户端进服」，先把 `./run/server.properties` 的
> `enforce-secure-profile` 改为 `false` 并重启测试服。

## 版本与产物

版本号、产物命名、留档与 ChangeLog 规则见 `./docs/dev/版本与产物规范.md`。
本插件当前为测试版 `Beta_0_1_0`，ChangeLog 见 [`ChatPrivacy-mc26_3-plugin-ChangeLog-Beta.md`](./ChatPrivacy-mc26_3-plugin-ChangeLog-Beta.md)。
