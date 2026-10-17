# 客户端资源包：清理暂停菜单（ESC）界面

这个资源包做两件事，都是**客户端**层面的改动 —— 服务端 API 做不到，只能用资源包覆盖：

| 目标 | 做法 | 涉及文件 |
| --- | --- | --- |
| 去掉「主菜单」按钮上那句 *注：自定义选项由第三方服务器或内容提供。请小心操作！* | 清空该提示的文案 | `assets/minecraft/lang/{zh_cn,en_us}.json` → `menu.custom_options.tooltip` |
| 让暂停菜单右下角的 4 个原版图标不可见 | 用同尺寸全透明贴图覆盖 | `assets/minecraft/textures/gui/sprites/pause_menu/{bug,social_interactions,player_reporting}.png`、`assets/minecraft/textures/gui/sprites/friends/friends.png` |

被隐藏的 4 个图标是原版客户端自带的按钮（不是插件加的）：

| 图标 | 按钮 | 说明 |
| --- | --- | --- |
| 瓢虫 | Report Bugs / 报告 Bug | 客户端硬编码指向 Mojang 的反馈地址 |
| 气泡 | Give Feedback / 给予反馈 | 同上 |
| 两个头像 | Friends / 好友 | 客户端社交功能 |
| 人像+警告 | Player Reporting / 举报玩家 | 客户端聊天举报入口 |

它们由客户端 `PauseScreen` 在多人游戏里**无条件创建**（服务端既没有 API 关闭，也没有配置项），所以只能这样处理。

## 用法

### 本地自测（最简单）

把本目录（或 `build/resourcepack/mcpligin-resourcepack.zip`）放进客户端的 `resourcepacks/` 文件夹，然后在游戏里
`选项 → 资源包` 启用它。资源包对**该客户端的所有服务器**生效，不需要服务端做任何事。

### 服务器统一下发

1. 把 zip 上传到玩家可访问的地址（网页/OSS/自建 HTTP 都行）；
2. 在服务端 `server.properties` 填：

   ```properties
   resource-pack=https://你的地址/mcpligin-resourcepack.zip
   resource-pack-sha1=<zip 的 sha1>
   resource-pack-prompt=本服资源包：清理暂停菜单界面（可选）
   require-resource-pack=false
   ```

   玩家进服时会收到提示，同意后自动启用。

也可以让插件用 `Player#setResourcePack(...)` 逐个下发，需要你自己有托管地址。

## 只想去掉一部分？

- 只想去掉那句提示、保留图标：删掉 `assets/minecraft/textures/` 整个目录；
- 想保留某个图标：删掉对应的 png 文件即可（例如保留"好友"就删 `sprites/friends/friends.png`）；
- 想把提示换成自己的话：把 `menu.custom_options.tooltip` 的值改成想要的文本（例如 `"由本服提供"`）。

## 注意

- 图标只是变成透明，**按钮本身仍然存在、仍然可以点击**（点下去是原版行为，比如打开举报界面）。想让它们彻底消失需要客户端模组。
- 语言文件是"按 key 覆盖"，只影响这里列出的几条文案，其它翻译不受影响。
- 升级 Minecraft 版本后，如果这些 key 或贴图路径变了，需要按新版本的 `assets/minecraft/lang/*.json` 和 `textures/gui/sprites/` 对照更新。

## 重新打包

```bash
./gradlew buildResourcePack      # 产物：build/resourcepack/mcpligin-resourcepack.zip
```
