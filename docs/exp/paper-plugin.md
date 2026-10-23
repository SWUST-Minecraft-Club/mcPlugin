# Paper / Folia 插件侧：坑与经验

> Folia 沿用 Paper API 与 `paper-plugin.yml`，本文的对话框、配置、事件类条目在 Folia 上同样适用；
> 调度器与线程归属的差异见 [folia.md](./folia.md)。

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

## `Bukkit.dispatchCommand` 不会触发 `PlayerCommandPreprocessEvent`

- **现象**：在菜单/按钮里执行 `Bukkit.dispatchCommand(player, "plt gui")`，目标插件只显示 `/plt` 的帮助页；
  玩家手动输入 `/plt gui` 却能打开称号仓库。其他在 `PlayerCommandPreprocessEvent` 里处理子命令的插件同样受影响。
- **根因**：`Bukkit.dispatchCommand` 直接走命令派发器，**不会**触发 `PlayerCommandPreprocessEvent`；
  PlayerTitle 5.5.4-cn 的 `CommandListener` 正是在该事件里识别 `plt gui`、取消事件并打开 GUI，
  所以绕过事件后只剩旧命令执行器打印帮助。
- **规避/解决**：按玩家手动输入的流程执行：
  1. 先 `player.closeInventory()`，避免目标 GUI 与当前菜单冲突；
  2. 下一 tick 构造并 `callEvent(new PlayerCommandPreprocessEvent(player, "/" + command))`；
  3. 事件被取消（目标插件已处理）就结束，否则再 `Bukkit.dispatchCommand`；
  4. `dispatchCommand` 返回 `false` 时提示“指令不可用或没有权限”。
  实现见 `./Minecraft-26_3-plugin/MainMenu/src/main/java/com/example/mainmenu/internal/CustomCommandFeature.java`。
- **来源**：2026.10.23，MainMenu `Beta_1_5_1`；PlayerTitle `5.5.4-cn` 的 `CommandListener#onPlayerCommand`。

## `preferredSlot` 写在功能区或越界，被误判成位置冲突

- **现象**：功能声明 `preferredSlot(19)`（功能区格子）或 `preferredSlot(99)`（越界）时，被当作"位置冲突"处理：
  排到功能区最前面，悬停文本还追加"位置不可用"提示。
- **根因**：冲突判定只看"该格是否已被占用"，没有区分「功能区格子 / 越界值」本身就等于"没声明位置"。
- **规避/解决**：只有**功能区以外**的格子才算自定义位置；声明在功能区、或超出 0~53 一律按"没声明位置"处理，
  直接按排序值进功能区。规则详见 `docs/dev/功能插件接入规范.md` §3.2。
- **来源**：2026.10.18，MainMenu 正式版 `1_0_1`。
