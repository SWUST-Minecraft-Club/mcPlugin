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

[Beta_0_2_0 -> Beta_0_2_1] 2026.10.25 12:06:08
1.调整：进出服广播默认关闭 —— 进服公告只在 dialog 弹窗里显示，插件默认不往聊天栏发任何消息
2.调整：`/joinannounce preview` 改为严格按 `announce.display` 预览（默认只弹窗，不再强制发聊天栏文本）
3.调整：`config-simple.yml` 说明同步（缺省值里广播已是关闭）

[Beta_0_2_1 -> Beta_0_3_0] 2026.10.25 14:03:58
1.新增：dialog 弹窗支持徽章 / 图标（`announce.dialog.icon`）——把徽章画进 64x64 皮肤「面部」，
   以 PLAYER_HEAD 物品正文展示（`showDecorations(false)`，尺寸可配 8~256，默认 48）；
   皮肤贴图通过 textures 属性 URL 由客户端下载，URL 非法时启动 / reload 告警并跳过
2.新增：仓库内置社团徽章皮肤 `./assets/swust-badge-skin.png`（徽章量化成蓝 / 灰 / 白三色 8x8 像素画）
3.调整：`config-simple.yml` 改为 SWUST MINECRAFT 社团公告示例（社团配色文案 + 徽章图标）
4.调整：头颅 Profile 改用 Paper 的 `createProfile` + `ProfileProperty`（`Bukkit.createPlayerProfile` 已过时）

[Beta_0_3_0 -> Beta_0_4_0] 2026.10.25 14:51:35
1.新增：进服自动下发资源包（`resource-pack`：url / sha1 / force / prompt）——
   `Player#setResourcePack(uuid, url, sha1, prompt, force)`，`force: true` 即强制安装；带 sha1 按 hash 缓存
2.新增：社团徽章资源包 `./assets/swust-badge-pack.zip`（源文件 `./assets/pack/`）——
   覆盖 `structure_void` 物品模型为 256x256 圆形透明徽章贴图；`pack.mcmeta` 用 26.3 资源包 format 97
3.新增：弹窗图标来源 `announce.dialog.icon.source`（`head` 皮肤头颅 / `pack` 资源包贴图，默认 head）；
   选 pack 但资源包未启用时告警
4.调整：`config-simple.yml` 徽章改为 pack 高清来源（size 64）并启用资源包强制下发

[Beta_0_4_0 -> Beta_0_4_1] 2026.10.25 15:17:51
1.修复：弹窗徽章只有 16 像素大 —— dialog 物品正文永远按原版物品大小绘制（`width`/`height` 只是布局框），
   在资源包徽章模型上加 GUI display 变换（`scale [4,4,4]` + 平移居中 `translation [1.5, -1.5, 0]`），
   徽章放大到 64 像素并居中；资源包 zip 与 SHA-1 同步更新
2.调整：`announce.dialog.icon.source` 为 pack 时 `size` 固定 64，配置成其它值会告警
3.文档：说明弹窗里的白色方框是物品控件获得键盘焦点时的原版描边，不是贴图

[Beta_0_4_1 -> Beta_0_4_2] 2026.10.25 15:34:54
1.修复：徽章放大后仍贴在控件左上角 —— 实测（玩家截图）确认 26.3 的 GUI display `translation`
   单位是 1 像素（原 [1.5,-1.5] 不可见），改为 `[24, -24, 0]`（16px 控件中心平移到 64px 布局框中心）；
   资源包 zip 与 SHA-1 同步更新
2.说明：dialog 正文是纵向列表、没有横向网格，「拆成 4 个物品」拼不出 2×2 徽章，
   放大走模型 GUI display 变换（物品被控件框裁剪，因此布局框必须 ≥ 放大后的贴图）

[Beta_0_4_2 -> Beta_0_4_3] 2026.10.25 15:52:13
1.修复：玩家截图（徽章只露 16×16 一角）反推出 26.3 渲染管线的 translation 方向为 x 取反向、
   y 同向，把平移改为 `[-24, 24, 0]`（位移 = (-24, -24) 像素，正中 64px 布局框中心）；
   资源包 zip 与 SHA-1 同步更新（5e565089...）
