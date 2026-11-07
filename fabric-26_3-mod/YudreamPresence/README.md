# YudreamPresence

一个 **Fabric 26.3 服务端模组**：把服务端的玩家在线情况上报到 YuDream Admin 的 `minecraft-server` 插件。
当前测试版 `Beta_0_1_0`。

上报范围只有「在线状态」：

| 事件 | 端点 | 说明 |
| --- | --- | --- |
| 进服 | `POST .../players/join` | 玩家完成登录后上报 |
| 退服 | `POST .../players/quit` | 玩家从玩家列表移除前上报 |
| AFK 开始 / 结束 | `POST .../players/afk/start` / `afk/end` | 默认 300 秒无操作进入 AFK，有操作时结束 |
| 全量在线名单 | `POST .../players/snapshot` | 默认 60 秒一次；空名单也会上报，用于关闭过期会话 |

直连 YuDream Admin，不走代理 `yudream:bridge`。不上报聊天、死亡、成就，也不包含游戏内管理命令。

## 安装

1. 服务端安装 **Fabric Loader `>=0.19.0`**（26.3 实测使用 `0.20.1`）与 Java 25；
2. 把 `YudreamPresence-mc26_3-fabric-Beta_0_1_0.jar` 放进服务端 `mods/`；
3. 启动一次服务端，生成 `config/yudream-presence.properties`；
4. 填好 `base-url`、`server-id`、`api-key`，重启服务端。

**不需要 Fabric API**；模组只在服务端加载（`environment: server`），客户端无需安装。

`api-key` 需要 `plugin:minecraft-server:report` 权限。`server-id` 是 YuDream Admin 里
「MC 服务器」中对应服务器的 id。

## 配置

```properties
enabled=true
base-url=http://127.0.0.1:8080
server-id=
api-key=

http.connect-timeout-ms=5000
http.read-timeout-ms=8000
http.retry-attempts=3
http.retry-delay-ms=1500
http.queue-capacity=1000

log.queued=false
log.attempts=false
log.success=true
log.failures=true
log.payload=false

afk.enabled=true
afk.timeout-seconds=300
afk.check-interval-seconds=30
afk.reset-on-move=true
afk.reset-on-chat=true
afk.reset-on-command=true
afk.reset-on-interact=true

snapshot.enabled=true
snapshot.interval-seconds=60

startup.sync-online-on-enable=true
shutdown.report-quit-on-disable=false
shutdown.flush-timeout-ms=5000

durable-queue.enabled=true
durable-queue.max-pending=5000
```

- 三项凭据（`base-url` / `server-id` / `api-key`）任一为空时只打一行告警，不上报，服务端照常运行。
- `enabled=false` 时完全不启动上报。
- `afk.reset-on-*` 分别控制移动（按方块坐标变化）、聊天、命令、交互/攻击/使用物品/潜行冲刺切换是否重置 AFK 计时。
- `shutdown.report-quit-on-disable` 默认关闭：停服只发空快照；开启后还会给在线玩家逐个补发 `quit`。

## 可靠性与恢复

- **进服 / 退服 / AFK 起止**：先写 `config/yudream-presence/pending-reports.tsv`，由单线程 worker
  按原始时间戳补发；后端不可用、网络失败、进程重启都不会丢。后端恢复后自动追平。
  后端按 `eventAt` 结算，`join / quit / AFK` 都是幂等的，补发不会重复计时。
- **全量快照**：只进内存队列，不落盘。某一次失败就等下一轮；快照本身是权威对账，
  空名单会关闭该服务器上已经离线的会话。
- **停服**：`MinecraftServer.stopServer` 前发空快照（可选逐个 `quit`），并等待队列 flush，
  超时时间由 `shutdown.flush-timeout-ms` 控制。
- **异常退出**：没有停服钩子时靠下一次启动 / 下一轮快照自愈；未发出的持久事件仍在落盘日志里。

## 构建

