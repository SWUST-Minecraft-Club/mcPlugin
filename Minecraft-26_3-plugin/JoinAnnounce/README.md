# JoinAnnounce

进服公告插件：玩家进服后按配置延迟弹出一个 **26.3 dialog 弹窗**（默认），也可以切回聊天栏或两者同时显示；
附带首次进服文案、进出服全服广播，以及主菜单里的「服务器公告」入口。

`config-simple.yml` 是无徽章的 SWUST 社团公告示例（星空社 + 社团配色文案），复制到
`plugins/JoinAnnounce/config.yml` 后 `/joinannounce reload` 即可使用。

| 能力 | 配置段 | 说明 |
| --- | --- | --- |
| 进服公告 | `announce` | 延迟 N tick（默认 20 = 1 秒）发送；`display` 决定 channel |
| 每日一次 | `announce.once-per-day` | 每位玩家每天只在当天第一次进服时弹；跨天在线不打断，服务器重启后重置；时区用 `announce.day-zone-offset-hours` |
| 公告弹窗 | `announce.dialog` | dialog 的标题、正文宽度、按钮文字与宽度、ESC 关闭开关；正文就是 `announce.lines` |
| 更新日志按钮 | `announce.dialog.link` | 主按钮旁的第二个按钮（默认「查看完整更新日志」），点击用浏览器打开外部链接；`url` 留空则不显示 |
| 标题 / 音效 | `announce.title` / `announce.sound` | 可选，默认关闭；音效键写错只告警并跳过 |
| 首次进服文案 | `first-join` | 接在正文后面，只对第一次进服的玩家生效 |
| 进服 / 退服广播 | `broadcast.join` / `broadcast.quit` | 默认关闭；打开后才替换原版 join / quit 消息 |
| 主菜单入口 | `menu` | 主菜单里的「服务器公告」，点击按 `announce.display` 显示；软依赖 MainMenu |

文本全部使用 MiniMessage，占位符：`<player>` 玩家名、`<online>` 当前在线人数、`<max>` 最大人数、`<world>` 所在世界。
单行解析失败时按纯文本显示，不会让整条公告丢失。

## 显示方式

`announce.display` 三选一，默认 `dialog`：**进服公告只在弹窗里显示，插件不往聊天栏发任何消息**。

| 值 | 效果 |
| --- | --- |
| `dialog`（默认） | 只弹 dialog，公告正文进弹窗，不进聊天栏 |
| `chat` | 只把 `announce.lines` 逐行发到聊天栏 |
| `both` | 弹窗 + 聊天栏都来一遍 |

dialog 用的是 26.3 的运行时 API（`Dialog.create(...)` + `player.showDialog(...)`），
**不需要 bootstrapper、内置数据包或注册表项**，也不会出现在 ESC 暂停菜单里。

## 命令与权限

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/joinannounce reload` | `joinannounce.admin`（默认 OP） | 重载 `config.yml`，主菜单入口的开关 / 排序值同时生效 |
| `/joinannounce preview` | `joinannounce.admin`（默认 OP） | 按 `announce.display` 预览一遍（默认只弹窗）；控制台只打印文本 |

已进服的玩家不会补发公告，改完文案后可以用 `preview` 自己看效果。

## 配置要点

- `announce.delay-ticks`：进服后延迟多少 tick 发送，避开原版进服消息刷屏；`0` 表示下一 tick 就发。
- `announce.once-per-day`：默认 `true`（每位玩家每天只在当天第一次进服时弹）；设为 `false` 则每次进服都弹。
  跨天在线的玩家不会被打断，退出后在新的一天再次进服时才弹；记录只在内存里，**服务器重启即重置**。
  「一天」按 `announce.day-zone-offset-hours` 划分（默认东 8 区）。`/joinannounce preview` 和主菜单入口不受此限制。
- `announce.enabled: false`：关闭进服公告（弹窗、聊天栏、标题、音效一并关闭），但进出服广播与主菜单入口不受影响。
- `announce.lines`：弹窗正文与聊天栏文本共用；`first-join.lines` 排队追加在后面。
- `announce.dialog.width` 单位为像素（60~600，默认 260），过长的行建议自己拆成多行。
- `announce.dialog.button-width` / `link.width` 是底部两个按钮的宽度（像素）：主按钮（开始冒险）更宽。
  两个按钮用 `confirmation` 类型渲染，**固定在弹窗底部页脚**、不随正文滚动：链接在左、主按钮在右；
  `link.url` 留空则只保留主按钮（notice 单按钮布局）。
- `broadcast.*` 默认关闭：进服公告不碰聊天栏；打开后才接管原版进服 / 退服消息，`message` 留空则关闭该条（原版消息也不再显示）。
- `menu.enabled: false`：不注册主菜单入口；`menu.order` 是功能区排序值（越小越靠前）。
- `menu.material` 无效时回退 `WRITTEN_BOOK` 并在控制台告警。

## 依赖

- 运行期**不需要** MainMenu：没装时进服公告、广播照常工作，只跳过菜单入口（启动日志会提示）。
- 装了 MainMenu 时通过 `compileOnly project(':MainMenu')` 编译、调用 `MainMenuApi` 注册入口，位置与排序由主菜单管理。

## Folia 说明

- `paper-plugin.yml` 声明 `folia-supported: true`，编译依赖 Folia API。
- 延迟公告走 `player.getScheduler().runDelayed(...)`，玩家在延迟期间退服时任务自动取消；弹窗也在该玩家区域线程里发送。
- 广播不遍历在线玩家：直接改写 `PlayerJoinEvent#joinMessage()` / `PlayerQuitEvent#quitMessage()`，由服务端统一广播。
- 配置在 `/joinannounce reload` 时整体替换为不可变快照（`volatile` 引用），不同区域线程读到的都是完整配置。
- 弹窗的关闭按钮复用同一个点击动作实例，避免每次进服都注册一条新的长效回调。

## 构建

```bash
./gradlew :JoinAnnounce:build
# 产物：./Minecraft-26_3-plugin/JoinAnnounce/build/libs/JoinAnnounce-mc26_3-plugin-Beta_0_4_1.jar
```

测试服：`./gradlew :JoinAnnounce:runServer`（共用仓库根目录的 `./run/`）。
