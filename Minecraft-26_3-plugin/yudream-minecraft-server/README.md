# yudream-minecraft-server（本地改造版）

把本服玩家动态（进服 / 退服 / AFK / 聊天 / 死亡 / 成就）上报给 YuDream Admin 的 **Bukkit 桥接插件**，
可独立运行（`standalone`），也可作为代理后端传感器（`downstream`）。

源码基线是官方 `1.6.2` 产物的**反编译代码**，原有功能与兼容分支保持不变；本仓版本在其上做了两处**加法**：

| 新增 | 说明 |
| --- | --- |
| 本地在线时长统计 | 口径与后端逐条对齐（在线时长 = 已结算区间 + 当前未结算区间；AFK 单独累计，不从在线时长里扣）；后端不可用、还没配好时都照常记账。落盘在 `plugins/YudreamMinecraftServer/playtime.yml` |
| 待发上报落盘 | 进服 / 退服 / AFK 起止先写本地日志（`pending-reports.jsonl`），后端恢复后按**原始时间戳**按序补发；进程重启也不丢。因为后端按 `eventAt` 结算且 join / quit / AFK 幂等，补发不会重复计时 |
| 与后端对齐 | 定期把后端的每玩家累计拉下来合并（`local-playtime.sync-from-backend`）：本地只从插件开始记账那一刻算起，后端可能有更早的历史，不对齐两边会差一个历史量。已结算累计取两边较大值，当前区间只认"玩家确实在线"的那一侧，所以只补历史、不会把离线时间算成在线 |

数据流向：**事件 → 本地台账（立即）→ 待发日志（落盘）→ 后端（可达时）** + **后端 → 本地台账（定期对齐）**。
所以「后端挂了」不会影响本地数字，后端恢复后两边的数字会自动收敛到同一个值。

## 构建

```bash
./gradlew :yudream-minecraft-server:build
# 产物：./Minecraft-26_3-plugin/yudream-minecraft-server/build/libs/yudream-minecraft-server-bukkit-Beta_1_7_0.jar
```

- 面向 1.8.8+ 的 Bukkit 系服务端（`plugin.yml` 声明 `api-version: 1.13`），因此**编译目标是
  spigot-api 1.13.2、字节码 Java 8**，而不是本仓其它插件的 Paper 26.3 + Java 25
  （在它自己的 `build.gradle` 里用 `ext.bringsOwnBukkitApi = true` 跳过根工程的默认依赖）。
- 产物命名沿用产品线 `yudream-minecraft-server-bukkit-<版本>.jar`（跨游戏版本，不带 mc 标签），
  这条是《版本与产物规范》里登记的例外。

## 新增配置

```yaml
local-playtime:
  enabled: true
  save-interval-seconds: 30     # 落盘间隔；停服与命令查询时也会立即落盘
  sync-from-backend: true       # 定期拉后端的每玩家累计，补齐本地台账缺的历史
  sync-interval-seconds: 300    # 对齐间隔

durable-queue:
  enabled: true
  max-pending: 5000             # 待发事件上限，超出丢最老的并告警
```

> 对齐（以及 `/yudreammc status` 的远端统计）走的是**管理端**列表接口 `/admin/servers/{id}/players`，
> 需要 API key 带 `plugin:minecraft-server:manage` 权限；没有该权限时对齐会打一行警告并跳过，本地统计不受影响。
> 下游（代理）模式不做对齐 —— 那种部署下凭证在代理侧，本机拿不到列表。

## 面包屑

- 本地台账文件格式与服务端之间是**稳定接口**：伴侣插件
  [`./Minecraft-26_3-plugin/YudreamPlaytime/`](../YudreamPlaytime) 直接读 `playtime.yml`，改格式要同步那一侧。
- `/yudreammc playtime [玩家]` 可以就地核对本地数字（在线时长 / AFK 时长 / 待发条数）。
- 反编译基线里 `online.yudream.minecraft.bridge.core` 包是产物自带的一份共享核心副本（部分类仍在用），
  其中两处无法编译的地方做了最小修正：`JsonValue.writeTo` 的重复局部变量名、`BridgeProtocol.decode`
  默认分支引用了不存在的变量；另外 `YudreamCommand` 里一处 `final` 局部变量在分支与 catch 里重复赋值。
  这三处都只影响编译，不改变行为。
