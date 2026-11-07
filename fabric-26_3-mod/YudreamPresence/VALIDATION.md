# YudreamPresence 验证记录

## 2026-11-07：Fabric 26.3 服务端启动 / 快照冒烟

### 环境

| 项 | 值 |
| --- | --- |
| 操作系统 | Windows 10.0.26200 x64 |
| Java | Oracle JDK 26.0.1 |
| Fabric Loader | 0.20.1（Mixin 0.17.5+mixin.0.8.7） |
| Minecraft | 26.3 官方服务端（server bundle SHA-1 `33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c`） |
| 模组 | `YudreamPresence-mc26_3-fabric-Beta_0_1_0.jar` |
| 模组配置 | `base-url=http://127.0.0.1:18080`、`server-id=smoke-server`、`api-key=smoke-key`、`snapshot.interval-seconds=5`、`afk.timeout-seconds=30` |
| mock Admin | JDK `com.sun.net.httpserver`，监听 `127.0.0.1:18080`，把每个 POST 的路径 / `X-API-Key` / body 写入 `requests.log` |

### 步骤

1. 用 Fabric Installer 1.1.0 安装 Fabric 26.3 服务端（Loader 0.20.1）；
2. 把模组放进 `mods/`，写入上面的配置；
3. 启动服务端，等待 `Done`；
4. 等待 12 秒，发送 `stop`；
5. 检查服务端日志与 mock Admin 请求。

### 结果

- Fabric Loader 正常识别 `yudream-presence Beta_0_1_0`；`Mixin` 日志出现
  `Mixing MinecraftServerMixin ... into net.minecraft.server.MinecraftServer` 与
  `Mixing PlayerListMixin ... into net.minecraft.server.players.PlayerList`。
- 启动日志：
  - `[YudreamPresence] 已启用：base-url=http://127.0.0.1:18080, server-id=smoke-server, snapshot=5s, afk=30s, durable-queue=on，待发落盘=on`
  - `[YudreamPresence] 服务端已接管：在线名单 5s 一次，AFK 检查 5s 一次`
- mock Admin 收到 5 次请求，全部为：

  ```
  POST /api/plugins/minecraft-server/servers/smoke-server/players/snapshot
  X-API-Key: smoke-key
  {"observedAt":...,"players":[]}
  ```

  - 启动时 `sync-online-on-enable` 与 1 秒首轮快照各一次；
  - 之后每 5 秒一次；
  - 停服 `stopServer` 再补一次空快照；
  - 服务端正常保存并退出。

- 断言脚本输出：`enabled=true, attached=true, snapshot=true, apiKey=true`，`SMOKE OK`。

### 未覆盖

- 真实客户端的进服 / 退服 / AFK 起止：本次没有客户端自动化环境，未做双客户端联调。
  这些路径由 25 项单元测试覆盖状态机与 payload，Mixin 目标已经过 `javap` 核对；
  与 Bukkit 插件同源的 `players/join|quit|afk/start|afk/end` 端点未在实机触发。
- 断网重连、后端 5xx、落盘补发：由单元测试覆盖队列重试与 journal 重放；
  未在真实服务端上做断网演练。
- 代理 / `downstream` 模式：本版本不实现。

### 本次踩到并修掉的两个 Mixin 坑

1. **注解桩保留级别**：`@Inject` / `@At` 的真实注解是 `RUNTIME`，仓库原桩没有写 `@Retention`（默认 CLASS）。
   结果是 Mixin 只把 handler 方法合并进目标类、不插入调用，`require = 1` 也不报错。
   `@Mixin` 本身仍是 CLASS，不要统一改成 RUNTIME。
2. **Mixin 类的字节码版本**：Fabric 26.3 自带 Mixin 0.8.7 的 `CompatibilityLevel` 最高 `JAVA_13`
   （class version 57）。业务代码用 Java 25，但 Mixin 类必须单独用 `--release 13` 编译；
   否则日志会提示 `Class version 69 required is higher than ... JAVA_13 supports class version 57`，
   注入同样静默失效。见 `./docs/exp/fabric-mod.md`。
