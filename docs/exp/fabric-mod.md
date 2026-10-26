# Fabric 模组侧：坑与经验

## Mixin 注入在本环境始终不生效，最终靠 Fabric API 通道

- **现象**：模组装上后暂停菜单毫无变化，日志里既没有 Mixin 报错，也没有"注入失败"提示。
- **根因**：没有收敛到单一原因。排查过程中确认了几个**必要条件**（缺一个就报错或静默失效）：

  | 排查项 | 结论 |
  | --- | --- |
  | 注解保留级别 | 必须是 `RUNTIME`，否则运行期报 `missing @Mixin annotation` |
  | `@Invoker` | 必须声明成**类**（`@Invoker` 接口），写成方法不生效 |
  | 注入参数 | 全部要 `remap = false`（本仓库手工编译、无 mappings） |
  | 构造时序 | 构造函数里控件还没创建，要排队到下一个 tick 再清理 |
  | 清理逻辑 | 静态方法与 lambda 不合并，需要内联 |

  逐项修完仍不生效 —— 结论是**不要把 Mixin 当作唯一通道**。
- **规避/解决**：真正生效的是 Fabric API 屏幕事件通道（`ScreenEvents.AFTER_INIT`）——按钮替换、控件清理都挂在这条通道上；
  Mixin 只保留 `require = 0` 的增强，失效也不让客户端崩溃。已升格进 `docs/dev/conventions.md` §4。
- **来源**：2026.10.17~18，MainMenuRepair `Beta_0_1_2` → `Beta_0_2_0`；`./fabric-26_3-mod/MainMenuRepair/README.md`。

## 用 fabric-loom 会卡死在下载（因此本仓库不用 loom）

- **现象**：按官方模板引入 `net.fabricmc.fabric-loom` 后，构建长时间卡在下载依赖上，速度只有 KB 级。
- **根因**：`maven.fabricmc.net` 在部分网络环境下被限速到 KB 级，而 loom 自身依赖多、会逐个下载。
- **规避/解决**：不用 loom，手工编译 —— 26.3 客户端**未混淆**，Fabric 也没有 26.3 的 yarn/intermediary，
  所以编译不需要 mappings、构建不需要 remap：只要官方客户端 jar（`gradle.properties` 的 `client_jar_url`）
  + `src/mixin-stubs/java` 下的注解桩。网络正常、需要用 `runClient` 调试时，可按 README 里的官方模板写法换回去。
- **来源**：2026.10.17 起；`./fabric-26_3-mod/MainMenuRepair/build.gradle`、同目录 README。

## 客户端判断服务端是否装了某插件：查命令树

- **场景**：MainMenuRepair 的「时钟右键空气」要发 `/cd clockair`，但纯客户端模组默认不知道服务端有没有 MainMenu，
  在别的服务器上每按一次右键就会发出一条未知命令。
- **做法**：用 `ClientPacketListener#getCommands()` 拿服务端随登录包下发的 Brigadier 命令树，
  检查 `getRoot().getChild("cd")` 下有没有 `clockair`。根节点没有任何子命令说明命令包还没到，
  此时返回「未知」、下个 tick 再看，不要过早缓存成「没装」。
- **注意**：命令树按权限过滤。`mainmenu.use` 默认 `true`，普通玩家也能看到；如果某天把权限改成 `op`，
  无权限玩家本来也用不了 `/cd`，此时功能不启用是正确行为。编译需要 brigadier 的桩
  （`src/mixin-stubs/java/com/mojang/brigadier/`），它不在客户端 jar 里。
- **来源**：MainMenuRepair `Beta_1_0_2`；`MainMenuRepairClient#detectClockAirCommand`。

## 客户端模组的排查输出不要用 System.out

- **现象**：用 `System.out.println` 打的排查信息，在游戏的 `latest.log` 里完全看不到，导致误判"代码没执行"。
- **根因**：`latest.log` 由 log4j 接管，`System.out` 不写入其中。
- **规避/解决**：状态单独写到游戏目录下的 `mainmenu-repair-status.txt`（`com.example.mainmenurepair.util.StatusLog`），
  记录各条钩子是否生效，并带上时间与工作目录。
- **来源**：MainMenuRepair `Beta` 排查期；`src/main/java/com/example/mainmenurepair/util/StatusLog.java`。

## 26.3 的屏幕、键盘与容器 API 需要按官方 jar 核对

- **现象**：沿用旧版示例中的 `Minecraft.screen/setScreen`、GLFW 键码、`ClickType/handleInventoryMouseClick` 会引用不存在的接口或产生错误输入。
- **根因**：26.3 当前屏幕位于 `Minecraft.gui.screen()`，设置屏幕用 `gui.setScreen(...)`；输入后端为 SDL，`KeyEvent` 的前两个参数是扫描码和键码；容器操作使用 `ContainerInput` 与 `MultiPlayerGameMode.handleContainerInput(...)`。
- **规避/解决**：用官方 26.3 客户端 jar 的 `javap` 输出核对签名；协议核心使用 `w/enter/escape` 等游戏键名，扫描码、SDL 键码和菜单类型只留在版本适配器。截图用 `Screenshot.takeScreenshot(RenderTarget, Consumer<NativeImage>)` 异步回调，PNG 编码后关闭 `NativeImage`；`getPixels()` 返回 ARGB，可传给 `BufferedImage.setRGB`。
- **来源**：2026.10.26，MinecraftMcp `Beta_0_1_0`；官方客户端 SHA-1 `e877b6a07acd633fb3bb475002175cec036e7b87`，实现位于 `./fabric-26_3-mod/MinecraftMcp/versions/mc26_3/`。Fabric tick/停机接口另用 26.3 的 lifecycle API jar 核对。