```bash
# 在本仓库根目录执行
./gradlew -p ./fabric-26_3-mod/YudreamPresence build
# 产物：./fabric-26_3-mod/YudreamPresence/build/libs/YudreamPresence-mc26_3-fabric-Beta_0_1_0.jar
# 同时自动归档到：./release/YudreamPresence/
```

### 为什么不用 fabric-loom

与本仓其它 26.3 模组一致：26.3 未混淆，不需要 mappings / remap；`maven.fabricmc.net` 在部分网络环境下会被限速。
工程只做两件事：

1. `downloadMinecraftServer`：从官方版本清单下载 **server bundler**（SHA-1 固定），
   解出真正的 `META-INF/versions/26.3/server-26.3.jar` 作为编译依赖；
2. `downloadMinecraftLibraries`：按清单下载编译边界会用到的服务端库（brigadier、fastutil、guava、
   authlib、datafixerupper、joml、commons-lang3、slf4j 等），全部 `compileOnly`，不打进模组。

Mixin / Fabric Loader 的接口用 `src/mixin-stubs/java` 下的同名桩编译，运行期由 Fabric Loader 提供真实实现。
Mixin 类单独放在 `src/mixins/java`，用 `--release 13` 编译（Fabric 26.3 自带的 Mixin 0.8.7 最高只接受
Java 13 字节码；业务代码仍是 Java 25）。`@Inject` / `@At` 的桩必须保持 `RUNTIME` 保留级别。

## 实现

- `YudreamPresence`：Fabric `DedicatedServerModInitializer`，读配置、建队列、挂调度；
- `src/mixins/java/.../mixin/`：`MinecraftServer#tickServer/stopServer`、`PlayerList#placeNewPlayer/remove`、
  `ServerGamePacketListenerImpl` 的移动 / 聊天 / 命令 / 交互信号，全部 `require = 0`；
- `config/`、`report/`、`afk/`、`PresenceService`：纯 Java，不引用 Minecraft 类型，单元测试覆盖。

### 26.3 API 备注

- 26.3 没有静态 `MinecraftServer.getServer()`；本模组在 `tickServer` 第一次调用时捕获服务端实例，
  `PlayerList#placeNewPlayer` 也会兜底触发捕获。
- 服务端侧没有 Fabric API 的 tick 事件；调度器线程通过 `MinecraftServer#executeIfPossible` 把
  名单遍历排回服务端线程。
- Mixin 目标方法变化时注入会静默失效（`require = 0`），不会让服务端崩溃；此时对应信号（如 AFK 重置）失效，
  但进服 / 退服 / 快照仍可用。

## 验证

- `./gradlew -p ./fabric-26_3-mod/YudreamPresence build`：编译 + 25 项 JUnit 测试通过。
- 单测覆盖：配置默认值与上下限、JSON 转义与名单顺序、落盘补发顺序/裁剪/坏行容忍、AFK 状态机、
  队列重试与 backlog 补发。
- 实机：2026-11-07 在真实 Fabric 26.3 服务端（Loader 0.20.1 / Java 26）加载模组，
  `已启用` / `服务端已接管` 正常，向本地 mock Admin 发出 5 次 `players/snapshot`（含启动与停服），
  URL、`X-API-Key`、JSON 均正确；真实客户端的进服 / 退服 / AFK 未做自动化联调。
  完整记录见 [VALIDATION.md](./VALIDATION.md)。

## 边界

- 只支持直连 YuDream Admin 的 `standalone` 形态；代理 / `downstream` 传感器形态未实现。
- 不读取服务端插件的本地时长台账，也不做 Vault / Wallet 结算。
- 没有游戏内命令；状态看服务端日志与 `config/yudream-presence/pending-reports.tsv`。
- `players/snapshot` 是权威全量名单：同一 `server-id` 下不要同时跑本模组与 Bukkit 上报插件，
  否则两边会用各自的名册互相覆盖。迁移到 Fabric 服务端后应停用 Bukkit 插件。
