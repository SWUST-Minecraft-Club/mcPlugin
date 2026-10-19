# Paper 插件侧：坑与经验

## ESC 菜单按钮点一次就失效

- **现象**：暂停菜单里的「主菜单」按钮点开一次后，再点没有任何反应（也不报错）。
- **根因**：26.3 的 dialog 点击回调默认只回调一次，注册时用的是「一次性」的 ClickCallback。
- **规避/解决**：注册 dialog 时改用可重复触发的回调类型；改动见 `./Minecraft-26_3-plugin/MainMenu/src/main/java/com/example/mainmenu/MainMenuBootstrap.java`。
- **来源**：2026.10.18，MainMenu `Beta_0_3_1`（ChangeLog-Beta「ESC 按钮只能点开一次」）。

## 管理界面的材质被旧配置带成石头

- **现象**：升级后管理入口显示成石头，属性页的空白格被石头填满。
- **根因**：管理界面的物品材质从 `config.yml` 读取，而旧的配置文件里没有新增的 `menu.admin.*` 键，
  读取时落到兜底值（石头）。
- **规避/解决**：菜单 / 管理界面的关键材质**写死在代码里**（入口 = 命令方块、分隔 = 黑色染色玻璃板、
  显示隐藏 = 黄绿 / 红色羊毛、排序 = 空地图、更改位置 = 末影之眼、转固定 = 拉杆、转非固定 = 红石火把、返回 = 屏障），
  属性页只放操作位与返回键，其余格子留空。已升格进 `docs/dev/conventions.md` §3。
- **来源**：2026.10.18，MainMenu `Beta_0_8_1`。

## 手持时钟右键空气：服务端收不到事件

- **现象**：对着空气右键时钟想打开菜单，服务端 `PlayerInteractEvent` 不触发，功能无效。
- **根因**：空挥不产生方块 / 实体交互事件，服务端侧根本没有可监听的目标。
- **规避/解决**：交给客户端模组检测后上报 —— MainMenuRepair 检测到 `use` 且无命中目标时执行 `/cd clockair`；
  对着方块的情况仍由服务端事件处理。已写进 MainMenuRepair 的 README 与 `docs/dev/` 相关说明。
- **来源**：2026.10.18，MainMenu `Beta_0_4_0`。

## `preferredSlot` 写在功能区或越界，被误判成位置冲突

- **现象**：功能声明 `preferredSlot(19)`（功能区格子）或 `preferredSlot(99)`（越界）时，被当作"位置冲突"处理：
  排到功能区最前面，悬停文本还追加"位置不可用"提示。
- **根因**：冲突判定只看"该格是否已被占用"，没有区分「功能区格子 / 越界值」本身就等于"没声明位置"。
- **规避/解决**：只有**功能区以外**的格子才算自定义位置；声明在功能区、或超出 0~53 一律按"没声明位置"处理，
  直接按排序值进功能区。规则详见 `docs/dev/功能插件接入规范.md` §3.2。
- **来源**：2026.10.18，MainMenu 正式版 `1_0_1`。

## 插件被禁用 / 重载时，已打开的菜单没关，物品会被取走

- **现象**：`/reload`、PlugMan 之类禁用插件后，玩家已打开的主菜单 / 管理界面还留在屏幕上；此时监听器已注销，
  点击不再被取消，菜单里的命令方块、玻璃板、功能图标会被当成普通物品取走。
- **根因**：`onDisable` 只取消了刷新任务，没有关闭这些界面；Bukkit 也不会在插件禁用时自动关掉它创建的容器。
- **规避/解决**：`onDisable` 里遍历在线玩家，把顶层 inventory 的 holder 是 `MenuHolder` / `AdminHolder` 的界面
  `closeInventory()` 掉，再取消刷新任务。见 `./Minecraft-26_3-plugin/MainMenu/src/main/java/com/example/mainmenu/MainMenuPlugin.java`。
- **来源**：2026.10.18，MainMenu `Beta_1_0_3`（ChangeLog-Beta「禁用时关闭菜单」）。

## 权限节点没写进 paper-plugin.yml，也会"看起来能工作"

- **现象**：代码里 `hasPermission("mainmenu.admin")` 对 OP 返回 true、普通玩家返回 false，表现和「默认 OP」一致，
  但 `paper-plugin.yml` 的 `permissions:` 里根本没有这个节点。
- **根因**：Bukkit 对未注册权限会回退到 `Permission.DEFAULT_PERMISSION`（= `PermissionDefault.OP`），
  功能碰巧是对的，但权限不会出现在权限管理界面 / 权限插件的已知列表里，属于隐式依赖。
- **规避/解决**：只要代码里检查了权限，就必须在 `paper-plugin.yml` 的 `permissions:` 登记（含 `description` 与 `default`，
  见 `docs/dev/conventions.md` §3「权限」）。
- **来源**：2026.10.18，MainMenu `Beta_1_0_3`（补 `mainmenu.admin` 声明）。

## 按启动配置决定"是否注册监听器"，会让 /cd reload 失效

- **现象**：`menu.open.clock-right-click` 启动时为 false，改成 true 后 `/cd reload` 仍然不生效，必须重启服务端。
- **根因**：`onEnable` 里用配置值决定要不要 `registerEvents`；reload 只重读配置，不会补注册监听器。
- **规避/解决**：监听器**始终注册**，把开关判断放到事件处理内部（每次事件现读配置），reload 即可即时生效。
- **来源**：2026.10.18，MainMenu `Beta_1_0_3`（ChangeLog-Beta「时钟开关热重载」）。

## 功能插件返回的 ItemStack 会被主菜单改写，渲染前必须复制

- **现象**：功能插件复用同一个 `ItemStack` 实例当图标时，菜单渲染后它的悬停文本被追加了「点击使用」等主菜单自己的行。
- **根因**：`MenuFeature.icon()` 的约定是「主菜单会复制它」，但渲染与管理界面都在返回的实例上直接 `setItemMeta`。
- **规避/解决**：渲染 / 管理界面统一 `feature.icon().clone()` 后再写 meta；并给单个功能的渲染加 try/catch，
  一个功能插件出错不中断整页（`MenuRenderer`、`AdminService`）。
- **来源**：2026.10.18，MainMenu `Beta_1_0_3`（ChangeLog-Beta「icon() 复制后再写悬停文本」）。
