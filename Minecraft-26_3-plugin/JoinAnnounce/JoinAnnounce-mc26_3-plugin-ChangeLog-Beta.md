# JoinAnnounce ChangeLog（测试版）

版本命名与产物见 `./docs/dev/版本与产物规范.md`。
格式：`[新版本] 时间` 或 `[上一版本 -> 新版本] 时间`，下面列本次改动（新增 / 修复 / 调整）。

---

[Beta_0_1_0] 2026.10.24 21:48:29
1.新增：进服公告 —— 玩家进服后按 `announce.delay-ticks` 延迟发送多行 MiniMessage 公告（默认 1 秒）；
   支持 `<player>` / `<online>` / `<max>` / `<world>` 占位符，单行解析失败按纯文本兜底
2.新增：首次进服专属文案（`first-join`，接在普通公告后面，只对第一次进服的玩家生效）
3.新增：可选标题 / 副标题与提示音（`announce.title` / `announce.sound`，默认关闭；音效键无效时告警并跳过）
4.新增：进出服全服广播（`broadcast.join` / `broadcast.quit`），替换原版 join / quit 消息，
   `enabled: false` 时完全不干预原版消息
5.新增：主菜单「服务器公告」入口（`menu`，软依赖 MainMenu）—— 点击关闭菜单并重播公告，
   悬停显示当前在线人数，排序值可配；MainMenu 缺失时自动跳过，不影响进服公告
6.新增：`/joinannounce reload|preview`（权限 `joinannounce.admin`，默认 OP）
7.适配：Folia 26.3 —— `paper-plugin.yml` 声明 `folia-supported: true`；延迟公告走 `player.getScheduler()`，
   广播改写事件消息而不跨区域遍历在线玩家，配置快照用 volatile 整体替换

[Beta_0_1_0 -> Beta_0_2_0] 2026.10.24 22:13:16
1.新增：进服公告支持 dialog 弹窗 —— 用 26.3 运行时 `Dialog.create(...)` 构造 notice 对话框
   （标题 + 正文 + 「知道了」按钮），`player.showDialog(...)` 只发给该玩家；不需要 bootstrapper / 数据包 / 注册表项
2.新增：`announce.display` 显示方式 `dialog` / `chat` / `both`（默认 `dialog`：只弹窗，不再刷聊天栏）
3.新增：`announce.dialog` 配置（标题 / 正文宽度 / 按钮文字 / 是否允许 ESC 关闭）
4.调整：主菜单「服务器公告」入口按 `announce.display` 显示；`/joinannounce preview` 玩家执行时同时预览聊天栏与弹窗
5.调整：弹窗关闭按钮复用同一个点击动作实例（无限次使用），避免每次进服注册一条新的长效回调
