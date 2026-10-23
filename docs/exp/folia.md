# Folia 服务端：坑与经验

> Folia 是 Paper 的区域化多线程分支，API 基本兼容 Paper，但**调度模型与线程归属完全不同**。
> 本文记录从 Paper 26.3 迁移到 Folia 26.3 时踩到 / 容易踩到的点。

## `Bukkit.getScheduler()` 在 Folia 上不可用

- **现象**：插件在 Paper 上正常，换到 Folia 后定时任务不执行，或直接抛
  `UnsupportedOperationException`；`BukkitRunnable`、`runTaskLater`、`scheduleSyncRepeatingTask` 同样失效。
- **根因**：Folia 没有单一主线程，BukkitScheduler 的“主线程队列”模型不存在。
- **规避/解决**：按任务类型换调度器：
  - 全局任务（遍历在线玩家、广播、与位置无关的维护）→ `Bukkit.getGlobalRegionScheduler()`；
  - 世界 / 方块 / 区块任务 → `Bukkit.getRegionScheduler()`；
  - 玩家 / 实体相关（背包、消息、传送、实体状态）→ `player.getScheduler()`（EntityScheduler）；
  - 纯 IO / 网络 → `Bukkit.getAsyncScheduler()`。
  - 测试服用 `./gradlew :插件名:runServer`（内部执行 `runFolia`），不要再用 Paper 启动验证。
- **来源**：2026.10.22，仓库 Paper → Folia 迁移；`./Minecraft-26_3-plugin/MainMenu/src/main/java/com/example/mainmenu/util/Schedulers.java`。

## 背包操作必须在该玩家所属区域线程

- **现象**：在异步回调或全局任务里 `player.openInventory(...)` / `inventory.setContents(...)`，
  Folia 抛线程归属异常，或者界面内容偶发错乱。
- **根因**：玩家实体与它打开的背包都属于某个区域（region），只有拥有该区域的线程能改。
- **规避/解决**：所有界面操作统一走 `player.getScheduler()`：
  - 打开 / 重绘 / 翻页用 `run` / `runDelayed` / `runAtFixedRate`；
  - 已经在该玩家区域线程时直接执行（用 `Bukkit.isOwnedByCurrentRegion(player)` 判断），避免多等一 tick；
  - 异步 HTTP 回调完成后，先在玩家区域线程重绘，再检查 `getOpenInventory()` 是否仍是自己的 Holder。
- **来源**：2026.10.22；`MainMenu` 的 `MenuService`、`Wallet` 的 `WalletMenus`。

## 缺 `folia-supported: true` 会被拒绝加载

- **现象**：插件 jar 放进 `plugins/`，Folia 启动日志提示插件不支持 Folia，插件没有启用。
- **根因**：Folia 在加载阶段检查插件描述文件里的 `folia-supported`，没有显式声明就视为不支持。
- **规避/解决**：每个 `paper-plugin.yml`（Folia 沿用该文件名）或旧式 `plugin.yml` 都写：
  ```yaml
  folia-supported: true
  ```
  新增插件时模板已自带，不要删。
- **来源**：2026.10.22；本仓 6 个插件的描述文件。

## 跨区域共享状态必须线程安全

- **现象**：多个玩家同时打开菜单时，刷新任务表 / 冷却表 / 缓存偶尔丢条目或抛
  `ConcurrentModificationException`。
- **根因**：Paper 只有一个主线程，普通 `HashMap` 够用；Folia 里不同玩家的回调可能落在不同区域线程，
  同一个 `HashMap` 会被并发读写。
- **规避/解决**：跨玩家共享的集合换 `ConcurrentHashMap` / `CopyOnWriteArrayList` / 不可变快照；
  状态机与文件台账用 `synchronized` 或显式锁；渲染路径只读缓存，不在里面做网络 / 大文件 IO。
- **来源**：2026.10.22；`MenuService`、`ClockInteractListener`、`PlaytimeStats`、`DownstreamSensor`。

## 保留 Java 8 / 旧 Bukkit 兼容的插件：用反射适配 Folia

- **现象**：`yudream-minecraft-server` 要同时支持 1.8.8+ 的 Bukkit 系服务端和 Folia 26.3，
  但 Folia 只支持 26.x + Java 25，不能把编译目标换成 Folia API。
- **根因**：Folia 调度器类不在 1.13.2 API 里，编译期引用不到。
- **规避/解决**：新增反射兼容层 `FoliaSchedulerCompat`：
  - 启动时用 `Bukkit.getServer().getName()` / `Bukkit.getVersion()` 识别 Folia；
  - Folia 上反射调用 `getGlobalRegionScheduler()` / `getAsyncScheduler()` / `Player#getScheduler()`；
  - 非 Folia 自动回退到原 `BukkitScheduler`；
  - 玩家任务先用 `Bukkit.isOwnedByCurrentRegion(Entity)` 判断，已在归属线程时直接执行（退服消息等不能拖到下一 tick）。
- **来源**：2026.10.22；`./Minecraft-26_3-plugin/yudream-minecraft-server/src/main/java/online/yudream/minecraft/bukkit/compat/FoliaSchedulerCompat.java`。

## run-paper 的 `runServer` 默认下载的是 Paper

- **现象**：只改 `gradle.properties` 的 API 依赖，测试服起来仍是 Paper。
- **根因**：run-paper 3.1.0 自动注册的 `runServer` 任务使用 Paper 下载服务；Folia 是单独的 `runFolia` 任务。
- **规避/解决**：根 `build.gradle` 用 `runPaper { folia { registerTask { ... } } }` 注册 `runFolia`，
  再让 `runServer` 依赖 `runFolia` 并跳过自己的 Paper 启动动作，保持原命令可用：
  ```groovy
  tasks.named('runServer') {
      dependsOn tasks.named('runFolia')
      onlyIf { false }
  }
  ```
  直接验证 Folia 构建号可用 `runFolia`；下载地址见 `https://fill.papermc.io/v3/projects/folia/versions/26.3/builds`。
- **来源**：2026.10.22；`./build.gradle`。
