# ConcertoMenu

把 **Concerto**（Minecraft 游戏内音乐播放器）的客户端菜单挂进 MainMenu：主菜单里放一个**唱片机**，
点击后让装了 Concerto 客户端模组的玩家直接打开 Concerto 的主界面。

## 原理

Concerto 的界面由**客户端模组**渲染，服务端无法直接打开它 —— 本插件通过插件消息通道
`concerto:menu` 发一条 `open` 指令，客户端模组（Concerto **2.2.0** 起支持）收到后打开自己的主界面
（与按 `I` 等价）。没装模组的客户端会忽略这个自定义负载，点击没有副作用。

负载格式：`VarInt 长度 + UTF-8` 的字符串 `open`（与 Concerto 客户端的 `StreamCodec.readUtf` 对齐）。

## 安装

| 需要 | 说明 |
| --- | --- |
| 服务端 | 本插件 + MainMenu（`compileOnly project(':MainMenu')`），放进 `plugins/` |
| 客户端 | Concerto Fabric 客户端模组 **2.2.0+**（2.2.0 之前的版本没有 `concerto:menu` 通道，点了没反应） |

## 配置

```yaml
order: 30   # 功能项在主菜单功能区里的排序值（越小越靠前）
```

## 面包屑

- 与 Concerto 自己的 `concerto:main` 协议**互不影响**：这里用的是独立通道 `concerto:menu`，
  也不改 Concerto 的协议版本，旧客户端只会忽略这个负载。
- 客户端没装模组时无任何提示（悬停里已写明需要安装 Concerto 模组）。
