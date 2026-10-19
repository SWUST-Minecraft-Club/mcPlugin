# YudreamPlaytime

MainMenu 的伴侣插件：在主菜单里加一个「我的在线时长」功能项。

| 显示 | 实现 |
| --- | --- |
| 图标 | 查看者自己的头（`PLAYER_HEAD` + 所有者是查看者） |
| 悬停 | 玩家名 / 在线时长 / AFK 时长，格式 `hh小时mm分钟` |
| 点击 | 无效（`clickable()` 为 false，主菜单只作展示） |
| 位置 | 功能区（默认 `order: 10`，可用 `config.yml` 调整） |

数字来自同机 `yudream-minecraft-server` 桥接插件的本地台账
（`plugins/YudreamMinecraftServer/playtime.yml`），口径与 YuDream Admin 后端一致
（含当前未结算区间；AFK 单独统计），所以**后端不可用时菜单照样显示数字**。
没有记录的玩家显示 `0分钟`。

## 构建

```bash
./gradlew :YudreamPlaytime:build
# 产物：./Minecraft-26_3-plugin/YudreamPlaytime/build/libs/YudreamPlaytime-mc26_3-plugin-Beta_0_1_0.jar
```

## 依赖

- 运行期需要 **MainMenu**（`compileOnly project(':MainMenu')`，由服务端提供类）。没装 MainMenu 时插件正常加载但不注册功能。
- 数据源需要 **yudream-minecraft-server** 桥接插件（它才写 `playtime.yml`）；没有它时所有玩家显示 `0分钟`。

## 配置

```yaml
stats-file: ""      # 留空 = <服务端目录>/plugins/YudreamMinecraftServer/playtime.yml
cache-seconds: 3    # 台账文件缓存时间
order: 10           # 功能区排序值
```

## 为什么用「按查看者渲染」

MainMenu 的功能项默认是全局渲染（文本对所有玩家一样）。要显示「查看者自己的数据」，
它提供了按查看者渲染的钩子：实现 `MenuFeature` 时覆写 `icon(viewer)` / `description(viewer)`
（用构建器的话是 `iconForViewer(...)` / `linesForViewer(...)`）。写法见
[`./docs/dev/功能插件接入规范.md`](../../docs/dev/功能插件接入规范.md)。
