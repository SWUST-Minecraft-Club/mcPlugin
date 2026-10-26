# MainMenuRepair

一个 **Fabric 客户端模组**：清理 ESC 暂停菜单里两样碍眼的东西。

| 改动 | 实现 |
| --- | --- |
| 去掉服务器自定义选项按钮上的 *注：自定义选项由第三方服务器或内容提供。请小心操作！* | Mixin `PauseScreen.addCustomDialogButtons`，把 `Button$Builder.tooltip(...)` 的参数置空 |
| 移除右下角 4 个原版图标按钮：报告 Bug / 给予反馈 / 好友 / 举报玩家 | Mixin `PauseScreen.createPauseMenu`，把图标行 `LinearLayout.horizontal()` 换成不接收子控件的 `EmptyIconRow`，4 个按钮不会被创建 |
| 跳过 ESC →「主菜单」的中间 dialog 页 | Fabric API `ScreenEvents.AFTER_INIT` 把按钮换成直接发 `/cd`；Mixin `Gui#setScreen` 拦下 dialog 界面兜底 |
| 手持时钟右键空气打开主菜单 | `ClientTickEvents.END_CLIENT_TICK` 检测“按下使用键 + 瞄准空气”，发 `/cd clockair`；仅在服务端提供该命令（装了 MainMenu）时启用 |
| 去掉服务端自定义界面右上角的黄色「自定义屏幕」警告图标 | Fabric API `ScreenEvents.AFTER_INIT` 遍历 `DialogScreen` 控件，按 `menu.custom_screen_info` 提示文本识别并隐藏，尺寸归零后标题重新居中 |

## 为什么需要模组

这两样都是**客户端硬编码**的，服务端无法处理：

- 那句提示来自客户端 `PauseScreen.addCustomDialogButtons` 里无条件附加的 `CUSTOM_OPTIONS_TOOLTIP`（语言 key `menu.custom_options.tooltip`）；
- 4 个图标是 `PauseScreen` 在多人游戏里无条件创建的原版按钮（贴图 `pause_menu/bug`、`pause_menu/social_interactions`、`friends/friends`、`pause_menu/player_reporting`），点击后指向 Mojang 的反馈地址或原版界面。

Paper 插件侧没有对应 API；资源包也只能改文案/贴图（图标会变成看不见但仍可点击的按钮）。模组直接改客户端界面，才能真正删掉。

## 构建

```bash
# 在本仓库根目录执行（用仓库自带的 Gradle wrapper）
./gradlew -p ./fabric-26_3-mod/MainMenuRepair build
```

产物：`./build/libs/MainMenuRepair-<版本>.jar`

### 关于构建方式（没有用 fabric-loom）

26.3 客户端已不再混淆，Fabric 也没有 26.3 的 yarn/intermediary，因此**编译不需要 mappings，构建也不需要 remap**。
本工程因此手工编译，依赖只有两项：

1. **官方客户端 jar**：由 `downloadMinecraftClient` 任务从 `gradle.properties` 里的 `client_jar_url` 下载到 `build/minecraft/`；
2. **Mixin 注解**：`src/mixin-stubs/java` 下的同名注解桩（按真实 `net.fabricmc:sponge-mixin` 的签名声明，仅参与编译，不会打进 jar）。

这么做是因为 `maven.fabricmc.net` 在部分网络环境下会被限速到 KB 级，loom 自身依赖较多，会长时间卡在下载上。
如果你的网络正常、想用标准工作流（`runClient` 调试、IDE 集成），可以把 `build.gradle` 换成官方模板的写法：

```groovy
plugins { id 'net.fabricmc.fabric-loom' version '1.18-SNAPSHOT' }
dependencies {
    minecraft "com.mojang:minecraft:26.3"
    implementation "net.fabricmc:fabric-loader:0.20.0"
}
```

## 安装

1. 客户端装好 Fabric Loader（26.3，本模组要求 `>=0.20.0`）；
2. 把 `MainMenuRepair-<版本>.jar` 放进该实例的 `mods/` 目录；
3. 重启客户端。

不需要 Fabric API，也不需要服务端做任何事（纯客户端模组，`fabric.mod.json` 里 `environment: client`）。
生效时游戏日志会打印一行：`[MainMenuRepair] 已接管 ESC 暂停菜单：...`（第一次打开暂停菜单时）。

## 注意

- 两处注入都用 `require = 0`：将来 Minecraft 改了方法名或调用点，模组不会让客户端崩溃，只是那一项改动失效（此时按新版客户端更新 mixin 目标即可）。
- 模组只影响**客户端界面**，不改服务端行为。
- 「时钟右键空气」只在服务端命令树里存在 `/cd clockair`（即装了 MainMenu）时判定；没装的服务器上不会发任何指令，
  状态文件会记录“功能不启用”。
- 服务端 dialog 右上角的黄色警告图标由客户端 `DialogScreen` 无条件创建（提示“这是服务器提供的自定义界面”），
  模组会把它隐藏；这只影响界面显示，不改变任何服务端行为。
- 想只保留其中一项：删掉 `PauseScreenMixin` 里对应的那个注入方法。
- 目标方法/调用点取自 26.3 官方客户端 jar：`PauseScreen#addCustomDialogButtons`、`PauseScreen#createPauseMenu`、`LinearLayout#horizontal()`、`Button$Builder#tooltip(Tooltip)`。
